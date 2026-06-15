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
        val toolCalls: List<ToolCall> = emptyList()
    ) : AgentMessage()

    @Serializable
    data class ToolResultMessage(
        val id: String = "",
        val toolName: String,
        val result: String
    ) : AgentMessage()
}

data class AgentContext(
    val currentFile: String?,
    val selectedCode: String?,
    val projectRoot: String,
    val language: String?,
    val history: List<AgentMessage> = emptyList()
)
