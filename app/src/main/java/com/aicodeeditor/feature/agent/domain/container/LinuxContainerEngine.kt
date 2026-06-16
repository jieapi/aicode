package com.aicodeeditor.feature.agent.domain.container

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
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
 * [argv] 的第 0 个元素即 proot 二进制路径，其余为参数。拆出来是为了既能喂给
 * [ProcessBuilder]，也能交给 Termux 的 TerminalSession（它需要 shellPath 与 args 分开）。
 */
data class ProotInvocation(
    val argv: List<String>,
    val env: Map<String, String>
) {
    val executable: String get() = argv.first()
    val args: List<String> get() = argv.drop(1)
}

@Singleton
class LinuxContainerEngine @Inject constructor(
    private val containerInstaller: ContainerInstaller
) {
    companion object {
        private const val TAG = "LinuxContainerEngine"
    }

    /**
     * 在容器内流式执行命令：每读到一行就 emit 一个 [CommandEvent.Line]，命令结束 emit
     * [CommandEvent.Exit]。首次调用会触发 rootfs 安装（幂等）。
     *
     * 与 [runCommandSync] 共用同一进程构建逻辑，区别仅在于输出按行实时下发，
     * 让终端能看到执行「过程」而非只有最终结果。
     */
    fun runCommandStream(command: String, projectPath: String? = null): Flow<CommandEvent> = flow {
        // 懒安装：首次执行命令时解压 rootfs/proot
        containerInstaller.installRootfsIfNeed()

        val process = startContainerProcess(command, projectPath)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                emit(CommandEvent.Line(line!!))
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) Log.w(TAG, "Command exited with code: $exitCode")
            emit(CommandEvent.Exit(exitCode))
        } finally {
            // 协程取消（用户离开页面等）时确保子进程被回收，避免泄漏
            runCatching { reader.close() }
            runCatching { process.destroy() }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 在容器内同步执行命令并返回输出。首次调用会触发 rootfs 安装（幂等）。
     */
    suspend fun runCommandSync(command: String, projectPath: String? = null): String = withContext(Dispatchers.IO) {
        try {
            // 懒安装：首次执行命令时解压 rootfs/proot，避免拖慢 App 启动
            containerInstaller.installRootfsIfNeed()

            val process = startContainerProcess(command, projectPath)

            // Read output
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            // Wait for command to finish
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "Command exited with code: $exitCode")
            }

            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: $command", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 启动进程。rootfs/proot 安装就绪则用 PRoot 进入 Alpine 容器；
     * 否则回退到 Android 原生 shell（rootfs 缺失时的兜底）。
     */
    private fun startContainerProcess(command: String, projectPath: String?): Process {
        val useProot = containerInstaller.isInstalled()

        val processBuilder = if (useProot) {
            buildProcessBuilder(buildProotInvocation(command, projectPath))
        } else {
            Log.w(TAG, "PRoot 未安装，回退到原生 shell")
            buildNativeProcess(command, projectPath)
        }

        // Redirect stderr to stdout so we capture everything in one stream
        processBuilder.redirectErrorStream(true)
        return processBuilder.start()
    }

    /** 容器是否已安装就绪。 */
    fun isContainerInstalled(): Boolean = containerInstaller.isInstalled()

    /** 幂等地确保 rootfs/proot 已安装（首次会解压，耗时）。 */
    suspend fun ensureInstalled() = containerInstaller.installRootfsIfNeed()

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
     * 构造进入容器执行 [command] 的完整 PRoot 调用（argv + env）。
     * 暴露给终端会话：Termux TerminalSession 需要把可执行文件与参数分开传入。
     */
    fun buildProotInvocation(command: String, projectPath: String?): ProotInvocation {
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

        argv.add("/bin/sh")
        argv.add("-c")
        argv.add(command)

        val env = mapOf(
            // Android proot 必需的环境变量
            "PROOT_TMP_DIR" to containerInstaller.prootTmpDir.absolutePath, // Android 没有 /tmp
            "PATH" to "/usr/bin:/bin:/usr/sbin:/sbin",
            "HOME" to "/root",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8"
        )

        return ProotInvocation(argv, env)
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
