package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.WorkflowResult
import com.aicodeeditor.feature.agent.domain.model.WorkflowStatus
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.StreamingAgentTool
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
    private val anthropicProvider: AIProvider
) : AgentWorkflow {

    private companion object {
        const val TAG = "StandardAgentWorkflow"
        const val MAX_ITERATIONS = 10
    }

    private suspend fun getActiveProvider(): AIProvider {
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
            val aiProvider = getActiveProvider()
            var finalContent = ""
            var iterations = 0

            while (iterations < MAX_ITERATIONS) {
                iterations++

                val aiResponse = aiProvider.complete(systemPrompt, messages, tools)
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
                    val result = runTool(toolCall.name, toolCall.arguments)
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
        val aiProvider = getActiveProvider()
        var iterations = 0

        while (iterations < MAX_ITERATIONS) {
            iterations++

            val aiResponse = aiProvider.complete(systemPrompt, messages, tools)
            messages.add(
                AgentMessage.AssistantMessage(
                    content = aiResponse.content,
                    toolCalls = aiResponse.toolCalls
                )
            )

            // 即便没有文字、只有工具调用，也要 emit：让 ViewModel 把 assistant(tool_calls) 落库，
            // 否则回放时会出现没有配对 assistant 的孤儿 tool_result，破坏 API 约束。
            if (aiResponse.content.isNotBlank() || aiResponse.toolCalls.isNotEmpty()) {
                emit(AgentEvent.AssistantText(aiResponse.content, aiResponse.toolCalls))
            }

            if (aiResponse.toolCalls.isEmpty()) break

            for (toolCall in aiResponse.toolCalls) {
                emit(
                    AgentEvent.ToolCallStarted(
                        id = toolCall.id,
                        toolName = toolCall.name,
                        argsPreview = JsonObject(toolCall.arguments).toString().take(500)
                    )
                )
                val tool = toolRegistry.getTool(toolCall.name)
                // 流式工具：逐行透传过程输出，结束后取最终聚合结果；其余走原同步路径。
                val rawResult = if (tool is StreamingAgentTool) {
                    val accumulated = StringBuilder()
                    var finalResult: ToolResult? = null
                    try {
                        tool.executeStream(toolCall.arguments).collect { ev ->
                            when (ev) {
                                is ToolStreamEvent.Progress -> {
                                    accumulated.append(ev.chunk).append("\n")
                                    emit(
                                        AgentEvent.ToolCallProgress(
                                            id = toolCall.id,
                                            toolName = toolCall.name,
                                            accumulated = accumulated.toString()
                                        )
                                    )
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
        val tool = toolRegistry.getTool(name) ?: return "Error: 工具 $name 不存在"
        return try {
            tool.execute(arguments).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "工具执行失败: $name", e)
            "Error: 工具执行失败: ${e.message}"
        }
    }

    private fun buildSystemPrompt(context: AgentContext): String {
        val hasWorkspace = context.projectRoot.isNotBlank()
        return """
            你是一个 AI 编程助手，可以通过调用工具来帮助用户完成编程任务（读写文件、修改代码、执行命令等）。
            当需要操作文件或运行命令时，请直接调用提供的工具，不要把工具调用写成普通文本或代码块。

            工具使用约定:
            - read_file 读文件；修改已有文件优先用 edit_file（基于 old_string/new_string 精确匹配，
              改前先 read_file 拿到确切原文，old_string 要带足够上下文以保证唯一）。
            - write_file 用于新建文件或整文件重写；不要用它做局部小改动。
            - 其余操作（列目录、搜索、构建、lint、格式化、git 等）一律通过 execute_command 执行 shell 命令。

            路径约定（重要）:
            - 项目根目录固定为容器内路径 /workspace。
            - 所有文件路径都基于 /workspace，例如 /workspace/src/Main.kt，或直接用相对路径 src/Main.kt。
            - execute_command 也在 /workspace 内执行（如同 `cd /workspace`），不要使用宿主机的真实路径。

            当前上下文:
            - 项目根目录: ${if (hasWorkspace) "/workspace" else "（未选择工作区）"}
            - 当前文件: ${context.currentFile ?: "无"}
            - 选中的代码: ${context.selectedCode ?: "无"}
            - 编程语言: ${context.language ?: "未知"}
        """.trimIndent()
    }
}
