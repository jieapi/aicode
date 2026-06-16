package com.aicodeeditor.feature.agent.data.remote.openai

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface OpenAIApi {
    @POST
    suspend fun createChatCompletion(
        @Url url: String,
        @retrofit2.http.Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}
