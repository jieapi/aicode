package com.aicode.feature.agent.domain.workflow

import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.model.AgentMode
import com.aicode.feature.agent.domain.model.WorkflowResult
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ToolCall
import kotlinx.coroutines.flow.Flow

/**
 * Agent 执行过程中向 UI 推送的分步事件。
 * 每个事件对应聊天里的一条（或对一条的更新），由 ViewModel 落库并渲染。
 */
sealed class AgentEvent {
    /** 模型产出的一段文字回复（可能同时伴随工具调用）。[reasoning] 为本轮思考过程，落库供历史展示，空串表示无。 */
    data class AssistantText(
        val content: String,
        val toolCalls: List<ToolCall> = emptyList(),
        val reasoning: String = "",
        val inputTokens: Int = 0,
        val outputTokens: Int = 0
    ) : AgentEvent()

    /** 流式过程中模型逐字吐出的文字（[accumulated] 为本轮已累积的完整文本，用于 UI 实时渲染，不落库）。 */
    data class AssistantDelta(val accumulated: String) : AgentEvent()

    /** 流式过程中模型逐字吐出的思考过程（[accumulated] 为本轮已累积的完整思考，用于 UI 实时渲染，不落库）。 */
    data class ReasoningDelta(val accumulated: String) : AgentEvent()

    /** 模型决定调用某工具（执行前）。 */
    data class ToolCallStarted(val id: String, val toolName: String, val argsPreview: String) : AgentEvent()

    /** 工具执行过程中的实时累积输出（仅流式工具产生，用于 UI 实时渲染，不落库）。 */
    data class ToolCallProgress(val id: String, val toolName: String, val accumulated: String) : AgentEvent()

    /** 工具执行完成的结果（成功、失败或被用户拒绝）。 */
    data class ToolCallFinished(
        val id: String,
        val toolName: String,
        val result: String,
        val isError: Boolean,
        val argsPreview: String? = null
    ) : AgentEvent()

    /** 网络请求正在重试（首字节前失败触发自动重试）。仅用于 UI 实时展示，不落库。 */
    data class Retrying(val attempt: Int, val maxRetries: Int) : AgentEvent()

    /** 正在进行上下文压缩。仅用于 UI 实时展示，不落库。 */
    data class CompactionStarted(val estimatedTokens: Int) : AgentEvent()

    /** 上下文压缩流程已结束（成功或失败）。仅用于 UI 实时展示，不落库。 */
    object CompactionFinished : AgentEvent()

    /** 整个流程因错误终止（如流式请求被网络中断、达到迭代上限）。与 [Completed] 区别：携带错误，UI 应展示错误而非成功。 */
    data class Failed(val error: String) : AgentEvent()

    /** 整个流程结束。 */
    object Completed : AgentEvent()

    /** 模式已切换（由 AI 调用 switchMode 触发），UI 据此展示计划审查面板等。 */
    data class ModeChanged(val newMode: AgentMode, val reason: String) : AgentEvent()
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

    /**
     * 手动触发指定会话的上下文压缩（供 /compress 命令调用）。
     * 复用自动压缩逻辑：解析当前生效 provider，对历史消息调用 ContextCompactor。
     * 压缩结果由 ContextCompactor 内部持久化，通过 [onEvent] 回调推送压缩进度事件。
     * @return 是否实际触发了压缩（历史未超阈值时返回 false，不改动）。
     */
    suspend fun compactSession(sessionId: String, onEvent: suspend (AgentEvent) -> Unit = {}): Boolean
}
