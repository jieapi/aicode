package com.aicodeeditor.feature.agent.data.remote.openai

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

interface OpenAIApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    @Streaming
    @POST("v1/chat/completions")
    suspend fun createChatCompletionStream(
        @Body request: ChatCompletionRequest
    ): ResponseBody
}
