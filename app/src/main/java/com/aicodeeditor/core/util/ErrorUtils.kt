package com.aicodeeditor.core.util

import java.io.IOException

/**
 * 把异常转换成对用户有意义的提示文本。
 *
 * 很多异常（如 NPE、被取消的请求）的 [Throwable.message] 为 null，
 * 直接使用会显示成 "null"。这里回退到异常类名，并向上追溯 cause 链，
 * 尽量给出可定位问题的信息。
 */
fun Throwable.toUserMessage(): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 4) {
        val msg = current.message?.trim().orEmpty()
        val label = when {
            msg.isNotEmpty() -> msg
            else -> friendlyName(current)
        }
        if (parts.none { it == label }) parts.add(label)
        current = current.cause
        depth++
    }
    return parts.joinToString(" · ").ifBlank { "未知错误" }
}

/** 针对常见异常类型给出更友好的中文说明。 */
private fun friendlyName(t: Throwable): String = when (t) {
    is java.net.UnknownHostException -> "无法连接服务器（DNS 解析失败，请检查网络或 Base URL）"
    is java.net.SocketTimeoutException -> "请求超时（网络较慢或服务无响应）"
    is java.net.ConnectException -> "连接被拒绝（请检查 Base URL 与端口）"
    is javax.net.ssl.SSLException -> "SSL 连接失败"
    is IOException -> "网络读写失败：${t.javaClass.simpleName}"
    else -> t.javaClass.simpleName
}
