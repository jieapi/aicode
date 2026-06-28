package com.aicodeeditor.feature.agent.domain.container

import com.aicodeeditor.core.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** 流式执行命令时产生的事件，供终端逐行实时渲染。 */
sealed interface CommandEvent {
    /** 一行标准输出/错误（stderr 已合并到 stdout）。 */
    data class Line(val text: String) : CommandEvent
    /** 命令结束，附退出码（兜底 shell 无法取到时为 null）。 */
    data class Exit(val code: Int?) : CommandEvent
}

/**
 * 一次 PRoot 调用的完整描述：可执行文件 + 参数列表 + 环境变量。
 *
 * [argv] 的第 0 个元素即 proot 二进制路径，其余为参数。
 *
 * 两种消费方都要拿到「完整 argv」：
 *  - [ProcessBuilder] 直接接收 argv 列表；
 *  - Termux TerminalSession 的 cmd 仅用于 execvp 查找可执行文件，真正的 argv 由它的
 *    args 参数原样构成（native execvp(cmd, argv)，不会自动补 argv[0]），故 args 必须
 *    也是「含 argv[0]=proot 二进制」的完整 argv，否则选项整体错位一位、proot 会把
 *    rootfs 路径误当客户机程序（"is not a regular file"）。[executable] 只用于前者的 cmd 槽位。
 */
data class ProotInvocation(
    val argv: List<String>,
    val env: Map<String, String>
) {
    val executable: String get() = argv.first()

    /**
     * 供 Termux [com.termux.terminal.TerminalSession] 使用的完整环境数组（"KEY=VALUE"）。
     *
     * [ProcessBuilder] 的 environment() 初始即为父进程（App）环境的副本，再叠加 [env]，
     * 因此 proot 能拿到 ANDROID_ROOT/ANDROID_DATA/LD_LIBRARY_PATH 等系统变量；但
     * TerminalSession 接收的是「完整、不继承父进程」的环境数组，只喂 [env] 会让 proot
     * 这个动态链接的 Android 可执行文件因缺系统环境而 exec 失败、瞬间退出（终端表现为
     * 「会话已结束」而日志无其他报错）。故在此显式合并父进程环境，复刻 ProcessBuilder 语义。
     */
    val ptyEnvArray: Array<String>
        get() = (System.getenv() + env).map { "${it.key}=${it.value}" }.toTypedArray()
}

