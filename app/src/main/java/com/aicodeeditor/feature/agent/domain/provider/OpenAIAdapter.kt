package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIApi
import com.aicodeeditor.feature.agent.data.remote.openai.ChatCompletionRequest
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIChatMessage
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIToolCall
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIToolDefinition
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIFunctionDefinition

class OpenAIAdapter @Inject constructor(
    private val api: OpenAIApi
) : AIProvider {

    override var apiKey = ""
    override var baseUrl = "https://api.openai.com/"
    override var model = "gpt-4-turbo"

    override suspend fun complete(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): AIResponse {
        val openAIMessages = buildList {
            if (systemPrompt.isNotBlank()) {
                add(OpenAIChatMessage(role = "system", content = systemPrompt))
            }
            addAll(convertToOpenAIMessages(messages))
        }

        val toolDefs = tools.takeIf { it.isNotEmpty() }?.map { tool ->
            OpenAIToolDefinition(
                function = OpenAIFunctionDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.toJsonSchema()
                )
            )
        }

        val response = api.createChatCompletion(
            url = joinUrl(baseUrl, "v1/chat/completions"),
            authorization = "Bearer $apiKey",
            request = ChatCompletionRequest(
                model = model,
                messages = openAIMessages,
                tools = toolDefs,
                stream = false
            )
        )

        val message = response.choices.firstOrNull()?.message
        val content = message?.content ?: ""
        val toolCalls = message?.tool_calls?.map { convertToToolCall(it) } ?: emptyList()

        return AIResponse(content = content, toolCalls = toolCalls)
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
                    tool_call_id = message.id
                )
            }
        }.toMutableList()
    }

    private fun convertToToolCall(openAIToolCall: OpenAIToolCall): ToolCall {
        val argumentsJson = runCatching {
            Json.parseToJsonElement(openAIToolCall.function.arguments).jsonObject
        }.getOrElse { JsonObject(emptyMap()) }
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
                arguments = JsonObject(toolCall.arguments).toString()
            )
        )
    }
}
