package com.aicode.feature.agent.domain.mcp

import com.aicode.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

/**
 * MCP「Streamable HTTP」传输实现。
 *
 * 单一端点 POST JSON-RPC；server 可回 `application/json`（单条响应）或
 * `text/event-stream`（SSE，我们读取其中首条带 id 的 message 事件即可，因为本传输是
 * 一问一答、不维持服务端推送通道）。`initialize` 响应里的 `Mcp-Session-Id` 头会被记下，
 * 之后每条请求都带上（spec 要求）。
 *
 * SSE 的解析方式与 AnthropicAdapter 一致——手动读 `data:` 行，避免引入 okhttp-sse。
 */
class StreamableHttpTransport(
    private val endpoint: String,
    private val client: OkHttpClient,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val json: Json = DEFAULT_JSON
) : McpTransport {

    private companion object {
        const val TAG = "McpHttpTransport"
        val JSON_MEDIA = "application/json".toMediaType()
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }

    private val idCounter = AtomicLong(0)

    @Volatile
    private var sessionId: String? = null

    override suspend fun request(method: String, params: JsonObject?): JsonRpcResponse =
        withContext(Dispatchers.IO) {
            val id = idCounter.incrementAndGet()
            val payload = JsonRpcRequest(id = id, method = method, params = params)
            val bodyJson = json.encodeToString(JsonRpcRequest.serializer(), payload)
            FileLogger.d(TAG, "→ [$method] id=$id")

            val httpReq = buildRequest(bodyJson)
            client.newCall(httpReq).execute().use { resp ->
                // 会话 id 在首个响应（initialize）里下发，记下供后续请求复用。
                resp.header("Mcp-Session-Id")?.let { if (it.isNotBlank()) sessionId = it }

                if (!resp.isSuccessful) {
                    throw McpException(message = "HTTP ${resp.code} 调用 $method 失败: ${resp.message}")
                }

                val contentType = resp.header("Content-Type").orEmpty()
                val rawJson = if (contentType.contains("text/event-stream", ignoreCase = true)) {
                    extractSseJson(resp.body?.charStream()?.buffered())
                        ?: throw McpException(message = "SSE 响应中未找到 $method 的数据")
                } else {
                    resp.body?.string()
                        ?: throw McpException(message = "$method 响应体为空")
                }

                parseAndValidate(rawJson, id, method)
            }
        }

    override suspend fun notify(method: String, params: JsonObject?) = withContext(Dispatchers.IO) {
        val payload = JsonRpcNotification(method = method, params = params)
        val bodyJson = json.encodeToString(JsonRpcNotification.serializer(), payload)
        FileLogger.d(TAG, "→ notify [$method]")
        client.newCall(buildRequest(bodyJson)).execute().use { resp ->
            // 通知按 spec 服务端通常返回 202 且无 body；非 2xx 仅记日志，不阻断流程。
            if (!resp.isSuccessful) {
                FileLogger.w(TAG, "通知 $method 返回 HTTP ${resp.code}")
            }
        }
    }

    override fun close() {
        sessionId = null
    }

    private fun buildRequest(bodyJson: String): Request {
        val headers = Headers.Builder().apply {
            add("Content-Type", "application/json")
            // 同时接受两种响应，让 server 自行决定单条 JSON 还是 SSE。
            add("Accept", "application/json, text/event-stream")
            sessionId?.let { add("Mcp-Session-Id", it) }
            extraHeaders.forEach { (k, v) -> add(k, v) }
        }.build()

        return Request.Builder()
            .url(endpoint)
            .headers(headers)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
    }

    /** 从 SSE 流里取第一条 `data:` 负载（可能跨多行），拼成完整 JSON 文本。 */
    private fun extractSseJson(reader: java.io.BufferedReader?): String? {
        reader ?: return null
        val data = StringBuilder()
        var line = reader.readLine()
        while (line != null) {
            when {
                line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                line.isBlank() && data.isNotEmpty() -> return data.toString()
            }
            line = reader.readLine()
        }
        return data.takeIf { it.isNotEmpty() }?.toString()
    }

    private fun parseAndValidate(rawJson: String, expectedId: Long, method: String): JsonRpcResponse {
        val response = runCatching {
            json.decodeFromString(JsonRpcResponse.serializer(), rawJson)
        }.getOrElse {
            throw McpException(message = "$method 响应 JSON 解析失败: ${it.message}", cause = it)
        }

        response.error?.let {
            throw McpException(rpcCode = it.code, message = "$method 返回错误 [${it.code}] ${it.message}")
        }
        if (response.id != null && response.id != expectedId) {
            FileLogger.w(TAG, "响应 id 不匹配: 期望 $expectedId, 实际 ${response.id}")
        }
        return response
    }
}
