package com.aicode

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aicode.core.util.AILogger
import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.ContainerInstaller
import com.aicode.feature.credentials.data.GitCredentialsFileSync
import com.aicode.feature.agent.domain.mcp.McpManager
import com.aicode.feature.settings.data.repository.KeepaliveSettingsRepository
import com.aicode.feature.settings.data.repository.LogSettingsRepository
import com.aicode.feature.terminal.domain.TerminalKeepaliveService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AIEditorApp : Application() {

    /** Hilt 字段注入：在 [onCreate] 的 super 调用后即可用。 */
    @Inject
    lateinit var logSettings: LogSettingsRepository

    /** 后台保活开关持久化。 */
    @Inject
    lateinit var keepaliveSettings: KeepaliveSettingsRepository

    /** MCP 生命周期总管：启动即连接已配置的远程 server。 */
    @Inject
    lateinit var mcpManager: McpManager

    /** git 凭据/署名落盘同步器：启动即把 Room 凭据 + DataStore 署名写到容器持久挂载目录，
     *  供终端/AI/UI 三端 git 经 credential.helper=store 共用，兜底 rootfs 升级或文件被删。 */
    @Inject
    lateinit var gitCredentialsFileSync: GitCredentialsFileSync

    /** 三端 git 缺凭据的统一弹窗桥：监听容器内 credential helper 经文件 IPC 发来的未登录请求，
     *  暴露 StateFlow 供全局弹窗回填后回喂 git。必须在主线程启动（FileObserver 绑定主 Looper）。 */
    @Inject
    lateinit var credentialRequestBridge: com.aicode.feature.credentials.data.CredentialRequestBridge

    /** 长驻作用域：持续把持久化的日志等级同步到 FileLogger。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        AILogger.init(this)
        installCrashHandler()
        createNotificationChannels()
        // 主线程启动凭据请求监听（FileObserver 必须主线程创建与 startWatching），
        // 监听容器内 credential helper 写来的 cred-req-* → 全局弹窗回填 → 回喂 git 续跑。
        credentialRequestBridge.start()
        // 启动即把最新的内置指南手册提取到私有配置目录
        appScope.launch {
            ContainerInstaller.extractDocs(this@AIEditorApp)
        }
        // 启动即把内置提示词全量释放到 ~/.aicode/prompts/（覆盖式，随 App 升级更新）；
        // 用户自定义覆盖放在 ~/.aicode/prompts.custom/，同名即覆盖，不被升级覆盖。
        appScope.launch {
            ContainerInstaller.extractPrompts(this@AIEditorApp)
        }
        // 启动即把 Room 凭据 + DataStore 署名落盘到容器持久挂载（/root/.aicode），
        // 让终端裸 git / AI 工具 / UI 三端共用同一份凭据与署名配置。
        appScope.launch {
            gitCredentialsFileSync.syncAll()
        }
        // 启动即加载持久化等级，并随设置页改动实时生效（唯一同步点）。
        appScope.launch {
            logSettings.levelFlow.collectLatest { FileLogger.setMinLevel(it) }
        }
        // 后台保活常驻通知的唯一反应器：监听开关，启停 TerminalKeepaliveService 的常驻模式。
        // 既覆盖设置页实时切换，也覆盖冷启动恢复。仅在「由开变关」时发 disable，
        // 避免为关闭而凭空拉起从未开过的 Service。
        appScope.launch {
            var last: Boolean? = null
            keepaliveSettings.enabledFlow.distinctUntilChanged().collect { enabled ->
                if (enabled) {
                    TerminalKeepaliveService.enablePersistent(this@AIEditorApp)
                } else if (last == true) {
                    TerminalKeepaliveService.disablePersistent(this@AIEditorApp)
                }
                last = enabled
            }
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

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                "terminal_service",
                "Terminal Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for background terminal tasks"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
