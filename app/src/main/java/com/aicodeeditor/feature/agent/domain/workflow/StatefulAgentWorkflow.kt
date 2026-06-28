package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.WorkflowResult
import com.aicodeeditor.feature.agent.domain.model.WorkflowStatus
import com.aicodeeditor.feature.agent.domain.permission.PermissionChoice
import com.aicodeeditor.feature.agent.domain.permission.PermissionScope
import com.aicodeeditor.feature.agent.domain.permission.ToolPermissionPolicyEngine
import com.aicodeeditor.feature.agent.domain.prompt.SystemPromptProvider
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import com.aicodeeditor.feature.agent.domain.provider.AIResponse
import com.aicodeeditor.feature.agent.domain.provider.AIStreamChunk
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.StreamingAgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionManager
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicodeeditor.feature.agent.domain.tool.ToolRegistry
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.agent.domain.tool.ToolStreamEvent
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * 阶段三重构：基于不可变状态 (Immutable State) 的 Agent 工作流引擎。
 * 彻底消除可变集合，所有状态扭转均通过复制并产生新状态树（StateFlow/Reducer 思路）完成。
 */
class StatefulAgentWorkflow @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val aiProviderRepository: AIProviderRepository,
    private val openAIProvider: AIProvider,
    private val anthropicProvider: AIProvider,
    private val promptProvider: SystemPromptProvider,
    private val permissionManager: ToolPermissionManager,
    private val policyEngine: ToolPermissionPolicyEngine,
    private val contextCompactor: ContextCompactor
) : AgentWorkflow {

    private companion object {
        const val TAG = "StatefulAgentWorkflow"
        const val MAX_ITERATIONS = 50
        const val LIVE_TAIL_CHARS = 8_000
        const val PROGRESS_INTERVAL_MS = 100L
    }

    /** 不可变状态树 */
    data class SessionState(
        val messages: List<AgentMessage>,
        val iterations: Int = 0,
        val isFinished: Boolean = false,
        val error: String? = null
    )

    private suspend fun getActiveProvider(sessionId: String?): AIProvider {
        val config = aiProviderRepository.getActiveProviderSync()
            ?: throw IllegalStateException("尚未配置 AI 服务商，请到设置中添加并选择一个")
        if (config.apiKey.isBlank()) throw IllegalStateException("「${config.name}」未填写 API Key")
        if (config.effectiveModel.isBlank()) throw IllegalStateException("「${config.name}」未选择模型")

        val provider = if (config.type == ProviderType.ANTHROPIC) anthropicProvider else openAIProvider
        provider.apiKey = config.apiKey
        provider.baseUrl = config.baseUrl
        provider.model = config.effectiveModel
        provider.logSessionId = sessionId
        return provider
    }

    override suspend fun execute(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): WorkflowResult {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
        val currentTime = java.time.ZonedDateTime.now().format(formatter)
        val enrichedRequest = "$userRequest\n\n[System] 当前本地时间: $currentTime"

        val initialState = SessionState(
            messages = context.history + AgentMessage.UserMessage(content = enrichedRequest)
        )
        val systemPrompt = promptProvider.build(context)
        val aiProvider = getActiveProvider(context.sessionId)

        var state = initialState
        while (!state.isFinished && state.iterations < MAX_ITERATIONS) {
            state = stepExecute(state, systemPrompt, aiProvider, tools)
        }

        if (!state.isFinished && state.iterations >= MAX_ITERATIONS) {
            state = state.copy(error = "达到最大迭代次数限制 ($MAX_ITERATIONS 次)")
        }

        return WorkflowResult(
            status = if (state.error == null) WorkflowStatus.SUCCESS else WorkflowStatus.FAILED,
            result = extractFinalContent(state),
            changes = emptyList(),
            errors = listOfNotNull(state.error),
            messages = state.messages
        )
    }

    private suspend fun stepExecute(
        state: SessionState,
        systemPrompt: String,
        aiProvider: AIProvider,
        tools: List<AgentTool>
    ): SessionState {
        val compactedMessages = contextCompactor.compactIfNeeded(state.messages, aiProvider)
        val aiResponse = try {
            aiProvider.complete(systemPrompt, compactedMessages, tools)
        } catch (e: Exception) {
            return state.copy(isFinished = true, error = "LLM 调用失败: ${e.message}")
        }

        val assistantMsg = AgentMessage.AssistantMessage(
            content = aiResponse.content,
            toolCalls = aiResponse.toolCalls
        )
        
        var nextMessages = compactedMessages + assistantMsg

        if (aiResponse.toolCalls.isEmpty()) {
            if (aiResponse.isTruncated) {
                nextMessages = nextMessages + AgentMessage.UserMessage(content = "你的回复因长度限制被截断了，请从截断处继续。")
                return state.copy(messages = nextMessages, iterations = state.iterations + 1)
            }
            return state.copy(messages = nextMessages, isFinished = true, iterations = state.iterations + 1)
        }

        for (toolCall in aiResponse.toolCalls) {
            val tool = toolRegistry.getTool(toolCall.name)
            val argsPreview = JsonObject(toolCall.arguments).toString().take(500)
            val approved = requestPermissionIfNeeded(tool, toolCall.id, toolCall.arguments, argsPreview)
            
            val rawResult = if (approved) {
                runToolSync(tool, toolCall.name, toolCall.arguments)
            } else {
                ToolResult.Error("用户拒绝执行该工具", "USER_REJECTED").toString()
            }
            
            nextMessages = nextMessages + AgentMessage.ToolResultMessage(
                id = toolCall.id,
                toolName = toolCall.name,
                result = rawResult
            )
        }

        return state.copy(messages = nextMessages, iterations = state.iterations + 1)
    }

    override fun executeEvents(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): Flow<AgentEvent> = flow {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
        val currentTime = java.time.ZonedDateTime.now().format(formatter)
        val enrichedRequest = "$userRequest\n\n[System] 当前本地时间: $currentTime"

        val initialState = SessionState(
            messages = context.history + AgentMessage.UserMessage(content = enrichedRequest)
        )
        val systemPrompt = promptProvider.build(context)
        val aiProvider = getActiveProvider(context.sessionId)

        var state = initialState
        while (!state.isFinished && state.iterations < MAX_ITERATIONS) {
            state = stepExecuteEvents(state, systemPrompt, aiProvider, tools)
        }
        emit(AgentEvent.Completed)
    }

    private suspend fun FlowCollector<AgentEvent>.stepExecuteEvents(
        state: SessionState,
        systemPrompt: String,
        aiProvider: AIProvider,
        tools: List<AgentTool>
    ): SessionState {
        val compactedMessages = contextCompactor.compactIfNeeded(state.messages, aiProvider) { emit(it) }
        
        val acc = StringBuilder()
        val reasoningAcc = StringBuilder()
        var finalResponse: AIResponse? = null
        
        try {
            aiProvider.completeStream(systemPrompt, compactedMessages, tools).collect { chunk ->
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
        } catch (e: Throwable) {
            val partial = acc.toString()
            if (partial.isNotEmpty()) {
                emit(AgentEvent.AssistantText(partial, emptyList(), reasoningAcc.toString()))
            }
            throw e
        }

        val aiResponse = finalResponse ?: AIResponse(content = acc.toString())
        val assistantMsg = AgentMessage.AssistantMessage(
            content = aiResponse.content,
            toolCalls = aiResponse.toolCalls
        )
        
        var nextMessages = compactedMessages + assistantMsg

        if (aiResponse.content.isNotBlank() || aiResponse.toolCalls.isNotEmpty()) {
            emit(AgentEvent.AssistantText(aiResponse.content, aiResponse.toolCalls, reasoningAcc.toString()))
        }

        if (aiResponse.toolCalls.isEmpty()) {
            if (aiResponse.isTruncated) {
                nextMessages = nextMessages + AgentMessage.UserMessage(content = "你的回复因长度限制被截断了，请从截断处继续。")
                return state.copy(messages = nextMessages, iterations = state.iterations + 1)
            }
            return state.copy(messages = nextMessages, isFinished = true, iterations = state.iterations + 1)
        }

        for (toolCall in aiResponse.toolCalls) {
            val argsPreview = JsonObject(toolCall.arguments).toString().take(500)
            val tool = toolRegistry.getTool(toolCall.name)
            val approved = requestPermissionIfNeeded(tool, toolCall.id, toolCall.arguments, argsPreview)
            
            if (!approved) {
                val rawResult = ToolResult.Error("用户拒绝执行该工具", "USER_REJECTED").toString()
                nextMessages = nextMessages + AgentMessage.ToolResultMessage(
                    id = toolCall.id,
                    toolName = toolCall.name,
                    result = rawResult
                )
                emit(AgentEvent.ToolCallFinished(toolCall.id, toolCall.name, rawResult, true, argsPreview))
                continue
            }

            emit(AgentEvent.ToolCallStarted(toolCall.id, toolCall.name, argsPreview))
            
            val rawResult = if (tool is StreamingAgentTool) {
                runToolStream(tool, toolCall)
            } else {
                runToolSync(tool, toolCall.name, toolCall.arguments)
            }
            
            val isError = tool == null || rawResult.startsWith("Error")
            nextMessages = nextMessages + AgentMessage.ToolResultMessage(
                id = toolCall.id,
                toolName = toolCall.name,
                result = rawResult
            )
            emit(AgentEvent.ToolCallFinished(toolCall.id, toolCall.name, rawResult, isError))
        }

        return state.copy(messages = nextMessages, iterations = state.iterations + 1)
    }

    private suspend fun runToolSync(tool: AgentTool?, name: String, arguments: Map<String, kotlinx.serialization.json.JsonElement>): String {
        if (tool == null) return "Error: 工具 $name 不存在"
        return try {
            tool.execute(arguments).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Error: 工具执行失败: ${e.message}"
        }
    }

    private suspend fun FlowCollector<AgentEvent>.runToolStream(tool: StreamingAgentTool, toolCall: ToolCall): String {
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
                            emit(AgentEvent.ToolCallProgress(toolCall.id, toolCall.name, live.toString()))
                        }
                    }
                    is ToolStreamEvent.Completed -> finalResult = ev.result
                }
            }
            return (finalResult ?: ToolResult.Error("流式工具未返回结果")).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Error: 工具执行失败: ${e.message}"
        }
    }

    private suspend fun requestPermissionIfNeeded(
        tool: AgentTool?,
        callId: String,
        arguments: Map<String, kotlinx.serialization.json.JsonElement>,
        argsPreview: String
    ): Boolean {
        if (tool == null || tool.permissionPolicy == ToolPermissionPolicy.AUTO_APPROVE) return true

        val eval = policyEngine.evaluate(tool.name, arguments)
        return when (eval.verdict) {
            ToolPermissionPolicyEngine.Verdict.ALLOW -> true
            ToolPermissionPolicyEngine.Verdict.DENY -> false
            ToolPermissionPolicyEngine.Verdict.ASK -> {
                val request = tool.buildPermissionRequest(callId, arguments, argsPreview)
                    .copy(rememberablePatterns = eval.rememberablePatterns)
                when (permissionManager.awaitApproval(request)) {
                    PermissionChoice.REJECT -> false
                    PermissionChoice.ONCE -> true
                    PermissionChoice.ALWAYS -> {
                        if (eval.rememberablePatterns.isNotEmpty()) {
                            policyEngine.remember(tool.name, eval.rememberablePatterns, PermissionScope.PROJECT)
                        }
                        true
                    }
                }
            }
        }
    }

    private fun extractFinalContent(state: SessionState): String {
        for (i in state.messages.indices.reversed()) {
            val msg = state.messages[i]
            if (msg is AgentMessage.AssistantMessage && msg.content.isNotBlank()) {
                return msg.content.trim()
            }
        }
        return ""
    }
}
