package com.aicode.feature.credentials.data

import android.content.Context
import android.os.FileObserver
import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.LinuxContainerEngine
import com.aicode.feature.credentials.domain.model.GitCredential
import com.aicode.feature.credentials.domain.model.newCredentialId
import com.aicode.feature.credentials.domain.repository.CredentialRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自定义 git credential helper（容器内 /root/.aicode/git-credential-aicode）与 app 之间的文件 IPC 桥。
 *
 * 容器内 git 缺凭据时 helper 写 `cred-req-<id>` 到 /root/.aicode（经 PRoot -b 绑定即宿主 filesDir/aicode 同一
 * inode）。本桥用 [FileObserver] 监听该宿主目录捕获请求 → 暴露 [request] StateFlow 供全局 Compose 弹窗 →
 * 用户回填后 [respond] 写 `cred-resp-<id>` 明文 KV，helper 轮询取走喂回 git，git 自动续跑。三端（UI / AI Bash /
 * 交互终端）git 缺凭据都走这条统一链，无需逐命令路径适配。
 *
 * 临时文件清理：请求文件由 helper 在拿到响应后自删（helper 知道自己何时结束），响应文件也由 helper 删它读到的；
 * app 只写响应、不删它没写的文件，避免与 helper 抢删。详见 helper 脚本内 rm 逻辑。
 *
 * 在途计数：收到请求 [LinuxContainerEngine.incPromptInFlight]、写回响应 [decPromptInFlight]，配合
 * [LinuxContainerEngine.launchKillWatchdog] 在 helper 在途时暂停超时杀进程——用户离开几分钟填凭据，git 命令
 * 不会被 120s watchdog 强杀。
 *
 * FileObserver 跨 PRoot -b 对 app filesDir 基本可靠，但个别机型 inotify 在绑定目录可能失效，故另起一个低频
 * 兜底轮询协程（[fallbackPollLoop]）扫 `cred-req-*` 未处理项；两者按 requestId 去重防双处理。
 *
 * 生命周期：@Singleton = App 级，借 [com.aicode.AIEditorApp] 主线程调 [start]。FileObserver 必须主线程创建与
 * startWatching。弹窗在任意页面（终端页/聊天页/Git 页）都能弹，不限于 Git 页。
 */