@Singleton
class LinuxContainerEngine @Inject constructor(
    private val containerInstaller: ContainerInstaller
) {
    /** 容器初始化的实时进度，供所有入口（终端页/AI/后台终端/MCP）共享同一份状态。 */
    private val _initProgress = MutableStateFlow<ContainerInitState>(ContainerInitState.Idle)
    val initProgress: StateFlow<ContainerInitState> = _initProgress.asStateFlow()

    /** 串行化 ensureInstalled，避免多入口并发触发重复解压/配置；后到者等待后看到就绪直接置 Ready。 */
    private val initMutex = Mutex()

    companion object {
        private const val TAG = "LinuxContainerEngine"

        /** 命令默认超时（毫秒）：未显式指定时套用，避免命令卡死时永久占用会话。 */
        const val DEFAULT_TIMEOUT_MS = 120_000L

        /** 命令超时上限（毫秒）：再大的请求也会被钳到此值，防止事实上的“无限等待”。 */
        const val MAX_TIMEOUT_MS = 1_800_000L

        /** 超时后给进程的优雅退出宽限（毫秒），过后强杀。 */
        private const val TIMEOUT_KILL_GRACE_MS = 200L

        /**
         * 容器首次初始化时自动安装的基础包清单（Alpine 3.21 的 `python3` 即 3.12.x）。
         * 包含：python3、git、pip（py3-pip）、nodejs、npm。用 `--no-cache` 避免 apk 缓存撑大 rootfs。
         * 改清单时同步 +1 [PROVISION_VERSION] 触发重装。
         */
        private const val PROVISION_PACKAGES = "python3 git py3-pip nodejs npm"

        /**
         * 基础包配置版本。改 [PROVISION_PACKAGES] 或配置逻辑时 +1，触发在设备上重新 `apk add`。
         * 独立于 [ContainerInstaller] 的 rootfs INSTALL_VERSION：rootfs 版本升级会删 rootfs
         * （连带清掉本标记），故新 rootfs 必然重跑配置；同 rootfs 下改包清单则靠本版本号触发。
         */
        private const val PROVISION_VERSION = "py3.12-pip-node-v1"

        /** `apk add` 下载基础包的超时（毫秒）：首次配置需联网拉包，给足时间。 */
        private const val PROVISION_TIMEOUT_MS = 600_000L
    }

    /** 标记基础包（python3/git）已按 [PROVISION_VERSION] 配置完成，内容为版本号。 */
    private val provisionMarker: java.io.File
        get() = java.io.File(containerInstaller.rootfsDir, ".provisioned")

    /**
     * 在容器内流式执行命令：每读到一行就 emit 一个 [CommandEvent.Line]，命令结束 emit
     * [CommandEvent.Exit]。首次调用会触发 rootfs 安装（幂等）。
     *
     * 与 [runCommandSync] 共用同一进程构建逻辑，区别仅在于输出按行实时下发，
     * 让终端能看到执行「过程」而非只有最终结果。
     *
     * [timeoutMs] 为命令最长执行时间（毫秒），默认 [DEFAULT_TIMEOUT_MS]，上限 [MAX_TIMEOUT_MS]。
     * 超时后强制终止子进程并在末尾追加一行超时提示，[CommandEvent.Exit] 退出码记为 null。
     * 由于 readLine 是阻塞读，单靠协程超时无法打断，这里用独立看门狗 destroy 进程来解除阻塞。
     */
    fun runCommandStream(
        command: String,
        projectPath: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Flow<CommandEvent> = flow {
        // 懒安装：首次执行命令时解压 rootfs/proot 并配置基础包（python3/git/pip/node/npm）
        ensureInstalled()
        emitAll(streamExecNoInstall(command, projectPath, timeoutMs))
    }.flowOn(Dispatchers.IO)

    /**
     * 在容器内流式执行命令的「裸」实现：每读到一行就 emit [CommandEvent.Line]，命令结束 emit
     * [CommandEvent.Exit]。**不触发懒安装**（不调 [ensureInstalled]），假定 rootfs 已就绪。
     *
     * 抽出此方法供 [runCommandStream]（先 [ensureInstalled]）与 [provisionIfNeeded]（先
     * [ContainerInstaller.installRootfsIfNeed]，不能再触发 ensureInstalled 否则递归）共用，
     * 让 provision 也能逐行拿到 apk 输出以更新进度。
     *
     * [timeoutMs] 为命令最长执行时间（毫秒），默认 [DEFAULT_TIMEOUT_MS]，上限 [MAX_TIMEOUT_MS]。
     * 超时后强制终止子进程并在末尾追加一行超时提示，[CommandEvent.Exit] 退出码记为 null。
     * 由于 readLine 是阻塞读，单靠协程超时无法打断，这里用独立看门狗 destroy 进程来解除阻塞。
     */
    private fun streamExecNoInstall(
        command: String,
        projectPath: String?,
        timeoutMs: Long
    ): Flow<CommandEvent> = flow {
        val effectiveTimeout = timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS)
        FileLogger.d(TAG, "执行命令(流式) cwd=$projectPath timeout=${effectiveTimeout}ms: $command")
        val process = startContainerProcess(command, projectPath)
        val timedOut = AtomicBoolean(false)
        // 看门狗跑在独立 scope（独立 Job）上：若放进包裹 emit 的 coroutineScope 里，emit 的
        // Job 与 flow 收集者不一致会触发「Flow invariant is violated」。这里仅用它在超时时杀进程。
        val watchScope = CoroutineScope(Dispatchers.IO + Job())
        val watchdog = launchKillWatchdog(watchScope, process, effectiveTimeout, timedOut, command)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(CommandEvent.Line(line!!))
            }
            val exitCode = process.waitFor()
            watchdog.cancel()
            if (timedOut.get()) {
                FileLogger.w(TAG, "命令超时(${effectiveTimeout}ms)已终止: $command")
                emit(CommandEvent.Line(timeoutNotice(effectiveTimeout)))
                emit(CommandEvent.Exit(null))
            } else {
                if (exitCode != 0) FileLogger.w(TAG, "命令退出码=$exitCode: $command")
                else FileLogger.v(TAG, "命令完成(退出码 0): $command")
                emit(CommandEvent.Exit(exitCode))
            }
        } finally {
            // 协程取消（用户离开页面等）时确保子进程被回收，避免泄漏
            watchdog.cancel()
            watchScope.cancel()
            runCatching { reader.close() }
            runCatching { process.destroy() }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 在容器内同步执行命令并返回输出。首次调用会触发 rootfs 安装（幂等）。
     *
     * [timeoutMs] 为命令最长执行时间（毫秒），默认 [DEFAULT_TIMEOUT_MS]，上限 [MAX_TIMEOUT_MS]。
     * 超时后强制终止子进程，返回已收集到的部分输出并在末尾追加超时提示。
     */
    suspend fun runCommandSync(
        command: String,
        projectPath: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): String = withContext(Dispatchers.IO) {
        // 懒安装：首次执行命令时解压 rootfs/proot 并配置基础包（python3/git）
        ensureInstalled()
        execCaptured(command, projectPath, timeoutMs).output
    }

    /** 一次容器内执行的结果：限幅后的完整输出 + 退出码（超时/异常时为 null）。 */
    private data class ExecResult(val output: String, val exitCode: Int?)

    /**
     * 在容器内同步执行命令并捕获输出。**假定 rootfs 已安装**（不做懒安装/配置），
     * 供 [runCommandSync]（先 [ensureInstalled]）与 [provisionIfNeeded]（先 [installRootfsIfNeed]）复用，
     * 避免配置流程反向触发 [ensureInstalled] 形成递归。
     *
     * [timeoutMs] 为命令最长执行时间（毫秒），钳到 [MAX_TIMEOUT_MS]。超时则强杀进程，
     * 返回已收集的部分输出并在末尾追加超时提示，[ExecResult.exitCode] 记为 null。
     */
    private suspend fun execCaptured(
        command: String,
        projectPath: String?,
        timeoutMs: Long
    ): ExecResult = withContext(Dispatchers.IO) {
        try {
            val effectiveTimeout = timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS)
            FileLogger.d(TAG, "执行命令(同步) cwd=$projectPath timeout=${effectiveTimeout}ms: $command")
            val process = startContainerProcess(command, projectPath)
            val timedOut = AtomicBoolean(false)

            // 限幅累积：超大输出只保留开头+结尾，避免撑爆内存与模型上下文。
            val output = BoundedOutput()
            // 看门狗与读循环并发：超时则 destroy 进程，使阻塞的 readLine 立即返回 null 退出循环。
            var exitCode: Int? = null
            coroutineScope {
                val watchdog = launchKillWatchdog(this, process, effectiveTimeout, timedOut, command)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line!!)
                    output.append("\n")
                }
                exitCode = process.waitFor()
                watchdog.cancel()
            }

            if (timedOut.get()) {
                FileLogger.w(TAG, "命令超时(${effectiveTimeout}ms)已终止: $command")
                output.append(timeoutNotice(effectiveTimeout))
                output.append("\n")
                ExecResult(output.build(), null)
            } else {
                FileLogger.v(TAG, "命令完成(退出码 $exitCode，输出 ${output.totalChars} 字符): $command")
                ExecResult(output.build(), exitCode)
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "执行命令异常: $command", e)
            ExecResult("Error: ${e.message}", null)
        }
    }

    /**
     * 首次初始化时配置基础包（python3 / git / pip / node / npm）。幂等：已按 [PROVISION_VERSION] 配置则直接返回。
     * 失败（断网/超时/退出码非 0）不写标记，下次 [ensureInstalled] 自动重试，且**不抛异常**——
     * 配置失败不应阻塞用户使用容器（只是暂时没有这些工具）。
     *
     * 流式执行：用 [streamExecNoInstall]（不触发懒安装，避免与 [ensureInstalled] 递归）逐行拿到 apk 输出，
     * 实时更新 [initProgress]，让 UI 能看到「正在安装…」+ 下载进度行。脚本内先幂等覆盖 apk 源为阿里云
     * 国内镜像（兜底存量已解压旧 rootfs，其镜像源仍是官方 dl-cdn），再 update 索引、装包。
     */
    private suspend fun provisionIfNeeded() {
        val marker = provisionMarker
        if (marker.exists() && marker.readText().trim() == PROVISION_VERSION) return

        FileLogger.i(TAG, "开始配置容器基础包：$PROVISION_PACKAGES（首次需联网，可能耗时较久）")
        _initProgress.value = ContainerInitState.InstallingPackages(line = "配置国内镜像源…")

        // 幂等覆盖镜像源 + 刷新索引 + 装包。apk 源用 http（minirootfs 无 ca-certificates，见 ALPINE_MIRROR 注释）。
        val script = buildString {
            append("set -e\n")
            append("mkdir -p /etc/apk\n")
            append("cat > /etc/apk/repositories <<EOF\n")
            append("${ContainerInstaller.ALPINE_MIRROR}/${ContainerInstaller.ALPINE_BRANCH}/main\n")
            append("${ContainerInstaller.ALPINE_MIRROR}/${ContainerInstaller.ALPINE_BRANCH}/community\n")
            append("EOF\n")
            append("apk update\n")
            append("apk add --no-cache $PROVISION_PACKAGES\n")
        }

        var exitCode: Int? = null
        // rootfs 此时已由 ensureInstalled→installRootfsIfNeed 解压就绪；streamExecNoInstall 不再触发安装，无递归。
        streamExecNoInstall(script, projectPath = null, timeoutMs = PROVISION_TIMEOUT_MS).collect { event ->
            when (event) {
                is CommandEvent.Line ->
                    _initProgress.value = ContainerInitState.InstallingPackages(line = event.text)
                is CommandEvent.Exit -> exitCode = event.code
            }
        }

        if (exitCode == 0) {
            marker.writeText(PROVISION_VERSION)
            FileLogger.i(TAG, "容器基础包配置完成：$PROVISION_PACKAGES")
        } else {
            FileLogger.w(
                TAG,
                "容器基础包配置未完成（退出码=$exitCode），将在下次初始化重试"
            )
        }
    }

    /**
     * 启动看门狗：等待 [timeoutMs] 后若进程仍存活，则标记超时并优雅→强制终止，借此解除
     * 调用方阻塞中的 readLine。返回的 [Job] 由调用方在正常结束时 cancel 掉。
     */
    private fun launchKillWatchdog(
        scope: CoroutineScope,
        process: Process,
        timeoutMs: Long,
        timedOut: AtomicBoolean,
        command: String
    ): Job = scope.launch {
        delay(timeoutMs)
        if (process.isAlive) {
            timedOut.set(true)
            FileLogger.w(TAG, "命令执行超过 ${timeoutMs}ms，终止进程: $command")
            runCatching { process.destroy() }
            delay(TIMEOUT_KILL_GRACE_MS)
            if (process.isAlive) runCatching { process.destroyForcibly() }
        }
    }

    /** 超时提示行（拼进输出，喂回模型/展示给用户）。 */
    private fun timeoutNotice(timeoutMs: Long): String =
        "[命令执行超时：超过 ${timeoutMs}ms 已被强制终止]"

    /**
     * 启动进程。rootfs/proot 安装就绪则用 PRoot 进入 Alpine 容器；
     * 否则回退到 Android 原生 shell（rootfs 缺失时的兜底）。
     */
    private fun startContainerProcess(command: String, projectPath: String?): Process {
        val useProot = containerInstaller.isInstalled()

        val processBuilder = if (useProot) {
            buildProcessBuilder(buildProotInvocation(command, projectPath))
        } else {
            FileLogger.w(TAG, "PRoot 未安装，回退到原生 shell")
            buildNativeProcess(command, projectPath)
        }

        // Redirect stderr to stdout so we capture everything in one stream
        processBuilder.redirectErrorStream(true)
        return processBuilder.start()
    }

    /** 容器是否已安装就绪。 */
    fun isContainerInstalled(): Boolean = containerInstaller.isInstalled()

    /** 基础包是否已按当前 [PROVISION_VERSION] 配置完成。 */
    private fun isProvisioned(): Boolean {
        val marker = provisionMarker
        return marker.exists() && marker.readText().trim() == PROVISION_VERSION
    }

    /**
     * 幂等地确保容器可用：先解压 rootfs/proot（首次耗时），再配置基础包 python3/git/pip/node/npm（首次需联网）。
     * 供所有命令执行入口（[runCommandSync]/[runCommandStream]）在执行前统一调用。
     *
     * 用 [initMutex] 串行化：多入口并发时只让第一个真正解压/配置，其余等待后看到就绪直接置 [ContainerInitState.Ready]。
     * 全程通过 [initProgress] 上报阶段进度，供 UI 实时展示。
     */
    suspend fun ensureInstalled() = initMutex.withLock {
        // 每次启动或执行命令前确保提取最新的内置文档
        containerInstaller.extractDocs()
        if (containerInstaller.isInstalled() && isProvisioned()) {
            _initProgress.value = ContainerInitState.Ready
            return@withLock
        }
        // installRootfsIfNeed 在真正解压/部署时回调更新进度（已安装则快路径不回调）
        containerInstaller.installRootfsIfNeed { _initProgress.value = it }
        provisionIfNeeded()
        _initProgress.value =
            if (containerInstaller.isInstalled()) ContainerInitState.Ready
            else ContainerInitState.Failed("容器未安装（缺少 rootfs/proot）")
    }

    /**
     * 以长驻进程方式在容器内执行命令（如 `sshd -D`）。调用前需保证容器已安装。
     * 与 [runCommandStream] 不同，这里不读取/消费输出流，由调用方决定如何处理（通常丢弃）。
     */
    fun startProotProcess(command: String, projectPath: String?): Process {
        val pb = buildProcessBuilder(buildProotInvocation(command, projectPath))
        pb.redirectErrorStream(true)
        return pb.start()
    }

    /**
     * 以长驻进程方式在容器内启动一个程序并保留**分离的** stdin/stdout/stderr，供调用方
     * 双向流式通信（如 MCP stdio server：往 stdin 写 JSON-RPC、从 stdout 读 JSON-RPC）。
     *
     * 与 [startProotProcess] 的关键区别：**不** redirectErrorStream，stderr 独立保留，
     * 保证 stdout 是干净的协议流（server 的日志走 stderr，不会污染 JSON-RPC）。
     *
     * [program] 为容器内可执行文件名/路径（如 `npx`），[programArgs] 为其参数，二者经
     * `/bin/sh -c 'exec "$0" "$@"'` 逐项透传，避免 shell 引号/转义问题。[extraEnv] 叠加到
     * 容器默认环境（覆盖同名项）。调用前需保证容器已安装（[ensureInstalled]）。
     */
    fun startStdioProcess(
        program: String,
        programArgs: List<String>,
        projectPath: String?,
        extraEnv: Map<String, String> = emptyMap()
    ): Process {
        val invocation = buildStdioInvocation(program, programArgs, projectPath, extraEnv)
        val pb = buildProcessBuilder(invocation)
        // 刻意不 redirectErrorStream：stdout 留给 JSON-RPC，stderr 由调用方单独消费。
        return pb.start()
    }

    /**
     * 构造「在容器内直接 exec 某程序（保留分离流）」的 PRoot 调用。
     * 用 `sh -c 'exec "$0" "$@"' program arg1 arg2 …` 把参数原样交给 execvp，规避引号问题。
     */
    private fun buildStdioInvocation(
        program: String,
        programArgs: List<String>,
        projectPath: String?,
        extraEnv: Map<String, String>
    ): ProotInvocation {
        val argv = buildBaseProotArgv(projectPath)
        argv.add("/bin/sh")
        argv.add("-c")
        argv.add("exec \"\$0\" \"\$@\"")
        argv.add(program)
        argv.addAll(programArgs)
        return ProotInvocation(argv, buildContainerEnv() + extraEnv)
    }

    /**
     * 构造进入容器执行 [command] 的完整 PRoot 调用（argv + env）。
     * 暴露给终端会话：Termux TerminalSession 需要把可执行文件与参数分开传入。
     */
    fun buildProotInvocation(command: String, projectPath: String?): ProotInvocation {
        val argv = buildBaseProotArgv(projectPath)
        argv.add("/bin/sh")
        argv.add("-c")
        argv.add(command)
        return ProotInvocation(argv, buildContainerEnv())
    }

    /**
     * 构造 PRoot 调用的公共前缀 argv：proot 二进制 + rootfs + 标准绑定 + 伪 root + 工作区绑定，
     * 但**不含**最终的客户机命令（由各调用方自行追加 `/bin/sh -c …` 或 `exec` 形式）。
     */
    private fun buildBaseProotArgv(projectPath: String?): MutableList<String> {
        val rootfs = containerInstaller.rootfsDir.absolutePath
        val prootBin = containerInstaller.prootBin.absolutePath

        val argv = mutableListOf(
            prootBin,
            "-r", rootfs,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/system",  // 绑定 /system 让宿主动态库可用
            "-0"              // 伪 root，apk 等需要
        )

        // 把当前工作区目录绑定到容器内 /workspace，使命令与文件工具作用于同一目录
        if (projectPath != null) {
            argv.add("-b")
            argv.add("$projectPath:/workspace")
            argv.add("-w")
            argv.add("/workspace")
        }

        // 把 AI 配置目录绑定到容器内 /root/.aicode（读写）：内含 skills/（load_skill 读到的指令常引用
        // skill 目录里的脚本，AI 用 execute_command 执行 `python /root/.aicode/skills/<name>/x.py` 等）与
        // mcp.json（MCP 配置）。宿主物理目录独立于 rootfs，容器升级重装不丢用户数据。
        // 基础解释器 python3(3.12) 与 git 由 [provisionIfNeeded] 在首次初始化时自动 `apk add`；
        // node 等其他运行时仍由 skill / 用户自行保证。proot 的 -b 要求源路径存在，故先确保目录已建。
        val aicodeDir = containerInstaller.aicodeDir.apply { mkdirs() }
        argv.add("-b")
        argv.add("${aicodeDir.absolutePath}:/root/.aicode")

        return argv
    }

    /** 容器内进程的标准环境变量（proot loader / 动态库 / PATH / HOME 等）。 */
    private fun buildContainerEnv(): Map<String, String> {
        return mapOf(
            // Android proot 必需的环境变量
            "PROOT_TMP_DIR" to containerInstaller.prootTmpDir.absolutePath, // Android 没有 /tmp
            // Termux proot 的 loader 分离，必须用 PROOT_LOADER/_32 指向，否则无法注入子进程而起不来。
            "PROOT_LOADER" to containerInstaller.prootLoader.absolutePath,
            "PROOT_LOADER_32" to containerInstaller.prootLoader32.absolutePath,
            // Termux proot 动态链接 libtalloc.so.2 / libandroid-shmem.so，需让 linker64 能找到它们；
            // libc.so/liblog.so 走系统默认路径(/system/lib64)。
            "LD_LIBRARY_PATH" to "${containerInstaller.prootLibDir.absolutePath}:/system/lib64:/system/lib",
            // 说明（statx / seccomp）：旧 proot 5.1.0 的 seccomp 过滤表没有 statx，Node 用 statx 解析
            // 模块路径会拿到未翻译的 /workspace/xxx → ENOENT「Cannot find module」。Termux proot
            // (5.1.107.x) 的 seccomp 过滤表已包含 statx，默认 seccomp 模式即可正确翻译，故此处
            // **刻意不设 PROOT_NO_SECCOMP**——这正是 Termux 自己用 proot 的方式；强制全量 ptrace
            // (PROOT_NO_SECCOMP=1) 反而在本设备触发过 ptrace(PEEKDATA) I/O error。
            "PATH" to "/usr/bin:/bin:/usr/sbin:/sbin",
            "HOME" to "/root",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8"
        )
    }

    private fun buildProcessBuilder(invocation: ProotInvocation): ProcessBuilder {
        val processBuilder = ProcessBuilder(invocation.argv)
        processBuilder.environment().putAll(invocation.env)
        return processBuilder
    }

    private fun buildNativeProcess(command: String, projectPath: String?): ProcessBuilder {
        // Fallback to Android's native shell
        val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
        if (projectPath != null) {
            processBuilder.directory(java.io.File(projectPath))
        }
        return processBuilder
    }
}
