package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicApi
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicMessageRequest
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicMessage
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicContentBlock
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicToolDefinition
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class AnthropicAdapter @Inject constructor(
    private val api: AnthropicApi
) : AIProvider {

    override var apiKey = ""
    override var baseUrl = "https://api.anthropic.com/"
    override var model = "claude-3-opus-20240229"

    override suspend fun complete(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): AIResponse {
        val anthropicMessages = convertToAnthropicMessages(messages)

        val toolDefs = tools.takeIf { it.isNotEmpty() }?.map { tool ->
            AnthropicToolDefinition(
                name = tool.name,
                description = tool.description,
                input_schema = tool.toJsonSchema()
            )
        }

        val response = api.createMessage(
            url = joinUrl(baseUrl, "v1/messages"),
            apiKey = apiKey,
            request = AnthropicMessageRequest(
                model = model,
                messages = anthropicMessages,
                system = systemPrompt.ifBlank { null },
                tools = toolDefs,
                stream = false
            )
        )

        var contentText = ""
        val toolCalls = mutableListOf<ToolCall>()

        for (block in response.content) {
            when (block.type) {
                "text" -> contentText += block.text ?: ""
                "tool_use" -> {
                    val arguments = block.input?.let { mapToJson(it) } ?: JsonObject(emptyMap())
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

        return AIResponse(content = contentText, toolCalls = toolCalls)
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
                         @Suppress("UNCHECKED_CAST")
                         val inputMap = jsonElementToMap(JsonObject(toolCall.arguments)) as Map<String, Any>

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

    /** Convert a Map<String, Any> (from Gson) to a JsonObject */
    private fun mapToJson(map: Map<String, Any>): JsonObject {
        val mutable = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for ((k, v) in map) {
            mutable[k] = when (v) {
                is String -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    mapToJson(v as Map<String, Any>)
                }
                else -> JsonPrimitive(v.toString())
            }
        }
        return JsonObject(mutable)
    }

    /** Convert a JsonObject back to Map<String, Any> for Anthropic API */
    private fun jsonElementToMap(element: kotlinx.serialization.json.JsonElement): Any {
        return when (element) {
            is JsonObject -> element.mapValues { (_, v) -> jsonElementToMap(v) }
            is kotlinx.serialization.json.JsonArray -> element.map { jsonElementToMap(it) }
            is JsonPrimitive -> element.contentOrNull ?: ""
            else -> element.toString()
        }
    }
}
