package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicApi
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicMessageRequest
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicMessage
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicContentBlock
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class AnthropicAdapter @Inject constructor(
    private val api: AnthropicApi
) : AIProvider {

    // Default model to use
    var model = "claude-3-opus-20240229"
    var apiKey = "" // In real app, this should come from a secure storage

    override suspend fun complete(request: String, messages: List<AgentMessage>): AIResponse {
        val anthropicMessages = convertToAnthropicMessages(messages)
        anthropicMessages.add(AnthropicMessage(role = "user", content = request))

        val response = api.createMessage(
            apiKey = apiKey,
            request = AnthropicMessageRequest(
                model = model,
                messages = anthropicMessages,
                stream = false
            )
        )

        var contentText = ""
        val toolCalls = mutableListOf<ToolCall>()

        for (block in response.content) {
            when (block.type) {
                "text" -> contentText += block.text ?: ""
                "tool_use" -> {
                    val arguments = block.input?.let {
                        Json.parseToJsonElement(Json.encodeToString(MapSerializer, it)).jsonObject
                    } ?: JsonObject(emptyMap())

                    toolCalls.add(
                        ToolCall(
                            id = block.id ?: "",
                            name = block.name ?: "",
                            arguments = arguments
                        )
                    )
                }
            }
        }

        return AIResponse(
            content = contentText,
            toolCalls = toolCalls
        )
    }

    override fun completeStream(request: String, messages: List<AgentMessage>): Flow<String> = flow {
         // Simple implementation that just calls the non-streaming version and emits the result
        val response = complete(request, messages)
        emit(response.content)
    }

    private fun convertToAnthropicMessages(messages: List<AgentMessage>): MutableList<AnthropicMessage> {
        val result = mutableListOf<AnthropicMessage>()

        for (message in messages) {
            when (message) {
                is AgentMessage.UserMessage -> {
                    result.add(AnthropicMessage(role = "user", content = message.content))
                }
                is AgentMessage.AssistantMessage -> {
                    val contentBlocks = mutableListOf<AnthropicContentBlock>()
                    if (message.content.isNotEmpty()) {
                        contentBlocks.add(AnthropicContentBlock(type = "text", text = message.content))
                    }

                    for (toolCall in message.toolCalls) {
                         // Converting arguments back to Map
                         // In a real app we need a proper JSON to Map converter
                         val inputMap = emptyMap<String, Any>()

                         contentBlocks.add(
                            AnthropicContentBlock(
                                type = "tool_use",
                                id = toolCall.id,
                                name = toolCall.name,
                                input = inputMap
                            )
                        )
                    }

                    if (contentBlocks.isNotEmpty()) {
                        result.add(AnthropicMessage(role = "assistant", content = contentBlocks))
                    }
                }
                is AgentMessage.ToolResultMessage -> {
                    result.add(
                        AnthropicMessage(
                            role = "user",
                            content = listOf(
                                AnthropicContentBlock(
                                    type = "tool_result",
                                    tool_use_id = message.id,
                                    content = message.result
                                )
                            )
                        )
                    )
                }
            }
        }

        return result
    }

    // Helper objects for serialization (placeholder)
    private object MapSerializer : kotlinx.serialization.KSerializer<Map<String, Any>> {
        override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor
            get() = TODO("Not yet implemented")
        override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Map<String, Any>) {
            TODO("Not yet implemented")
        }
        override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Map<String, Any> {
            TODO("Not yet implemented")
        }
    }
}
