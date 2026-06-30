package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.core.util.FileLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.pow

/**
 * 参考 opencode 的网络请求重试策略：指数退避，仅针对瞬时故障。
 */
private const val TAG = "RetryPolicy"

// 对应 opencode 的 attempts = 3（最多 2 次重试）→ 提升至 6 次重试以应对不稳定网络
const val MAX_NETWORK_RETRIES = 6

/**
 * 流式请求首字节等待超时：超过此时间未收到首个内容块即关闭流，触发可重试的 IOException。
 *
 * OkHttp 的 readTimeout（120s）是「相邻数据块之间」的等待上限，对慢启动/长思考留足空间；
 * 但首字节前若卡死，等待 120s 才超时体验过差，故用此应用层 watchdog 缩短到 60s。
 */
const val FIRST_BYTE_TIMEOUT_MS = 60_000L

/**
 * 启动首字节超时 watchdog（作为当前协程的子协程）：在 [FIRST_BYTE_TIMEOUT_MS] 后
 * 若 [isFirstByteReceived] 仍为 false，则调用 [close]（通常是关闭 ResponseBody），
 * 强制读取抛出 IOException 以被重试机制捕获。
 *
 * 调用方应在收到首个内容块后取消返回的 [Job]。
 */
suspend fun launchFirstByteWatchdog(
    close: () -> Unit,
    isFirstByteReceived: () -> Boolean
): Job = CoroutineScope(coroutineContext[Job]!!).launch {
    delay(FIRST_BYTE_TIMEOUT_MS)
    if (!isFirstByteReceived()) {
        runCatching { close() }
    }
}

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
 * 对应 opencode 的 isTransientError，并兼容 Android 的网络异常类型。
 *
 * 除传统的 IOException 判定外，还支持 HTTP 状态码感知：
 * - 429（速率限制）、408（请求超时）→ 可重试
 * - 5xx（500/502/503/504 等）→ 可重试（服务端瞬时故障）
 * - 其他 4xx → 不重试（客户端错误，重试无意义）
 */
fun isRetriableNetworkError(t: Throwable): Boolean {
    if (t is CancellationException) return false

    // 兼容原生网络异常
    if (t is SocketTimeoutException || t is InterruptedIOException ||
        t is java.net.UnknownHostException || t is java.net.ConnectException ||
        t is javax.net.ssl.SSLException || t is IOException) {
        return true
    }

    // HTTP 状态码感知：429/408/5xx 视为瞬时故障可重试
    if (t is HttpException) {
        val code = t.code()
        return code == 429 || code == 408 || code >= 500
    }

    val message = t.message?.lowercase() ?: t.toString().lowercase()
    return TRANSIENT_MESSAGES.any { message.contains(it) }
}

/**
 * 从 [HttpException] 的响应头中解析 `Retry-After`，返回等待毫秒数。
 *
 * `Retry-After` 有两种格式：
 * 1. 秒数（如 `"30"`）→ 直接转为毫秒
 * 2. HTTP 日期（如 `"Fri, 29 Jun 2026 10:00:00 GMT"`）→ 计算距当前时间的差值
 *
 * 非 HttpException 或无 `Retry-After` 头部 → 返回 null。
 * 解析失败也返回 null（降级到指数退避）。
 */
fun extractRetryAfterMillis(t: Throwable): Long? {
    if (t !is HttpException) return null
    val header = t.response()?.headers()?.get("Retry-After") ?: return null

    // 格式 1：纯秒数
    header.toLongOrNull()?.let { seconds ->
        return seconds * 1000L
    }

    // 格式 2：HTTP 日期（RFC 1123）
    return runCatching {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val retryTime = sdf.parse(header)?.time ?: return null
        val delay = retryTime - System.currentTimeMillis()
        if (delay > 0) delay else null
    }.getOrNull()
}

/**
 * 计算重试等待时间：优先使用服务端 `Retry-After` 头部指定的延迟，
 * 否则回退到指数退避 [exponentialDelayMillis]。
 *
 * 对 429（速率限制）尤其重要——尊重服务端要求，避免频繁重试加剧限制。
 */
fun retryDelayMillis(retryIndex: Int, error: Throwable): Long {
    val serverDelay = extractRetryAfterMillis(error)
    if (serverDelay != null && serverDelay > 0) {
        return min(serverDelay, MAX_RETRY_AFTER_MILLIS)
    }
    return exponentialDelayMillis(retryIndex)
}

/** Retry-After 头部值的上限，防止服务端返回过大的值导致无限等待。 */
private const val MAX_RETRY_AFTER_MILLIS = 60_000L

/**
 * 在指数退避下重试 [block]（保持原方法名），用于非流式请求。
 *
 * @param onRetry 重试前回调，参数为 (当前重试次数, 最大重试次数)；用于通知上层"正在重试"。
 *                置于 [block] 之前以保证 `retryStaircase { ... }` 的 trailing lambda 仍绑定到 [block]。
 */
suspend fun <T> retryStaircase(
    onRetry: (suspend (attempt: Int, maxRetries: Int) -> Unit)? = null,
    block: suspend () -> T
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            coroutineContext.ensureActive()
            if (attempt >= MAX_NETWORK_RETRIES || !isRetriableNetworkError(e)) throw e
            val wait = retryDelayMillis(attempt, e)
            FileLogger.w(TAG, "网络请求失败，第 ${attempt + 1}/$MAX_NETWORK_RETRIES 次重试（等待 ${wait}ms）: ${e.javaClass.simpleName} ${e.message}")
            onRetry?.invoke(attempt + 1, MAX_NETWORK_RETRIES)
            attempt++
            if (wait > 0) delay(wait)
        }
    }
}

/**
 * 流式请求的重试封装（保持原方法名）。
 *
 * @param onRetry 重试前回调，参数为 (当前重试次数, 最大重试次数)；用于通知上层"正在重试"。
 *                回调在 delay 之前调用，确保 UI 能立即展示重试状态。声明为 suspend 以便
 *                调用方在其中通过 Flow 的 emit() 推送重试事件。
 */
suspend fun streamWithStaircaseRetry(
    attemptOnce: suspend (onProduced: () -> Unit) -> Unit,
    onRetry: (suspend (attempt: Int, maxRetries: Int) -> Unit)? = null
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
            coroutineContext.ensureActive()
            if (produced || attempt >= MAX_NETWORK_RETRIES || !isRetriableNetworkError(e)) throw e
            val wait = retryDelayMillis(attempt, e)
            FileLogger.w(TAG, "流式请求在首字节前失败，第 ${attempt + 1}/$MAX_NETWORK_RETRIES 次重试（等待 ${wait}ms）: ${e.javaClass.simpleName} ${e.message}")
            onRetry?.invoke(attempt + 1, MAX_NETWORK_RETRIES)
            attempt++
            if (wait > 0) delay(wait)
        }
    }
}
