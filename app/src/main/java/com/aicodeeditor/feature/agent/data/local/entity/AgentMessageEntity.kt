package com.aicodeeditor.feature.agent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aicodeeditor.feature.agent.presentation.AgentAttachment
import com.aicodeeditor.feature.agent.presentation.MessageRole
import com.aicodeeditor.feature.agent.presentation.AgentUIMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
    // 仅 USER 行：用户上传附件的展示元数据。内部模型提示仍不落入 content。
    val attachmentsJson: String? = null,
    /** 该消息已被上下文压缩归入摘要，不应再参与上下文回放或 UI 展示。默认 false。 */
    val isCompacted: Boolean = false,
    /** 上下文压缩生成的内部摘要：参与模型回放，但不作为普通聊天气泡展示。 */
    val isContextSummary: Boolean = false,
    /** 上下文压缩生成的内部用户锚点：参与模型回放，UI 渲染为压缩分隔线。 */
    val isCompactionMarker: Boolean = false
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
            reasoning = reasoning,
            attachments = decodeAttachments(attachmentsJson),
            isCompactionMarker = isCompactionMarker
        )
    }

    private fun decodeAttachments(value: String?): List<AgentAttachment> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<AgentAttachment>>(value) }.getOrDefault(emptyList())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
