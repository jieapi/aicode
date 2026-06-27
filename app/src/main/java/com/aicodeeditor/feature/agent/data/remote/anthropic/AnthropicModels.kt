package com.aicodeeditor.feature.agent.data.remote.anthropic

data class AnthropicMessageRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val max_tokens: Int = 16384,
    val temperature: Float = 0.7f,
    val tools: List<AnthropicToolDefinition>? = null,
    val stream: Boolean = false
)

data class AnthropicMessage(
    val role: String, // "user" or "assistant"
    val content: Any // Can be String or List<AnthropicContentBlock>
)

data class AnthropicContentBlock(
    val type: String, // "text", "tool_use", "tool_result"
    val text: String? = null,
    val id: String? = null, // for tool_use
    val name: String? = null, // for tool_use
    val input: Map<String, Any>? = null, // for tool_use
    val tool_use_id: String? = null, // for tool_result
    val content: String? = null, // for tool_result
    val is_error: Boolean? = null // for tool_result
)

data class AnthropicToolDefinition(
    val name: String,
    val description: String,
    val input_schema: Map<String, Any>
)

data class AnthropicMessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicContentBlock>,
    val model: String,
    val stop_reason: String?,
    val stop_sequence: String?,
    val usage: AnthropicUsage
)

data class AnthropicUsage(
    val input_tokens: Int,
    val output_tokens: Int
)
