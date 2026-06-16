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
    // 仅 TOOL 行：工具是否执行失败，用于 UI 渲染 ✅/❌。
    val isError: Boolean = false
) {
    fun toUIMessage(): AgentUIMessage {
        return AgentUIMessage(
            id = id,
            role = MessageRole.valueOf(role),
            content = content,
            timestamp = timestamp,
            toolName = toolName,
            isError = isError
        )
    }
}
