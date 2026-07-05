package com.aicodeeditor.feature.agent.domain.model

import kotlinx.serialization.Serializable
import com.aicodeeditor.feature.agent.domain.tool.ToolCall

sealed class AgentMessage {
    @Serializable
    data class UserMessage(val id: String = "", val content: String) : AgentMessage()

    @Serializable
    data class AssistantMessage(
        val id: String = "",
        val content: String,
        val toolCalls: List<ToolCall> = emptyList(),
        /** 本轮模型的思考过程（对应 OpenAI/DeepSeek 的 reasoning_content）。回传上下文时需要原样发回，否则 DeepSeek 思考模式会报 400 错误。 */
        val reasoning: String = ""
    ) : AgentMessage()

    @Serializable
    data class ToolResultMessage(
        val id: String = "",
        val toolName: String,
        val result: String
    ) : AgentMessage()
}

val AgentMessage.id: String
    get() = when (this) {
        is AgentMessage.UserMessage -> id
        is AgentMessage.AssistantMessage -> id
        is AgentMessage.ToolResultMessage -> id
    }

data class AgentContext(
    val currentFile: String?,
    val selectedCode: String?,
    val projectRoot: String,
    val language: String?,
    val history: List<AgentMessage> = emptyList(),
    /** 当前会话 id：用于把本轮所有 AI 请求/响应落到该会话的日志文件（[com.aicodeeditor.core.util.AILogger]）。 */
    val sessionId: String? = null,
    val mode: AgentMode = AgentMode.BUILD
)