@Singleton
class CredentialRequestBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val credentialRepository: CredentialRepository,
    private val fileSync: GitCredentialsFileSync,
    private val containerEngine: LinuxContainerEngine
) {
    private companion object {
        const val TAG = "CredentialRequestBridge"
        const val REQ_PREFIX = "cred-req-"
        const val RESP_PREFIX = "cred-resp-"
        /** 兜底轮询间隔：FileObserver 不灵时也能在 ~1s 内捕获请求。 */
        const val FALLBACK_POLL_MS = 1000L
        /** seen 去重集容量上限，超过淘汰最旧。 */
        const val SEEN_CAP = 64
    }

    /** 当前待弹凭据请求（host 来自 git credential 协议的 host 字段）。null 表示无待处理。 */
    data class CredentialRequest(
        val requestId: String,
        val host: String
    )

    private val _request = MutableStateFlow<CredentialRequest?>(null)
    val request: StateFlow<CredentialRequest?> = _request.asStateFlow()

    /** 已处理/在途的 requestId 去重集，防 FileObserver 与兜底轮询双触发同一条。 */
    private val seen = LinkedHashSetWithCap(SEEN_CAP)

    private val seenMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var observer: FileObserver? = null
    private var aicodeDir: File? = null

    /**
     * 主线程启动：建 [FileObserver] 监听 aicodeDir，并起兜底轮询协程。
     * 必须在主线程调用（FileObserver 绑定主 Looper）。幂等可重复调。
     */
    fun start() {
        val dir = File(context.filesDir, "aicode").apply { mkdirs() }
        aicodeDir = dir
        if (observer == null) {
            val obs = object : FileObserver(dir, FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    val name = path ?: return
                    if (!name.startsWith(REQ_PREFIX) || name.endsWith(".tmp")) return
                    scope.launch { handleRequest(File(dir, name), name) }
                }
            }
            obs.startWatching()
            observer = obs
            FileLogger.i(TAG, "FileObserver 启动，监听 ${dir.absolutePath}")
        }
        // 兜底轮询：个别机型 -b 目录 inotify 失效时仍能取到请求；并清理上次会话残留。
        scope.launch { cleanupStale(dir); fallbackPollLoop(dir) }
    }

    /** 兜底轮询：扫未在 seen 里的 cred-req-* 文件，补 FileObserver 漏报。App 常驻，永不退出。 */
    private suspend fun fallbackPollLoop(dir: File) {
        while (true) {
            delay(FALLBACK_POLL_MS)
            runCatching {
                dir.listFiles { f -> f.name.startsWith(REQ_PREFIX) && !f.name.endsWith(".tmp") }
                    ?.forEach { f -> handleRequest(f, f.name) }
            }
        }
    }

    /** 启动时清理上次会话残留的 cred-req/cred-resp，避免误触发弹窗。 */
    private fun cleanupStale(dir: File) {
        runCatching {
            dir.listFiles { f -> f.name.startsWith(REQ_PREFIX) || f.name.startsWith(RESP_PREFIX) }
                ?.forEach { it.delete() }
        }
    }

    /** 解析请求文件 → 置 [request]，并 inc 在途。FileObserver 与轮询都走这里，靠 seen 去重。 */
    private suspend fun handleRequest(file: File, name: String) {
        val requestId = name.removePrefix(REQ_PREFIX)
        val fresh = seenMutex.withLock { seen.addCapped(requestId) }
        if (!fresh) return   // 已处理过，跳过
        // 文件已被 helper 删了或为空（启动残留已 cleanup，正常不该到这里），跳过。
        if (!file.exists() || file.length() == 0L) return
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val host = text.lineSequence()
            .firstOrNull { it.startsWith("host=") }
            ?.removePrefix("host=")
            ?.trim()
            ?.lowercase()
            ?: ""
        if (host.isBlank()) return
        containerEngine.incPromptInFlight()
        FileLogger.i(TAG, "收到凭据请求 host=$host id=$requestId")
        _request.value = CredentialRequest(requestId, host)
    }

    /**
     * 用户填完凭据确认：写响应明文 KV + 存 Room（默认条）+ syncAll 落 store（后续免填）+ dec 在途 + 清 [request]。
     * [host] 取自 [CredentialRequest]（Git 进程在收到 resp 前不会删 req，但 host 已在请求里，直接传更稳）。
     */
    fun respond(requestId: String, host: String, username: String, token: String) {
        val dir = aicodeDir ?: File(context.filesDir, "aicode")
        scope.launch {
            writeRespAtomically(dir, requestId, "username=$username\npassword=$token\n")
            runCatching {
                val cred = GitCredential(
                    id = newCredentialId(),
                    host = host.trim().lowercase(),
                    username = username.trim(),
                    token = token,
                    isDefault = true
                )
                credentialRepository.save(cred)
                fileSync.syncAll()
            }.onFailure { FileLogger.e(TAG, "保存回填凭据失败", it) }
            containerEngine.decPromptInFlight()
            clearIfCurrent(requestId)
            FileLogger.i(TAG, "已回填凭据 id=$requestId host=$host")
        }
    }

    /** 用户取消弹窗：写 cancel 响应（helper 退出非零让 git 报认证失败）+ dec 在途 + 清状态。 */
    fun cancel(requestId: String) {
        val dir = aicodeDir ?: File(context.filesDir, "aicode")
        scope.launch {
            writeRespAtomically(dir, requestId, "cancel=1\n")
            containerEngine.decPromptInFlight()
            clearIfCurrent(requestId)
            FileLogger.i(TAG, "用户取消凭据弹窗 id=$requestId")
        }
    }

    private fun clearIfCurrent(requestId: String) {
        if (_request.value?.requestId == requestId) _request.value = null
    }

    /** 先写 .tmp 再 rename 原子写，避免 helper 轮询读到半截文件。仿 [GitCredentialsFileSync] 的写法。 */
    private fun writeRespAtomically(dir: File, requestId: String, content: String) {
        val target = File(dir, RESP_PREFIX + requestId)
        val tmp = File(dir, "$RESP_PREFIX${requestId}.tmp")
        runCatching {
            tmp.writeText(content)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        }.onFailure { FileLogger.e(TAG, "写凭据响应文件失败", it) }
    }
}

/** 带容量上限的 [LinkedHashSet]，超过 [cap] 自动淘汰最旧条目。供 [CredentialRequestBridge.seen] 去重。 */
private class LinkedHashSetWithCap(private val cap: Int) : LinkedHashSet<String>() {
    fun addCapped(id: String): Boolean {
        if (contains(id)) return false
        add(id)
        if (size > cap) {
            iterator().next().let { remove(it) }
        }
        return true
    }
}
