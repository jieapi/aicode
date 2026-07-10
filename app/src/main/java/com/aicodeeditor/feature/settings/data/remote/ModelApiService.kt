package com.aicodeeditor.feature.settings.data.remote

import com.aicodeeditor.feature.agent.domain.provider.joinUrl
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** 测试模型连通性的结果。 */
data class ModelTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String
)

/**
 * 直接通过 OkHttp 调用提供商的 REST 接口来拉取模型列表与测试连通性，
 * 复用全局 OkHttpClient，独立于聊天用的 Retrofit 适配器。
 */
@Singleton
class ModelApiService @Inject constructor(
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 拉取提供商可用模型列表（OpenAI 兼容 / Anthropic 均为 GET /v1/models，Gemini 为 GET /v1beta/models）。 */
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        type: ProviderType
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            if (apiKey.isBlank()) error("请先填写 API Key")

            val modelsPath = if (type == ProviderType.GEMINI) "v1beta/models" else "v1/models"
            val request = Request.Builder()
                .url(joinUrl(baseUrl, modelsPath))
                .applyAuth(apiKey, type)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${body.take(200)}")
                }

                val jsonObj = json.parseToJsonElement(body).jsonObject
                val data = if (type == ProviderType.GEMINI) {
                    jsonObj["models"]?.jsonArray
                } else {
                    jsonObj["data"]?.jsonArray
                } ?: error("响应缺少列表字段")

                data.mapNotNull { 
                        it.jsonObject[if (type == ProviderType.GEMINI) "name" else "id"]?.jsonPrimitive?.contentOrNull 
                    }
                    .map { if (type == ProviderType.GEMINI) it.removePrefix("models/") else it }
                    .filter { it.isNotBlank() }
                    .sorted()
            }
        }
    }

    /** 发送一条极短请求验证 Key + 模型 + 端点是否可用，返回耗时。 */
    suspend fun testModel(
        baseUrl: String,
        apiKey: String,
        type: ProviderType,
        apiPath: String,
        useResponseApi: Boolean,
        model: String
    ): ModelTestResult = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        try {
            if (apiKey.isBlank()) error("请先填写 API Key")

            val (url, payload) = when (type) {
                ProviderType.ANTHROPIC -> joinUrl(baseUrl, apiPath) to
                    """{"model":${model.jsonStr()},"max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
                ProviderType.GEMINI -> {
                    val path = if (apiPath.endsWith("/")) "$apiPath$model:generateContent" else "$apiPath/$model:generateContent"
                    joinUrl(baseUrl, path) to
                        """{"contents":[{"role":"user","parts":[{"text":"hi"}]}]}"""
                }
                else -> {
                    if (useResponseApi) {
                        joinUrl(baseUrl, apiPath) to
                            """{"model":${model.jsonStr()},"input":[{"role":"user","content":"hi"}]}"""
                    } else {
                        joinUrl(baseUrl, apiPath) to
                            """{"model":${model.jsonStr()},"max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
                    }
                }
            }

            val request = Request.Builder()
                .url(url)
                .applyAuth(apiKey, type)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val latency = (System.nanoTime() - start) / 1_000_000
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    ModelTestResult(true, latency, "连通 · ${latency}ms")
                } else {
                    ModelTestResult(false, latency, "HTTP ${response.code}: ${body.take(160)}")
                }
            }
        } catch (e: Exception) {
            val latency = (System.nanoTime() - start) / 1_000_000
            ModelTestResult(false, latency, e.message ?: "请求失败")
        }
    }

    private fun Request.Builder.applyAuth(apiKey: String, type: ProviderType): Request.Builder =
        when (type) {
            ProviderType.ANTHROPIC -> this
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
            ProviderType.GEMINI -> this
                .header("x-goog-api-key", apiKey)
            else -> this.header("Authorization", "Bearer $apiKey")
        }

    /** 转成安全的 JSON 字符串字面量（含引号、正确转义）。 */
    private fun String.jsonStr(): String = JsonPrimitive(this).toString()
}
