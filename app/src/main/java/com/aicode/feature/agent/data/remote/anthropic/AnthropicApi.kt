package com.aicode.feature.agent.data.remote.anthropic

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface AnthropicApi {
    @POST
    suspend fun createMessage(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: AnthropicMessageRequest
    ): AnthropicMessageResponse

    /** 流式（SSE）补全：返回原始响应体，由调用方逐行解析 event-stream。请求需带 stream=true。 */
    @Streaming
    @POST
    suspend fun streamMessage(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: AnthropicMessageRequest
    ): ResponseBody
}
