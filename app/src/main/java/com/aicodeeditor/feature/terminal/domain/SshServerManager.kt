package com.aicodeeditor.feature.terminal.domain

import android.content.Context
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.LinuxContainerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在本地 Alpine 容器内准备并运行 OpenSSH 的 sshd，让终端可以用「真实 SSH」登录进容器，
 * 从而获得真正的 PTY（vim/top/方向键/Ctrl-C/ANSI 等交互能力）。
 *
 * 设计要点：
 * - 服务端用 dropbear（在 proot 下比 openssh-sshd 更可靠），只监听回环 [HOST]:[PORT]，
 *   密码随机生成且仅存于 App 私有目录，外部无法访问。
 * - 容器内同时装了 openssh 的 ssh 客户端与 sshpass，终端进程通过 `sshpass -p … ssh …` 自动登录，
 *   既走密码认证又免去手动输入。
 * - dropbear 以单个长驻 proot 进程（`dropbear -F`）运行；其 stdout/stderr 被后台线程持续抽干，
 *   避免管道缓冲写满阻塞进程。
 *
 * 首次调用 [prepare] 需要联网（minirootfs 不含 SSH 组件，要 `apk add`）。成功后在容器内
 * 写下标记文件，后续启动走快路径。
 */
@Singleton
class SshServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val containerEngine: LinuxContainerEngine
) {
    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 8022
        const val USER = "root"

        private const val TAG = "SshServerManager"

        /**
         * 与 assets 里 alpine-rootfs 版本对应的 apk 分支，用于启用 community 仓库装 sshpass。
         * 固定 3.21：该版本用 apk-tools 2.14（libfetch + 同步 socket），在 proot 下可靠；
         * 3.24 的 apk-tools 3 自身网络实现与 proot 不兼容，会把下载误报成 "Permission denied"。
         */
        private const val ALPINE_BRANCH = "v3.21"

        /**
         * apk 镜像源；用阿里云国内镜像替代官方 dl-cdn，避免国外源过慢。
         *
         * 用 http 而非 https：Alpine minirootfs 不含 ca-certificates，容器内 apk 走自己的原生 TLS，
         * 无 CA 证书库会导致 HTTPS 握手失败，并被 apk 误报成 "Permission denied"（首次安装直接卡死）。
         * apk 对索引与每个 .apk 都用 /etc/apk/keys 的签名独立校验，故 http 传输仍保证完整性，
         * 这也是 proot/容器化 Alpine 引导阶段的通行做法。
         */
        private const val ALPINE_MIRROR = "http://mirrors.aliyun.com/alpine"
    }

    /** 串行化 prepare，避免并发重复安装/启动。 */
    private val mutex = Mutex()

    @Volatile private var daemon: Process? = null
    /** sshd 启动时绑定的工作区路径；工作区变化时需重启 sshd 以重新绑定 /workspace。 */
    @Volatile private var daemonWorkspace: String? = null

    /** 登录密码：随机生成一次后持久化复用。 */
    val password: String by lazy { loadOrCreatePassword() }

    /** 准备阶段的结果，供 UI 区分「成功」与各类失败原因。 */
    sealed interface Result {
        data object Ready : Result
        data class Failed(val message: String) : Result
    }

    /**
     * 确保 sshd 就绪并监听端口。幂等：可在每次进入终端时调用。
     * @param workspacePath 要绑定到容器 /workspace 的当前工作区目录。
     */
    suspend fun prepare(workspacePath: String): Result = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                containerEngine.ensureInstalled()
                if (!containerEngine.isContainerInstalled()) {
                    return@withContext Result.Failed("容器未安装（缺少 rootfs/proot）")
                }

                ensureSetup()
                startDaemonIfNeeded(workspacePath)

                if (awaitPort(timeoutMs = 10_000)) {
                    FileLogger.i(TAG, "sshd 就绪：$HOST:$PORT")
                    Result.Ready
                } else {
                    Result.Failed("sshd 启动后端口 $PORT 未就绪")
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "准备 sshd 失败", e)
                Result.Failed(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 构造在容器内自动登录本地 sshd 的命令字符串。
     * 该字符串会作为 `proot … /bin/sh -c "<此处>"` 的命令执行。
     * 远端命令 `cd /workspace; exec /bin/sh -l` 让登录后落在工作区并启动交互登录 shell。
     */
    fun buildConnectCommand(): String {
        val opts = listOf(
            "-tt",
            "-p", PORT.toString(),
            "-o", "StrictHostKeyChecking=no",
            "-o", "UserKnownHostsFile=/dev/null",
            "-o", "GlobalKnownHostsFile=/dev/null",
            "-o", "PreferredAuthentications=password",
            "-o", "LogLevel=ERROR"
        ).joinToString(" ")
        // 密码为纯字母数字（见 randomPassword），可安全置于单引号内。
        return "sshpass -p '$password' ssh $opts $USER@$HOST " +
            "'cd /workspace 2>/dev/null; exec /bin/sh -l'"
    }

    /** 应用退出/不再需要时回收 sshd 进程。 */
    fun stop() {
        runCatching { daemon?.destroy() }
        daemon = null
        daemonWorkspace = null
    }

    // ---- 内部实现 ----

    /** 容器内一次性安装与配置（幂等，靠校验 dropbear 二进制是否存在跳过）。 */
    private suspend fun ensureSetup() {
        val script = buildString {
            append("set -e\n")
            // 已装过则直接结束：直接校验 dropbear 二进制是否存在，
            // 比标记文件更可靠（rootfs 被删/损坏时会自动触发重装）。
            append("if command -v dropbear >/dev/null 2>&1; then echo SETUP_DONE; exit 0; fi\n")
            // DNS：用阿里云公共 DNS（国内解析更快/更稳；8.8.8.8 在部分网络被拦）。
            // 这里再写一次而非只靠安装期写入，是为了让已解压旧 rootfs 的设备在「重试」时也生效。
            append("mkdir -p /etc /etc/apk\n")
            append("cat > /etc/resolv.conf <<EOF\n")
            append("nameserver 223.5.5.5\n")
            append("nameserver 223.6.6.6\n")
            append("EOF\n")
            // 启用 community 仓库（sshpass 在 community），并刷新索引；用国内 http 镜像（见 ALPINE_MIRROR 注释）
            append("cat > /etc/apk/repositories <<EOF\n")
            append("$ALPINE_MIRROR/$ALPINE_BRANCH/main\n")
            append("$ALPINE_MIRROR/$ALPINE_BRANCH/community\n")
            append("EOF\n")
            // apk 默认缓存/临时目录在精简 rootfs 下可能不存在或不可写，写失败会被误报成
            // "Permission denied"。显式把缓存指向 /tmp 下新建的可写目录，并设 TMPDIR 兜底。
            append("export TMPDIR=/tmp\n")
            append("mkdir -p /tmp /var/tmp /run /tmp/apk-cache\n")
            append("apk --cache-dir /tmp/apk-cache update\n")
            // dropbear 作为 SSH 服务端：在 proot 下比 openssh-sshd 更可靠（不依赖 seccomp 特权分离沙箱）。
            // openssh-client 提供 ssh 客户端，sshpass 用于自动输入密码。复用上面的缓存目录，避免二次拉取。
            append("apk --cache-dir /tmp/apk-cache add dropbear openssh-client sshpass\n")
            append("mkdir -p /etc/dropbear /run /root/.ssh\n")
            append("chmod 700 /root/.ssh\n")
            // 设置 root 密码（busybox/shadow 的 chpasswd 均支持 user:pass 形式）
            append("echo 'root:$password' | chpasswd\n")
            append("echo SETUP_DONE\n")
        }

        // 首次需联网 apk update/add，耗时较长，给足上限超时，避免被默认超时打断。
        val output = containerEngine.runCommandSync(
            script,
            projectPath = null,
            timeoutMs = LinuxContainerEngine.MAX_TIMEOUT_MS
        )
        if (!output.contains("SETUP_DONE")) {
            throw IllegalStateException("SSH 环境安装失败（首次需联网）：${output.takeLast(400)}")
        }
        FileLogger.i(TAG, "SSH 容器环境就绪")
    }

    /** 启动（或在工作区变化/进程已死时重启）dropbear 长驻进程。 */
    private fun startDaemonIfNeeded(workspacePath: String) {
        val alive = daemon?.let { it.isAlive } ?: false
        if (alive && daemonWorkspace == workspacePath) return

        // 工作区变了或进程已死，先清理旧的
        runCatching { daemon?.destroy() }

        // -F 前台运行，-E 日志到 stderr（已与 stdout 合并），-R 按需生成主机密钥，
        // -p 仅绑定回环端口。默认允许 root 密码登录（未传 -w/-s）。
        val process = containerEngine.startProotProcess(
            "/usr/sbin/dropbear -F -E -R -p $HOST:$PORT",
            workspacePath
        )
        daemon = process
        daemonWorkspace = workspacePath
        drainAsync(process)
        FileLogger.i(TAG, "已启动 dropbear（workspace=$workspacePath）")
    }

    /** 后台抽干 sshd 输出，避免管道写满阻塞，并把日志落盘便于排查。 */
    private fun drainAsync(process: Process) {
        Thread {
            runCatching {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        FileLogger.d(TAG, "[dropbear] $line")
                    }
                }
            }
        }.apply { isDaemon = true; name = "dropbear-drain" }.start()
    }

    /** 轮询回环端口直到可建立 TCP 连接（sshd 在容器内 bind 的是真实回环）。 */
    private suspend fun awaitPort(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (daemon?.isAlive == false) {
                FileLogger.w(TAG, "sshd 进程已退出，停止等待端口")
                return false
            }
            val ok = runCatching {
                Socket().use { it.connect(InetSocketAddress(HOST, PORT), 500); true }
            }.getOrDefault(false)
            if (ok) return true
            delay(250)
        }
        return false
    }

    private fun loadOrCreatePassword(): String {
        val file = File(context.filesDir, "ssh/password")
        file.parentFile?.mkdirs()
        if (file.exists()) {
            val saved = file.readText().trim()
            if (saved.isNotEmpty()) return saved
        }
        val pw = randomPassword()
        file.writeText(pw)
        return pw
    }

    /** 纯字母数字密码：可安全嵌入单引号命令，且避开 shell 特殊字符。 */
    private fun randomPassword(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        return buildString { repeat(20) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
    }
}
