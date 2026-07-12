package com.aicode.feature.agent.domain.mcp

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.LinuxContainerEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.BufferedWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * MCP「stdio」传输实现：把 MCP server 作为容器内的长驻子进程跑起来，
 * 往其 **stdin** 写一行 JSON-RPC、从其 **stdout** 读一行 JSON-RPC（NDJSON），
 * **stderr** 仅作为 server 日志单独消费、不污染协议流。
 *
 * 进程由 [LinuxContainerEngine.startStdioProcess] 在 proot 容器内拉起（如 `npx -y some-server`），
 * 首次 [request]/[notify] 时惰性启动（[startMutex] 串行化）。stdout 读循环把每条带 id 的响应路由到
 * 对应的 pending [CompletableDeferred]；server 主动发来的请求/通知（带 method）当前不支持，记录后忽略。
 *
 * 与 [StreamableHttpTransport] 对等：协议错误以 [McpException] 抛出，由 [McpClient]/[McpManager]
 * 按 server 粒度兜底，单个 server 失败不影响其它 server 与整个 Agent。
 */
class StdioTransport(
    private val serverName: String,
    private val engine: LinuxContainerEngine,
    private val program: String,
    private val programArgs: List<String>,
    private val projectPath: String?,
    private val extraEnv: Map<String, String> = emptyMap(),
    private val json: Json = DEFAULT_JSON
) : McpTransport {

    private companion object {
        const val TAG = "McpStdioTransport"

        /** 单次请求等待响应的上限：首跑 `npx -y` 可能要联网下载，给足时间。 */
        const val REQUEST_TIMEOUT_MS = 120_000L

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    private val writeMutex = Mutex()
    private val idCounter = AtomicLong(0)

    /** 已发出、等待响应的请求：id → 待完成的响应。 */
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonRpcResponse>>()

    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var closed = false

    override suspend fun request(method: String, params: JsonObject?): JsonRpcResponse {
        ensureStarted()
        val id = idCounter.incrementAndGet()
        val payload = JsonRpcRequest(id = id, method = method, params = params)
        val deferred = CompletableDeferred<JsonRpcResponse>()
        pending[id] = deferred
        FileLogger.d(TAG, "→ [$serverName] $method id=$id")

        try {
            writeLine(json.encodeToString(JsonRpcRequest.serializer(), payload))
        } catch (e: Exception) {
            pending.remove(id)
            throw McpException(message = "[$serverName] 写入 stdin 失败: ${e.message}", cause = e)
        }

        val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { deferred.await() }
        if (response == null) {
            pending.remove(id)
            throw McpException(message = "[$serverName] 请求 $method 超时（${REQUEST_TIMEOUT_MS}ms）")
        }
        response.error?.let {
            throw McpException(rpcCode = it.code, message = "$method 返回错误 [${it.code}] ${it.message}")
        }
        return response
    }

    override suspend fun notify(method: String, params: JsonObject?) {
        ensureStarted()
        val payload = JsonRpcNotification(method = method, params = params)
        FileLogger.d(TAG, "→ notify [$serverName] $method")
        runCatching { writeLine(json.encodeToString(JsonRpcNotification.serializer(), payload)) }
            .onFailure { FileLogger.w(TAG, "[$serverName] 发送通知 $method 失败: ${it.message}") }
    }

    override fun close() {
        if (closed) return
        closed = true
        FileLogger.i(TAG, "[$serverName] 关闭 stdio 传输")
        scope.cancel()
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
        writer = null
        process = null
        failAllPending("transport 已关闭")
    }

    /** 惰性启动子进程并挂上 stdout 读循环 / stderr 排空。串行化避免并发重复启动。 */
    private suspend fun ensureStarted() = startMutex.withLock {
        if (process != null) return@withLock
        if (closed) throw McpException(message = "[$serverName] transport 已关闭")

        FileLogger.i(TAG, "[$serverName] 启动 stdio server: $program ${programArgs.joinToString(" ")}")
        val p = try {
            engine.startStdioProcess(program, programArgs, projectPath, extraEnv)
        } catch (e: Exception) {
            throw McpException(message = "[$serverName] 启动子进程失败: ${e.message}", cause = e)
        }
        process = p
        writer = p.outputStream.bufferedWriter()
        scope.launch { readLoop(p) }
        scope.launch { drainStderr(p) }
    }

    /** 同步化写入一行（多个并发请求共享一条 stdin）。 */
    private suspend fun writeLine(line: String) = writeMutex.withLock {
        val w = writer ?: throw McpException(message = "[$serverName] transport 未启动")
        withContext(Dispatchers.IO) {
            w.write(line)
            w.write("\n")
            w.flush()
        }
    }

    /** 逐行读 stdout，把每条响应按 id 路由到对应 pending；非响应（带 method 或不可解析）忽略。 */
    private fun readLoop(p: Process) {
        val reader = p.inputStream.bufferedReader()
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                val obj = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull()
                if (obj == null) {
                    FileLogger.d(TAG, "[$serverName] 跳过非 JSON 行: ${line.take(200)}")
                    continue
                }
                // 带 method 的是 server→client 的请求/通知（如 sampling/roots），当前不支持，忽略。
                if (obj.containsKey("method")) {
                    FileLogger.d(TAG, "[$serverName] 忽略 server 主动消息: ${obj["method"]}")
                    continue
                }

                val resp = runCatching { json.decodeFromJsonElement(JsonRpcResponse.serializer(), obj) }.getOrNull()
                if (resp?.id == null) {
                    FileLogger.d(TAG, "[$serverName] 跳过无 id 响应")
                    continue
                }
                val deferred = pending.remove(resp.id)
                if (deferred == null) {
                    FileLogger.w(TAG, "[$serverName] 收到无匹配请求的响应 id=${resp.id}")
                } else {
                    deferred.complete(resp)
                }
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "[$serverName] stdout 读循环异常结束: ${e.message}")
        } finally {
            FileLogger.i(TAG, "[$serverName] stdout 已结束（进程可能已退出）")
            failAllPending("server stdout 已关闭")
        }
    }

    /** server 的诊断日志写在 stderr，单独排空并降级记录，避免阻塞进程缓冲区。 */
    private fun drainStderr(p: Process) {
        val reader = p.errorStream.bufferedReader()
        runCatching {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank()) FileLogger.d(TAG, "[$serverName] stderr: $line")
            }
        }
    }

    /** 让所有在途请求以异常收尾（进程退出 / 关闭时调用），避免调用方永久挂起。 */
    private fun failAllPending(reason: String) {
        pending.keys.toList().forEach { id ->
            pending.remove(id)?.completeExceptionally(McpException(message = "[$serverName] $reason"))
        }
    }
}
