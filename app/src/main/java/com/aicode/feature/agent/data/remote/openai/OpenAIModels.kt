package com.aicode.feature.agent.data.remote.openai

data class ChatCompletionRequest(
    val model: String,
    val messages: List<OpenAIChatMessage>,
    val temperature: Float = 0.7f,
    val tools: List<OpenAIToolDefinition>? = null,
    val tool_choice: String? = null,
    val stream: Boolean = false,
    val stream_options: StreamOptions? = null
)

data class OpenAIChatMessage(
    val role: String,
    val content: Any?,
    val name: String? = null,
    val tool_calls: List<OpenAIToolCall>? = null,
    val tool_call_id: String? = null,
    /** DeepSeek 思考模式要求将上轮 assistant 消息的 reasoning_content 原样回传，否则 400。 */
    val reasoning_content: String? = null
)

data class OpenAIToolDefinition(
    val type: String = "function",
    val function: OpenAIFunctionDefinition
)

data class OpenAIFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class OpenAIToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAIFunctionCall
)

data class OpenAIFunctionCall(
    val name: String,
    val arguments: String
)

data class StreamOptions(
    val include_usage: Boolean = true
)

data class ChatCompletionResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: OpenAIChatMessage?,
    val delta: OpenAIChatMessage?, // Used for streaming
    val finish_reason: String?
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)
