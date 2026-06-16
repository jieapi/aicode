package com.aicodeeditor.feature.terminal.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.LinuxContainerEngine
import com.aicodeeditor.feature.terminal.domain.SshServerManager
import com.aicodeeditor.feature.terminal.presentation.component.AppTerminalSessionClient
import com.aicodeeditor.feature.terminal.presentation.component.TerminalKeyModifiers
import com.aicodeeditor.feature.workspace.data.repository.WorkspaceRepository
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 真实 SSH 终端的状态机：
 *  1. 在本地 Alpine 容器内准备并启动 sshd（[SshServerManager]）；
 *  2. 用 Termux 的 [TerminalSession] 拉起一个 PTY，进程为 `proot … sshpass … ssh …`，
 *     从而以真实 SSH 会话登录容器，获得完整交互能力；
 *  3. 渲染交给 Termux 的 [TerminalView]（见 TerminalScreen）。
 *
 * 与 AI 的 execute_command 工具共用同一容器后端与工作区目录。
 */
@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val containerEngine: LinuxContainerEngine,
    private val sshManager: SshServerManager,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private companion object {
        const val TAG = "TerminalViewModel"
        const val TRANSCRIPT_ROWS = 2000
    }

    sealed interface UiState {
        /** 正在安装/启动 sshd 并建立会话。 */
        data object Connecting : UiState
        /** 会话就绪，可渲染。 */
        data class Ready(val session: TerminalSession) : UiState
        /** 准备失败（如首次无网络无法安装 openssh）。 */
        data class Error(val message: String) : UiState
        /** SSH 会话已结束（exit/断开），可重连。 */
        data object Disconnected : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Connecting)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 额外按键行驱动的虚拟修饰键，供 TerminalView 读取。 */
    val modifiers = TerminalKeyModifiers()

    /** 由 Compose 在创建 [TerminalView] 后回填；会话客户端据此刷新屏幕。 */
    @Volatile
    var terminalView: TerminalView? = null

    /** 会话事件回调：输出刷新转给视图、结束时切到 Disconnected。 */
    val sessionClient = AppTerminalSessionClient(
        context = appContext,
        viewProvider = { terminalView },
        onFinished = { _uiState.value = UiState.Disconnected }
    )

    private var currentSession: TerminalSession? = null

    init {
        connect()
    }

    /** 建立（或在错误后重试）SSH 会话。 */
    fun connect() {
        if (_uiState.value is UiState.Ready) return
        viewModelScope.launch {
            _uiState.value = UiState.Connecting
            val workspace = workspaceRepository.currentPath()
            when (val result = sshManager.prepare(workspace)) {
                is SshServerManager.Result.Ready -> {
                    val invocation = containerEngine.buildProotInvocation(
                        sshManager.buildConnectCommand(),
                        workspace
                    )
                    val session = TerminalSession(
                        invocation.executable,
                        appContext.filesDir.absolutePath,
                        invocation.args.toTypedArray(),
                        invocation.env.map { "${it.key}=${it.value}" }.toTypedArray(),
                        TRANSCRIPT_ROWS,
                        sessionClient
                    )
                    currentSession = session
                    _uiState.value = UiState.Ready(session)
                    FileLogger.i(TAG, "SSH 终端会话已建立")
                }
                is SshServerManager.Result.Failed -> {
                    FileLogger.w(TAG, "SSH 终端准备失败: ${result.message}")
                    _uiState.value = UiState.Error(result.message)
                }
            }
        }
    }

    /** 断开后重连：结束旧会话并重建。 */
    fun reconnect() {
        currentSession?.finishIfRunning()
        currentSession = null
        terminalView = null
        _uiState.value = UiState.Connecting
        connect()
    }

    /** 向会话写入文本（额外按键行：方向键/Tab 等）。 */
    fun write(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        currentSession?.write(bytes, 0, bytes.size)
    }

    /** 向会话写入原始字节（发送控制字符，如 Ctrl-C=0x03、Ctrl-D=0x04）。 */
    fun writeBytes(vararg bytes: Int) {
        val arr = ByteArray(bytes.size) { bytes[it].toByte() }
        currentSession?.write(arr, 0, arr.size)
    }

    override fun onCleared() {
        currentSession?.finishIfRunning()
        // 不在此停止 sshd：它是单例长驻进程，下次进入终端可直接复用。
        super.onCleared()
    }
}
