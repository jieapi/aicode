package com.aicodeeditor.feature.agent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aicodeeditor.feature.agent.presentation.MessageRole
import com.aicodeeditor.feature.agent.presentation.AgentUIMessage

@Entity(tableName = "agent_messages")
data class AgentMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    // 仅 ASSISTANT 行：结构化的 tool_calls（Json 编码的 List<ToolCall>），无工具调用时为 null。
    val toolCallsJson: String? = null,
    // 仅 TOOL 行：对应 assistant tool_use 的 id，用于回放时重建合法配对。
    val toolCallId: String? = null,
    // 仅 TOOL 行：工具名，用于 UI 渲染。
    val toolName: String? = null,
    // 仅 TOOL 行：本次调用传入的参数（argsPreview，JSON 文本），用于 UI 显示「执行的指令」。
    val toolArgs: String? = null,
    // 仅 TOOL 行：工具是否执行失败，用于 UI 渲染状态圆点（绿/红）。
    val isError: Boolean = false,
    // 仅 ASSISTANT 行：本轮模型的思考过程（reasoning）。仅供 UI 持久化展示，不回放进上下文；无则为 null。
    val reasoning: String? = null,
    /** 该消息已被上下文压缩归入摘要，不应再参与上下文回放或 UI 展示。默认 false。 */
    val isCompacted: Boolean = false
) {
    fun toUIMessage(): AgentUIMessage {
        return AgentUIMessage(
            id = id,
            role = MessageRole.valueOf(role),
            content = content,
            timestamp = timestamp,
            toolName = toolName,
            toolArgs = toolArgs,
            isError = isError,
            reasoning = reasoning
        )
    }
}
