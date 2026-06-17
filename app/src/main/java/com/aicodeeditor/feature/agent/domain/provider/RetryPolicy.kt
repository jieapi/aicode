package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.core.util.FileLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * 网络请求的阶梯重试策略：超时/瞬时网络故障时最多重试 [MAX_NETWORK_RETRIES] 次。
 * 退避按「尝试序号」线性递增：第 1 次重试立即（0s），随后 1s、2s、3s、4s……
 *
 * 即首次失败后立刻重试，再失败等 1s，再失败等 2s，以此类推，共 5 次重试。
 */
private const val TAG = "RetryPolicy"

const val MAX_NETWORK_RETRIES = 5

/**
 * 第 [retryIndex] 次重试前的等待毫秒数（retryIndex 从 0 开始）：
 * 0 → 0ms（立即），1 → 1000ms，2 → 2000ms，3 → 3000ms，4 → 4000ms。
 */
fun staircaseDelayMillis(retryIndex: Int): Long = retryIndex.coerceAtLeast(0) * 1000L

/**
 * 是否为「值得重试」的瞬时网络错误（超时、连接中断、读写失败等）。
 * 业务错误（如 HTTP 4xx，由上层抛出的 IllegalState 等）不在此列，不应重试。
 * [CancellationException] 永远不算，交由调用方在外层先行重新抛出。
 */
fun isRetriableNetworkError(t: Throwable): Boolean = when (t) {
    is CancellationException -> false
    is SocketTimeoutException -> true
    is InterruptedIOException -> true // 含 OkHttp 的 "timeout" 包装
    is java.net.UnknownHostException -> true
    is java.net.ConnectException -> true
    is javax.net.ssl.SSLException -> true
    is IOException -> true // 其余网络读写类故障
    else -> false
}

/**
 * 在阶梯退避下重试 [block]，用于**非流式**的一次性请求。
 * 命中 [isRetriableNetworkError] 才重试；取消优先向上传播；
 * 超过 [MAX_NETWORK_RETRIES] 次后抛出最后一次的异常。
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
            val wait = staircaseDelayMillis(attempt)
            FileLogger.w(TAG, "网络请求失败，第 ${attempt + 1}/$MAX_NETWORK_RETRIES 次重试（等待 ${wait}ms）: ${e.javaClass.simpleName} ${e.message}")
            attempt++
            if (wait > 0) delay(wait)
        }
    }
}

/**
 * 流式请求的阶梯重试封装：仅在**尚未向下游产出任何分块前**失败才重试
 * （即连接/首字节超时这类场景）；一旦开始吐字再失败则向上抛出，避免重发已显示的文本。
 *
 * [attemptOnce] 接收一个 `onProduced` 回调——实现里**在第一次真正向下游发射数据时**调用它，
 * 以标记「已产出」。同一次 [streamWithStaircaseRetry] 调用内只要产出过一次，后续失败便不再重试。
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
            // 已经吐过字 / 已无重试额度 / 非瞬时网络错误：直接上抛。
            if (produced || attempt >= MAX_NETWORK_RETRIES || !isRetriableNetworkError(e)) throw e
            val wait = staircaseDelayMillis(attempt)
            FileLogger.w(TAG, "流式请求在首字节前失败，第 ${attempt + 1}/$MAX_NETWORK_RETRIES 次重试（等待 ${wait}ms）: ${e.javaClass.simpleName} ${e.message}")
            attempt++
            if (wait > 0) delay(wait)
        }
    }
}
