package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.WorkflowResult
import com.aicodeeditor.feature.agent.domain.model.WorkflowStatus
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import com.aicodeeditor.feature.agent.domain.prompt.SystemPromptProvider
import com.aicodeeditor.feature.agent.domain.provider.AIResponse
import com.aicodeeditor.feature.agent.domain.provider.AIStreamChunk
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import com.aicodeeditor.feature.agent.domain.permission.PermissionChoice
import com.aicodeeditor.feature.agent.domain.permission.PermissionScope
import com.aicodeeditor.feature.agent.domain.permission.ToolPermissionPolicyEngine
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.StreamingAgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionManager
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicodeeditor.feature.agent.domain.tool.ToolRegistry
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.agent.domain.tool.ToolStreamEvent
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

class StandardAgentWorkflow @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val aiProviderRepository: AIProviderRepository,
    private val openAIProvider: AIProvider,
    private val anthropicProvider: AIProvider,
    private val promptProvider: SystemPromptProvider,
    private val permissionManager: ToolPermissionManager,
    private val policyEngine: ToolPermissionPolicyEngine
) : AgentWorkflow {

    private companion object {
        const val TAG = "StandardAgentWorkflow"
        const val MAX_ITERATIONS = 10

        /** 实时输出气泡只保留最近这么多字符（尾部），避免超大输出卡死渲染/重组。 */
        const val LIVE_TAIL_CHARS = 8_000

        /** 实时输出推送的最小间隔（毫秒）：再快的逐行输出也按此节流，防止刷爆 UI。 */
        const val PROGRESS_INTERVAL_MS = 100L
    }

    private suspend fun getActiveProvider(sessionId: String?): AIProvider {
        val config = aiProviderRepository.getActiveProviderSync()
            ?: throw IllegalStateException("尚未配置 AI 服务商，请到设置中添加并选择一个")

        if (config.apiKey.isBlank()) {
            throw IllegalStateException("「${config.name}」未填写 API Key，请到设置中配置")
        }
        if (config.effectiveModel.isBlank()) {
            throw IllegalStateException("「${config.name}」未选择模型，请到设置中拉取或添加模型")
        }

        val provider = if (config.type == ProviderType.ANTHROPIC) anthropicProvider else openAIProvider
        provider.apiKey = config.apiKey
        provider.baseUrl = config.baseUrl
        provider.model = config.effectiveModel
        provider.logSessionId = sessionId // 让本会话的请求/响应落到对应日志文件
        return provider
    }

    override suspend fun execute(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): WorkflowResult {
        val messages = mutableListOf<AgentMessage>()
        val errors = mutableListOf<String>()

        try {
            messages.addAll(context.history)
            messages.add(AgentMessage.UserMessage(content = userRequest))

            val systemPrompt = buildSystemPrompt(context)
            val aiProvider = getActiveProvider(context.sessionId)
            var finalContent = ""
            var iterations = 0

            FileLogger.i(TAG, "工作流开始 (历史 ${context.history.size} 条, 工具 ${tools.size} 个): ${userRequest.take(120)}")
            while (iterations < MAX_ITERATIONS) {
                iterations++

                val aiResponse = aiProvider.complete(systemPrompt, messages, tools)
                FileLogger.d(TAG, "AI 响应 #$iterations: content=${aiResponse.content.length} 字符, toolCalls=${aiResponse.toolCalls.size}")
                messages.add(
                    AgentMessage.AssistantMessage(
                        content = aiResponse.content,
                        toolCalls = aiResponse.toolCalls
                    )
                )
                if (aiResponse.content.isNotEmpty()) {
                    finalContent = aiResponse.content
                }

                if (aiResponse.toolCalls.isEmpty()) break

                for (toolCall in aiResponse.toolCalls) {
                    val tool = toolRegistry.getTool(toolCall.name)
                    val argsPreview = JsonObject(toolCall.arguments).toString().take(500)
                    val approved = requestPermissionIfNeeded(tool, toolCall.id, toolCall.arguments, argsPreview)
                    val result = if (approved) {
                        runTool(toolCall.name, toolCall.arguments)
                    } else {
                        ToolResult.Error("用户拒绝执行该工具", "USER_REJECTED").toString()
                    }
                    messages.add(
                        AgentMessage.ToolResultMessage(
                            id = toolCall.id,
                            toolName = toolCall.name,
                            result = result
                        )
                    )
                }
            }

            if (iterations >= MAX_ITERATIONS) {
                errors.add("达到最大迭代次数限制 ($MAX_ITERATIONS 次)")
            }

            return WorkflowResult(
                status = WorkflowStatus.SUCCESS,
                result = finalContent.trim(),
                changes = emptyList(),
                errors = errors,
                messages = messages
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "execute 工作流失败", e)
            return WorkflowResult(
                status = WorkflowStatus.FAILED,
                result = "工作流执行失败: ${e.message}",
                errors = listOf(e.message ?: "未知错误"),
                messages = messages
            )
        }
    }

    override fun executeEvents(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): Flow<AgentEvent> = flow {
        val messages = mutableListOf<AgentMessage>()
        messages.addAll(context.history)
        messages.add(AgentMessage.UserMessage(content = userRequest))

        val systemPrompt = buildSystemPrompt(context)
        val aiProvider = getActiveProvider(context.sessionId)
        var iterations = 0

        while (iterations < MAX_ITERATIONS) {
            iterations++

            // 流式补全：文字增量实时推给 UI，本轮结束拿到聚合后的完整响应（含工具调用）。
            val acc = StringBuilder()
            val reasoningAcc = StringBuilder()
            var finalResponse: AIResponse? = null
            aiProvider.completeStream(systemPrompt, messages, tools).collect { chunk ->
                when (chunk) {
                    is AIStreamChunk.TextDelta -> {
                        acc.append(chunk.text)
                        emit(AgentEvent.AssistantDelta(acc.toString()))
                    }
                    is AIStreamChunk.ReasoningDelta -> {
                        reasoningAcc.append(chunk.text)
                        emit(AgentEvent.ReasoningDelta(reasoningAcc.toString()))
                    }
                    is AIStreamChunk.Final -> finalResponse = chunk.response
                }
            }
            val aiResponse = finalResponse ?: AIResponse(content = acc.toString())

            messages.add(
                AgentMessage.AssistantMessage(
                    content = aiResponse.content,
                    toolCalls = aiResponse.toolCalls
                )
            )

            // 即便没有文字、只有工具调用，也要 emit：让 ViewModel 把 assistant(tool_calls) 落库，
            // 否则回放时会出现没有配对 assistant 的孤儿 tool_result，破坏 API 约束。
            if (aiResponse.content.isNotBlank() || aiResponse.toolCalls.isNotEmpty()) {
                emit(AgentEvent.AssistantText(aiResponse.content, aiResponse.toolCalls, reasoningAcc.toString()))
            }

            if (aiResponse.toolCalls.isEmpty()) break

            for (toolCall in aiResponse.toolCalls) {
                val argsPreview = JsonObject(toolCall.arguments).toString().take(500)
                val tool = toolRegistry.getTool(toolCall.name)
                val approved = requestPermissionIfNeeded(tool, toolCall.id, toolCall.arguments, argsPreview)
                if (!approved) {
                    val rawResult = ToolResult.Error("用户拒绝执行该工具", "USER_REJECTED").toString()
                    messages.add(
                        AgentMessage.ToolResultMessage(
                            id = toolCall.id,
                            toolName = toolCall.name,
                            result = rawResult
                        )
                    )
                    emit(
                        AgentEvent.ToolCallFinished(
                            id = toolCall.id,
                            toolName = toolCall.name,
                            result = rawResult,
                            isError = true,
                            argsPreview = argsPreview
                        )
                    )
                    continue
                }

                emit(
                    AgentEvent.ToolCallStarted(
                        id = toolCall.id,
                        toolName = toolCall.name,
                        argsPreview = argsPreview
                    )
                )
                // 流式工具：逐行透传过程输出，结束后取最终聚合结果；其余走原同步路径。
                val rawResult = if (tool is StreamingAgentTool) {
                    // 实时输出只保留尾部 LIVE_TAIL_CHARS 字符，并按 PROGRESS_INTERVAL_MS 节流推送：
                    // 超大输出时既不撑爆内存/重组，也不会因逐行刷新而卡死 UI。最终完整结果由工具侧
                    // 聚合（已限幅），不受此处尾部窗口影响。
                    val live = StringBuilder()
                    var lastEmitMs = 0L
                    var finalResult: ToolResult? = null
                    try {
                        tool.executeStream(toolCall.arguments).collect { ev ->
                            when (ev) {
                                is ToolStreamEvent.Progress -> {
                                    live.append(ev.chunk).append('\n')
                                    if (live.length > LIVE_TAIL_CHARS) {
                                        live.delete(0, live.length - LIVE_TAIL_CHARS)
                                    }
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmitMs >= PROGRESS_INTERVAL_MS) {
                                        lastEmitMs = now
                                        emit(
                                            AgentEvent.ToolCallProgress(
                                                id = toolCall.id,
                                                toolName = toolCall.name,
                                                accumulated = live.toString()
                                            )
                                        )
                                    }
                                }
                                is ToolStreamEvent.Completed -> finalResult = ev.result
                            }
                        }
                        (finalResult ?: ToolResult.Error("流式工具未返回结果")).toString()
                    } catch (e: CancellationException) {
                        // 用户主动停止：让取消向上传播，终止整个流（并触发 runCommandStream 回收进程）。
                        throw e
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "流式工具执行失败: ${toolCall.name}", e)
                        "Error: 工具执行失败: ${e.message}"
                    }
                } else {
                    runTool(toolCall.name, toolCall.arguments)
                }
                val isError = tool == null || rawResult.startsWith("Error")
                messages.add(
                    AgentMessage.ToolResultMessage(
                        id = toolCall.id,
                        toolName = toolCall.name,
                        result = rawResult
                    )
                )
                emit(
                    AgentEvent.ToolCallFinished(
                        id = toolCall.id,
                        toolName = toolCall.name,
                        result = rawResult,
                        isError = isError
                    )
                )
            }
        }

        emit(AgentEvent.Completed)
    }

    /** 执行单个工具并返回字符串结果；找不到或抛异常时返回以 "Error" 开头的文本。 */
    private suspend fun runTool(name: String, arguments: Map<String, kotlinx.serialization.json.JsonElement>): String {
        val tool = toolRegistry.getTool(name) ?: run {
            FileLogger.w(TAG, "工具不存在: $name")
            return "Error: 工具 $name 不存在"
        }
        FileLogger.d(TAG, "调用工具 $name args=${arguments.keys}")
        return try {
            tool.execute(arguments).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "工具执行失败: $name", e)
            "Error: 工具执行失败: ${e.message}"
        }
    }

    private suspend fun requestPermissionIfNeeded(
        tool: AgentTool?,
        callId: String,
        arguments: Map<String, kotlinx.serialization.json.JsonElement>,
        argsPreview: String
    ): Boolean {
        if (tool == null || tool.permissionPolicy == ToolPermissionPolicy.AUTO_APPROVE) return true

        // 先按已记忆的规则评估：命中即自动放行/拒绝，不弹窗。
        val eval = policyEngine.evaluate(tool.name, arguments)
        return when (eval.verdict) {
            ToolPermissionPolicyEngine.Verdict.ALLOW -> {
                FileLogger.i(TAG, "已记忆规则自动放行: ${tool.name}")
                true
            }
            ToolPermissionPolicyEngine.Verdict.DENY -> {
                FileLogger.i(TAG, "已记忆规则自动拒绝: ${tool.name}")
                false
            }
            ToolPermissionPolicyEngine.Verdict.ASK -> {
                val request = tool.buildPermissionRequest(callId, arguments, argsPreview)
                    .copy(rememberablePatterns = eval.rememberablePatterns)
                FileLogger.i(TAG, "等待用户确认工具调用: ${tool.name}")
                when (permissionManager.awaitApproval(request)) {
                    PermissionChoice.REJECT -> false
                    PermissionChoice.ONCE -> true
                    PermissionChoice.ALWAYS -> {
                        // 「始终允许」固定写入当前项目 scope；不可记忆时（前缀为空）退化为单次放行。
                        if (eval.rememberablePatterns.isNotEmpty()) {
                            policyEngine.remember(tool.name, eval.rememberablePatterns, PermissionScope.PROJECT)
                        }
                        true
                    }
                }
            }
        }
    }

    private fun buildSystemPrompt(context: AgentContext): String =
        promptProvider.build(context)
}
