package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.WorkflowResult
import com.aicodeeditor.feature.agent.domain.model.WorkflowStatus
import com.aicodeeditor.feature.agent.domain.provider.AIProvider
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolRegistry
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StandardAgentWorkflow @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val aiProvider: AIProvider
) : AgentWorkflow {

    override suspend fun execute(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): WorkflowResult {
        val messages = mutableListOf<AgentMessage>()
        val errors = mutableListOf<String>()
        var maxIterations = 10

        try {
            // 添加系统提示词 (暂不支持)
            // val systemPrompt = buildSystemPrompt(context, tools)

            // 添加历史记录
            messages.addAll(context.history)

            // 添加用户消息
            messages.add(AgentMessage.UserMessage(content = buildPromptWithContext(userRequest, context, tools)))

            var finalContent = ""

            // Agent 循环
            while (maxIterations > 0) {
                maxIterations--

                // 调用 AI
                val aiResponse = aiProvider.complete(userRequest, messages)

                messages.add(
                    AgentMessage.AssistantMessage(
                        content = aiResponse.content,
                        toolCalls = aiResponse.toolCalls
                    )
                )

                if (aiResponse.content.isNotEmpty()) {
                    finalContent += aiResponse.content + "\n"
                }

                // 如果没有工具调用，结束循环
                if (aiResponse.toolCalls.isEmpty()) {
                    break
                }

                // 执行工具
                for (toolCall in aiResponse.toolCalls) {
                    try {
                        val tool = toolRegistry.getTool(toolCall.name)
                        if (tool != null) {
                            val result = tool.execute(toolCall.arguments)
                            messages.add(
                                AgentMessage.ToolResultMessage(
                                    id = toolCall.id,
                                    toolName = toolCall.name,
                                    result = result.toString()
                                )
                            )
                        } else {
                            messages.add(
                                AgentMessage.ToolResultMessage(
                                    id = toolCall.id,
                                    toolName = toolCall.name,
                                    result = "Error: Tool ${toolCall.name} not found"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        messages.add(
                            AgentMessage.ToolResultMessage(
                                id = toolCall.id,
                                toolName = toolCall.name,
                                result = "Error executing tool: ${e.message}"
                            )
                        )
                    }
                }
            }

            if (maxIterations == 0) {
                errors.add("达到最大迭代次数限制 (10 次)")
            }

            return WorkflowResult(
                status = WorkflowStatus.SUCCESS,
                result = finalContent.trim(),
                changes = emptyList(), // Changes are handled by CodeChangeTracker
                errors = errors,
                messages = messages
            )

        } catch (e: Exception) {
            return WorkflowResult(
                status = WorkflowStatus.FAILED,
                result = "工作流执行失败: ${e.message}",
                errors = listOf(e.message ?: "未知错误"),
                messages = messages
            )
        }
    }

    override fun executeStream(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): Flow<String> = flow {
        // ... similar implementation, emitting chunks
        val messages = mutableListOf<AgentMessage>()

        messages.addAll(context.history)
        messages.add(AgentMessage.UserMessage(content = buildPromptWithContext(userRequest, context, tools)))

        try {
            aiProvider.completeStream(userRequest, messages).collect { chunk ->
                emit(chunk)
            }
        } catch (e: Exception) {
            emit("Error: ${e.message}")
        }
    }

    private fun buildPromptWithContext(request: String, context: AgentContext, tools: List<AgentTool>): String {
        val toolDescriptions = tools.joinToString("\n") { tool ->
            """
            工具: ${tool.name}
            描述: ${tool.description}
            参数: ${tool.parameters.values.joinToString(", ") { "${it.name}(${it.type})" }}
            """.trimIndent()
        }

        return """
            $request

            ---
            当前上下文信息:
            - 项目根目录: ${context.projectRoot}
            - 当前文件: ${context.currentFile ?: "无"}
            - 选中的代码: ${context.selectedCode ?: "无"}

            可用的工具:
            $toolDescriptions
        """.trimIndent()
    }
}
