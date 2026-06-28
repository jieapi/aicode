package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.core.util.FileLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.math.min
import kotlin.math.pow

/**
 * 参考 opencode 的网络请求重试策略：指数退避，仅针对瞬时故障。
 */
private const val TAG = "RetryPolicy"

// 对应 opencode 的 attempts = 3（最多 2 次重试）
const val MAX_NETWORK_RETRIES = 2

private val TRANSIENT_MESSAGES = listOf(
    "load failed",
    "network connection was lost",
    "network request failed",
    "failed to fetch",
    "econnreset",
    "econnrefused",
    "etimedout",
    "socket hang up"
)

/**
 * 对应 opencode 的 delay=500, factor=2, maxDelay=10000
 */
fun exponentialDelayMillis(retryIndex: Int): Long {
    val delay = 500L
    val factor = 2.0
    val maxDelay = 10000L
    val wait = (delay * factor.pow(retryIndex)).toLong()
    return min(wait, maxDelay)
}

/**
 * 对应 opencode 的 isTransientError，并兼容 Android 的网络异常类型
 */
fun isRetriableNetworkError(t: Throwable): Boolean {
    if (t is CancellationException) return false

    // 兼容原生网络异常
    if (t is SocketTimeoutException || t is InterruptedIOException || 
        t is java.net.UnknownHostException || t is java.net.ConnectException || 
        t is javax.net.ssl.SSLException || t is IOException) {
        return true
    }

    val message = t.message?.lowercase() ?: t.toString().lowercase()
    return TRANSIENT_MESSAGES.any { message.contains(it) }
}

/**
 * 在指数退避下重试 [block]（保持原方法名），用于非流式请求。
 */
suspend fun <T> retryStaircase(block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (attempt >= MAX_NETWORK_RETRIES || !isRetriableNetworkError(e)) throw e
            val wait = exponentialDelayMillis(attempt)
            FileLogger.w(TAG, "网络请求失败，第 ${attempt + 1}/$MAX_NETWORK_RETRIES 次重试（等待 ${wait}ms）: ${e.javaClass.simpleName} ${e.message}")
            attempt++
            if (wait > 0) delay(wait)
        }
    }
}

/**
 * 流式请求的重试封装（保持原方法名）。
 */
suspend fun streamWithStaircaseRetry(
    attemptOnce: suspend (onProduced: () -> Unit) -> Unit
) {
    var attempt = 0
    while (true) {
        var produced = false
        try {
            attemptOnce { produced = true }
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (produced || attempt >= MAX_NETWORK_RETRIES || !isRetriableNetworkError(e)) throw e
            val wait = exponentialDelayMillis(attempt)
            FileLogger.w(TAG, "流式请求在首字节前失败，第 ${attempt + 1}/$MAX_NETWORK_RETRIES 次重试（等待 ${wait}ms）: ${e.javaClass.simpleName} ${e.message}")
            attempt++
            if (wait > 0) delay(wait)
        }
    }
}
