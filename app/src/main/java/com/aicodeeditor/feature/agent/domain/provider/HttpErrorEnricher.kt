package com.aicodeeditor.feature.agent.domain.provider

import retrofit2.HttpException

/**
 * 把上游返回的 HTTP 错误转换成带**响应体**的异常，便于用户定位 4xx/5xx 的真实原因。
 *
 * Retrofit 的 [HttpException] 默认 message 只有 `"HTTP 400 Bad Request"` 这类状态码描述，
 * 服务端写在响应体里的具体原因（如 `model not found`、`invalid api key`、`Invalid value for tools`）
 * 不会被读出，用户只看到一句没用的状态码。这里把 errorBody 读出来拼到 message 里，
 * 原异常作为 cause 保留。
 *
 * 注意：errorBody 只能读一次，调用后即被消费。
 */
fun Throwable.enrichWithHttpErrorBody(): Throwable {
    if (this !is HttpException) return this
    val raw = this.response()?.errorBody()?.string()?.trim()
    val body = runCatching {
        if (raw.isNullOrBlank()) return@runCatching ""
        // 常见格式：{"error":{"message":"...","type":"..."}} 或 {"message":"..."}；尽量抽出可读文本。
        val obj = com.google.gson.JsonParser.parseString(raw).asJsonObject
        obj.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.asString
            ?: obj.get("message")?.asString
            ?: raw
    }.getOrNull().orEmpty()
    val detail = if (body.isBlank()) raw?.take(500).orEmpty() else body
    val composed = if (detail.isBlank()) {
        this.message() ?: "HTTP ${this.code()}"
    } else {
        "HTTP ${this.code()}: $detail"
    }
    return IllegalStateException(composed, this)
}
