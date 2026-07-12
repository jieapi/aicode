package com.aicode.feature.agent.data.remote.gemini

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface GeminiApi {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: Any
    ): com.google.gson.JsonObject

    @Streaming
    @POST
    suspend fun streamGenerateContent(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: Any
    ): ResponseBody
}
