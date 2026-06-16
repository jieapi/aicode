package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCall

data class AIResponse(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList()
)

interface AIProvider {
    var apiKey: String
    var baseUrl: String
    var model: String

    /**
     * 单轮补全。[tools] 会以服务商的 function-calling 格式真正发给模型，
     * 模型若决定调用工具，结果会出现在返回的 [AIResponse.toolCalls] 中。
     */
    suspend fun complete(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool> = emptyList()
    ): AIResponse
}

/**
 * Builds an absolute request URL from a user-configured base URL and an API path
 * such as "v1/chat/completions". Tolerates trailing slashes and a base URL that
 * already ends with the version segment (e.g. "https://host/v1") so it isn't duplicated.
 */
fun joinUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trim().trimEnd('/')
    val cleanPath = path.trimStart('/')
    return if (base.endsWith("/v1")) {
        "$base/${cleanPath.removePrefix("v1/")}"
    } else {
        "$base/$cleanPath"
    }
}
