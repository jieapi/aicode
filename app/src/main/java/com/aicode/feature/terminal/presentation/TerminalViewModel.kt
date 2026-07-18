package com.aicode.feature.terminal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.ContainerInitState
import com.aicode.feature.agent.domain.container.LinuxContainerEngine
import com.aicode.feature.terminal.domain.TerminalSessionManager
import com.aicode.feature.terminal.presentation.component.TerminalKeyModifiers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 终端页的薄观察层。
 *
 * 会话的所有权在 [TerminalSessionManager]（Singleton），本 ViewModel 只转发 UI 操作并暴露其状态流，
 * **不持有也不销毁任何会话**——这正是「常驻后台」的关键：离开终端页导致本 VM onCleared 时，
 * 会话仍由管理器持有、继续在后台运行，下次回到终端页直接复用。
 */
@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sessionManager: TerminalSessionManager,
    private val containerEngine: LinuxContainerEngine
) : ViewModel() {

    private companion object { const val TAG = "TerminalViewModel" }

    /** 容器准备阶段的整体状态：仅用于首个标签创建前的 Loading/Error 提示。 */
    sealed interface PrepareState {
        data object Loading : PrepareState
        data object Ready : PrepareState
        data class Error(val message: String) : PrepareState
    }

    private val _prepareState = MutableStateFlow<PrepareState>(PrepareState.Loading)
    val prepareState: StateFlow<PrepareState> = _prepareState.asStateFlow()

    /** 容器初始化实时进度（解压/部署/装包），Loading 阶段用它展示细粒度文案。 */
    val containerInit: StateFlow<ContainerInitState> = containerEngine.initProgress

    val tabs = sessionManager.tabs
    val activeTabId = sessionManager.activeTabId
    val revision = sessionManager.revision

    /** 额外按键行驱动的虚拟修饰键，供 TerminalView 读取。 */
    val modifiers = TerminalKeyModifiers()

    init {
        prepare()
    }

    /** 进入终端页：确保至少有一个标签（首次会解压容器）。 */
    fun prepare() {
        viewModelScope.launch {
            _prepareState.value = PrepareState.Loading
            try {
                sessionManager.ensureInitialTab()
                _prepareState.value = PrepareState.Ready
            } catch (e: Exception) {
                FileLogger.e(TAG, "终端准备失败", e)
                _prepareState.value = PrepareState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun newTab() {
        viewModelScope.launch {
            try {
                sessionManager.createInteractiveTab()
            } catch (e: Exception) {
                FileLogger.e(TAG, "新建标签失败", e)
            }
        }
    }

    fun activate(id: String) = sessionManager.activate(id)

    fun closeTab(id: String) = sessionManager.closeTab(id)

    /** 关闭所有终端标签（切换工作区前调用）。 */
    fun closeAllTabs() {
        sessionManager.tabs.value.map { it.id }.forEach { sessionManager.closeTab(it) }
    }

    fun reconnectActive() {
        val id = activeTabId.value ?: return
        viewModelScope.launch { runCatching { sessionManager.reconnect(id) } }
    }

    /** 向当前活动标签写入文本（额外按键行：方向键/Tab 等）。 */
    fun write(text: String) = sessionManager.writeToActive(text)

    /** 向当前活动标签写入原始字节（发送控制字符，如 Ctrl-C=0x03、Ctrl-D=0x04）。 */
    fun writeBytes(vararg bytes: Int) = sessionManager.writeBytesToActive(*bytes)

    // 注意：故意不在 onCleared 里销毁会话——会话归 Singleton 管理器所有，需常驻后台。
}
