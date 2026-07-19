package com.aicode.feature.agent.domain.provider

import com.aicode.feature.agent.domain.model.AgentMessage
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ToolCall
import kotlinx.coroutines.flow.Flow

data class AIResponse(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    /**
     * 模型停止的原因。Anthropic: "end_turn" / "tool_use" / "max_tokens"；
     * OpenAI: "stop" / "tool_calls" / "length"。
     * 当值为 "max_tokens" 或 "length" 时表示输出因 token 上限被截断，Agent 循环应自动续写。
     */
    val stopReason: String? = null,
    /** 本轮模型的完整思考过程（对应 OpenAI/DeepSeek 的 reasoning_content）。非空时需回传给 API，否则 DeepSeek 思考模式会报 400。 */
    val reasoning: String? = null,
    /** 本轮输入 token 数（来自 API 返回的 usage）。取不到时为 0。 */
    val inputTokens: Int = 0,
    /** 本轮输出 token 数（来自 API 返回的 usage）。取不到时为 0。 */
    val outputTokens: Int = 0
) {
    val isTruncated: Boolean
        get() = stopReason == "max_tokens" || stopReason == "length"
}

/**
 * 流式补全过程中向上游推送的分块。
 * [TextDelta] 为模型新吐出的一小段文字（增量，非累积）；
 * [Final] 在本轮结束时给出完整结果（聚合后的文字 + 工具调用），供 Agent 循环驱动后续工具执行。
 * [Retrying] 在网络重试时推送，供 UI 展示"正在重试"提示。
 */
sealed class AIStreamChunk {
    data class TextDelta(val text: String) : AIStreamChunk()
    /** 模型新吐出的一小段思考过程（增量，非累积）。仅用于 UI 实时展示，不进入上下文回放。 */
    data class ReasoningDelta(val text: String) : AIStreamChunk()
    data class Final(val response: AIResponse) : AIStreamChunk()
    /** 网络请求正在重试。仅用于 UI 实时展示，不进入上下文回放。 */
    data class Retrying(val attempt: Int, val maxRetries: Int) : AIStreamChunk()
}

interface AIProvider {
    var apiKey: String
    var baseUrl: String
    var useFullUrl: Boolean
    var useResponseApi: Boolean
    var model: String

    /**
     * 当前会话 id，仅用于日志归档：调用前由工作流设置，[com.aicode.core.util.AILogger]
     * 据此把每次请求/响应写到对应会话的文件。为 null 时落到 `session-unknown.log`。
     */
    var logSessionId: String?

    /**
     * 单轮补全。[tools] 会以提供商的 function-calling 格式真正发给模型，
     * 模型若决定调用工具，结果会出现在返回的 [AIResponse.toolCalls] 中。
     */
    suspend fun complete(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool> = emptyList()
    ): AIResponse

    /**
     * 流式单轮补全：以 SSE 逐字接收模型回复。文字以 [AIStreamChunk.TextDelta] 增量推送，
     * 本轮结束时以 [AIStreamChunk.Final] 给出聚合后的完整 [AIResponse]（含工具调用）。
     * 工具调用的 function-calling 语义与 [complete] 一致。
     */
    fun completeStream(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool> = emptyList()
    ): Flow<AIStreamChunk>
}

/**
 * Builds an absolute request URL from a user-configured base URL and an API path
 * such as "v1/chat/completions". Tolerates trailing slashes and a base URL that
 * already ends with the version segment (e.g. "https://host/v1") so it isn't duplicated.
 */
fun joinUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trim().trimEnd('/')
    val cleanPath = path.trimStart('/')
    
    val lastSegment = base.substringAfterLast('/', "")
    
    return if (lastSegment.isNotEmpty() && cleanPath.startsWith("$lastSegment/")) {
        "$base/${cleanPath.removePrefix("$lastSegment/")}"
    } else {
        "$base/$cleanPath"
    }
}
