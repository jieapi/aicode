package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIApi
import com.aicodeeditor.feature.agent.data.remote.openai.ChatCompletionRequest
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIChatMessage
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIToolCall
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIToolDefinition

class OpenAIAdapter @Inject constructor(
    private val api: OpenAIApi
) : AIProvider {

    // Default model to use
    var model = "gpt-4-turbo"

    override suspend fun complete(request: String, messages: List<AgentMessage>): AIResponse {
        val openAIMessages = convertToOpenAIMessages(messages)
        // Add the current request as a user message
        openAIMessages.add(OpenAIChatMessage(role = "user", content = request))

        val response = api.createChatCompletion(
            ChatCompletionRequest(
                model = model,
                messages = openAIMessages,
                stream = false
            )
        )

        val choice = response.choices.firstOrNull()
        val message = choice?.message
        val content = message?.content ?: ""

        val toolCalls = message?.tool_calls?.map { convertToToolCall(it) } ?: emptyList()

        return AIResponse(
            content = content,
            toolCalls = toolCalls
        )
    }

    override fun completeStream(request: String, messages: List<AgentMessage>): Flow<String> = flow {
        // Simple implementation that just calls the non-streaming version and emits the result
        // A true streaming implementation would use Server-Sent Events (SSE) parsing
        val response = complete(request, messages)
        emit(response.content)
    }

    private fun convertToOpenAIMessages(messages: List<AgentMessage>): MutableList<OpenAIChatMessage> {
        return messages.map { message ->
            when (message) {
                is AgentMessage.UserMessage -> OpenAIChatMessage(role = "user", content = message.content)
                is AgentMessage.AssistantMessage -> {
                    val toolCalls = if (message.toolCalls.isNotEmpty()) {
                        message.toolCalls.map { convertToOpenAIToolCall(it) }
                    } else null
                    OpenAIChatMessage(role = "assistant", content = message.content, tool_calls = toolCalls)
                }
                is AgentMessage.ToolResultMessage -> OpenAIChatMessage(
                    role = "tool",
                    content = message.result,
                    tool_call_id = message.id // Note: In a real app we need to track tool_call_ids
                )
            }
        }.toMutableList()
    }

    private fun convertToToolCall(openAIToolCall: OpenAIToolCall): ToolCall {
        val argumentsJson = Json.parseToJsonElement(openAIToolCall.function.arguments).jsonObject
        return ToolCall(
            id = openAIToolCall.id,
            name = openAIToolCall.function.name,
            arguments = argumentsJson
        )
    }

    private fun convertToOpenAIToolCall(toolCall: ToolCall): OpenAIToolCall {
        return OpenAIToolCall(
            id = toolCall.id,
            type = "function",
            function = com.aicodeeditor.feature.agent.data.remote.openai.OpenAIFunctionCall(
                name = toolCall.name,
                arguments = toolCall.arguments.toString()
            )
        )
    }
}
