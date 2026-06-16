package com.aicodeeditor.feature.agent.domain.workflow

import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.WorkflowResult
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import kotlinx.coroutines.flow.Flow

/**
 * Agent 执行过程中向 UI 推送的分步事件。
 * 每个事件对应聊天里的一条（或对一条的更新），由 ViewModel 落库并渲染。
 */
sealed class AgentEvent {
    /** 模型产出的一段文字回复（可能同时伴随工具调用）。 */
    data class AssistantText(
        val content: String,
        val toolCalls: List<ToolCall> = emptyList()
    ) : AgentEvent()

    /** 模型决定调用某工具（执行前）。 */
    data class ToolCallStarted(val id: String, val toolName: String, val argsPreview: String) : AgentEvent()

    /** 工具执行过程中的实时累积输出（仅流式工具产生，用于 UI 实时渲染，不落库）。 */
    data class ToolCallProgress(val id: String, val toolName: String, val accumulated: String) : AgentEvent()

    /** 工具执行完成的结果（成功或失败）。 */
    data class ToolCallFinished(val id: String, val toolName: String, val result: String, val isError: Boolean) : AgentEvent()

    /** 整个流程结束。 */
    object Completed : AgentEvent()
}

interface AgentWorkflow {
    suspend fun execute(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): WorkflowResult

    /**
     * 运行 Agent 循环并以事件流的形式分步推送进度：
     * 模型回复 → （可能的）工具调用 → 工具结果 → 再回复 …… 直至模型不再调用工具。
     */
    fun executeEvents(
        userRequest: String,
        context: AgentContext,
        tools: List<AgentTool>
    ): Flow<AgentEvent>
}
