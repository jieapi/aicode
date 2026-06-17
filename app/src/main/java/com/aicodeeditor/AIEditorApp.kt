package com.aicodeeditor

import android.app.Application
import com.aicodeeditor.core.util.AILogger
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.mcp.McpManager
import com.aicodeeditor.feature.settings.data.repository.LogSettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AIEditorApp : Application() {

    /** Hilt 字段注入：在 [onCreate] 的 super 调用后即可用。 */
    @Inject
    lateinit var logSettings: LogSettingsRepository

    /** MCP 生命周期总管：启动即连接已配置的远程 server。 */
    @Inject
    lateinit var mcpManager: McpManager

    /** 长驻作用域：持续把持久化的日志等级同步到 FileLogger。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        AILogger.init(this)
        installCrashHandler()
        // 启动即加载持久化等级，并随设置页改动实时生效（唯一同步点）。
        appScope.launch {
            logSettings.levelFlow.collectLatest { FileLogger.setMinLevel(it) }
        }
        // 连接已配置的 MCP server，把其工具注册进 ToolRegistry（内部自有 scope，失败不影响启动）。
        mcpManager.start()
    }

    /** 捕获未处理异常并落盘，随后交回系统默认处理器（保留原有崩溃弹窗/上报行为）。 */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            FileLogger.e("CRASH", "线程 ${thread.name} 未捕获异常", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
