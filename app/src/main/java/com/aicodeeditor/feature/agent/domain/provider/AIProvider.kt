package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import kotlinx.coroutines.flow.Flow

data class AIResponse(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList()
)

interface AIProvider {
    suspend fun complete(request: String, messages: List<AgentMessage>): AIResponse
    fun completeStream(request: String, messages: List<AgentMessage>): Flow<String>
}
