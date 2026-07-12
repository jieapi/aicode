package com.aicode.feature.agent.domain.model

import kotlinx.serialization.Serializable
import com.aicode.feature.agent.domain.tool.ToolCall

sealed class AgentMessage {
    @Serializable
    data class UserMessage(
        val id: String = "",
        val content: String,
        val images: List<AgentImage> = emptyList()
    ) : AgentMessage()

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
        val result: String,
        val images: List<AgentImage> = emptyList()
    ) : AgentMessage()
}

@Serializable
data class AgentImage(
    val mimeType: String,
    val base64Data: String,
    val path: String = ""
)

const val CONTEXT_COMPACTION_MARKER = "What did we do so far?"
const val CONTEXT_SUMMARY_LEGACY_PREFIX = "【系统提示：早期的对话已被压缩，以下是之前的核心状态摘要】"

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
    val inputImages: List<AgentImage> = emptyList(),
    /** 当前会话 id：用于把本轮所有 AI 请求/响应落到该会话的日志文件（[com.aicode.core.util.AILogger]）。 */
    val sessionId: String? = null,
    val mode: AgentMode = AgentMode.BUILD
)
