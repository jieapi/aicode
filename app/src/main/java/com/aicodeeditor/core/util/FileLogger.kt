package com.aicodeeditor.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 把日志（尤其是错误与崩溃）落盘到 App 的内部存储，方便在没有连接 adb 的情况下调试。
 *
 * 日志写到外部私有目录 `getExternalFilesDir/logs/` 下（不可用时回退到内部 `filesDir/logs/`），
 * 按天分文件（log-yyyy-MM-dd.txt）。外部私有目录可通过文件管理器在
 * `/storage/emulated/0/Android/data/<包名>/files/logs/` 直接查看，无需 root。
 * 所有写入都串行化到单线程后台执行，避免阻塞主线程与多线程交错。同时镜像一份到 [android.util.Log]。
 *
 * 使用前需在 [Application.onCreate] 调用一次 [init]。
 */
object FileLogger {

    private const val TAG = "FileLogger"
    private const val MAX_AGE_DAYS = 7
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024 // 单个日志文件上限 2MB

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "file-logger").apply { isDaemon = true }
    }
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logDir: File? = null

    /** 初始化日志目录。重复调用安全。 */
    fun init(context: Context) {
        if (logDir != null) return
        // 优先外部私有目录，便于用户用文件管理器查看；不可用时回退内部存储。
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "logs").apply { mkdirs() }
        logDir = dir
        ioExecutor.execute { cleanupOldLogs(dir) }
        i(TAG, "FileLogger 初始化完成，日志目录: ${dir.absolutePath}")
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        write("DEBUG", tag, message, null)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("INFO", tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        write("WARN", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        write("ERROR", tag, message, throwable)
    }

    /** 返回当前所有日志文件，按文件名（即日期）排序，供"查看日志"等界面使用。 */
    fun listLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.startsWith("log-") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val dir = logDir ?: return // 未初始化则只走 logcat，不落盘
        val now = Date()
        val line = buildString {
            append(timestampFormat.format(now))
            append(" ").append(level)
            append(" [").append(tag).append("] ")
            append(message)
            if (throwable != null) {
                append("\n").append(stackTraceToString(throwable))
            }
            append("\n")
        }
        ioExecutor.execute {
            runCatching {
                val file = File(dir, "log-${fileNameFormat.format(now)}.txt")
                if (file.length() > MAX_FILE_BYTES) {
                    // 超过上限则截断重开，避免单文件无限增长
                    file.writeText("--- 日志文件超过 ${MAX_FILE_BYTES / 1024 / 1024}MB 已重置 ---\n")
                }
                file.appendText(line)
            }.onFailure {
                Log.e(TAG, "写入日志失败", it)
            }
        }
    }

    private fun stackTraceToString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString().trimEnd()
    }

    /** 删除超过 [MAX_AGE_DAYS] 天的日志文件。 */
    private fun cleanupOldLogs(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_DAYS * 24L * 60 * 60 * 1000
        dir.listFiles { f -> f.isFile && f.name.startsWith("log-") }?.forEach { file ->
            if (file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }
}
