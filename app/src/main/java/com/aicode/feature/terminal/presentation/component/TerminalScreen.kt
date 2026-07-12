package com.aicode.feature.terminal.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.rememberImeBottomInset
import com.aicode.feature.agent.domain.container.ContainerInitState
import com.aicode.feature.terminal.domain.TerminalSessionManager
import com.aicode.feature.terminal.presentation.TerminalViewModel
import com.termux.view.TerminalView
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onNavigateBack: () -> Unit
) {
    val prepareState by viewModel.prepareState.collectAsStateWithLifecycle()
    val containerInit by viewModel.containerInit.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    // revision 变化时强制重组：标签输出/状态在管理器里就地更新（非 data class 替换），靠它驱动刷新。
    val revision by viewModel.revision.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("终端") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reconnectActive() }) {
                        Icon(
                            FeatherIcons.RefreshCw,
                            contentDescription = "重连当前标签",
                            tint = androidx.compose.ui.graphics.Color(0xFF424242))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = rememberImeBottomInset())
        ) {
            when (val state = prepareState) {
                is TerminalViewModel.PrepareState.Loading -> StatusView(
                    loading = true,
                    message = containerInitMessage(containerInit)
                )

                is TerminalViewModel.PrepareState.Error -> StatusView(
                    loading = false,
                    message = "无法启动终端：\n${state.message}",
                    actionLabel = "重试",
                    onAction = { viewModel.prepare() }
                )

                is TerminalViewModel.PrepareState.Ready -> {
                    // revision 仅用于触发重组：读一下避免被优化掉。
                    @Suppress("UNUSED_EXPRESSION") revision

                    TabBar(
                        tabs = tabs,
                        activeTabId = activeTabId,
                        onSelect = { viewModel.activate(it) },
                        onClose = { viewModel.closeTab(it) },
                        onNew = { viewModel.newTab() }
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val active = tabs.firstOrNull { it.id == activeTabId }
                        if (active == null) {
                            StatusView(
                                loading = false,
                                message = "没有打开的终端标签",
                                actionLabel = "新建标签",
                                onAction = { viewModel.newTab() }
                            )
                        } else {
                            // 切换标签时按 id 重建 TerminalView，并把视图回填到该 tab、挂载其会话。
                            key(active.id) {
                                TerminalSurface(tab = active, viewModel = viewModel)
                            }
                        }
                    }

                    if (activeTabId != null) {
                        ExtraKeysRow(viewModel)
                    }
                }
            }
        }
    }
}

/** 可横滑的标签栏：每个标签显示状态点 + 标题 + 关闭；末尾「+」新建。 */
@Composable
private fun TabBar(
    tabs: List<TerminalSessionManager.TerminalTab>,
    activeTabId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                TabChip(
                    tab = tab,
                    selected = tab.id == activeTabId,
                    onClick = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) }
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable(onClick = onNew),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    FeatherIcons.Plus,
                    contentDescription = "新建标签",
                    tint = androidx.compose.ui.graphics.Color(0xFF424242),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TabChip(
    tab: TerminalSessionManager.TerminalTab,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    // 状态点：运行中=绿，已结束=灰；后台标签用主题三级色提示。
    val running = tab.runState is TerminalSessionManager.RunState.Running
    val dot = when {
        !running -> MaterialTheme.colorScheme.outline
        tab.isBackground -> MaterialTheme.colorScheme.tertiary
        else -> Color(0xFF22C55E)
    }
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Text(
            text = tab.title,
            color = fg,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                FeatherIcons.X,
                contentDescription = "关闭标签",
                tint = androidx.compose.ui.graphics.Color(0xFF424242),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Termux TerminalView 的 Compose 包装：渲染与输入全部由该开源组件负责。 */
@Composable
private fun TerminalSurface(tab: TerminalSessionManager.TerminalTab, viewModel: TerminalViewModel) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        factory = { ctx ->
            val view = TerminalView(ctx, null)
            val density = ctx.resources.displayMetrics.density
            view.setTextSize((11f * density).toInt())
            view.setTerminalViewClient(
                AppTerminalViewClient(
                    context = ctx,
                    viewProvider = { view },
                    modifiers = viewModel.modifiers
                )
            )
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            // 把视图回填到该标签：会话 client 的 viewProvider 据此把输出刷到当前挂载的视图。
            tab.view = view
            view.attachSession(tab.session)
            // 切回已有标签时立即把累计的屏幕缓冲渲染出来。
            view.onScreenUpdated()
            view.requestFocus()
            view
        },
        onRelease = { view ->
            // 视图被回收（切走/重建）时解除引用，避免管理器把输出刷到已废弃的视图。
            if (tab.view === view) tab.view = null
        }
    )
}

@Composable
private fun StatusView(
    loading: Boolean,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.md))
        }
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 把容器初始化进度状态映射为 Loading 阶段展示给用户的文案。 */
private fun containerInitMessage(state: ContainerInitState): String = when (state) {
    is ContainerInitState.ExtractingRootfs ->
        "正在解压环境…\n已处理 ${state.processed} 个文件"
    ContainerInitState.DeployingProot ->
        "正在部署 proot 运行时…"
    is ContainerInitState.InstallingPackages ->
        "正在安装 python3 / git / pip / node / npm…\n${state.line ?: ""}"
    is ContainerInitState.Failed ->
        "正在准备容器环境…\n${state.reason}"
    ContainerInitState.Idle, ContainerInitState.Ready ->
        "正在准备容器环境…\n首次运行会解压容器，请稍候"
}

/** 手机软键盘缺失的常用按键：Esc / Tab / Ctrl(预置) / 方向键 / Ctrl-C / Ctrl-D。 */
@Composable
private fun ExtraKeysRow(viewModel: TerminalViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            KeyChip("Esc") { viewModel.write("") }
            KeyChip("Tab") { viewModel.write("\t") }
            KeyChip("Ctrl", active = viewModel.modifiers.ctrl) {
                viewModel.modifiers.ctrl = !viewModel.modifiers.ctrl
            }
            KeyChip("←") { viewModel.write("[D") }
            KeyChip("↑") { viewModel.write("[A") }
            KeyChip("↓") { viewModel.write("[B") }
            KeyChip("→") { viewModel.write("[C") }
            KeyChip("C-c") { viewModel.writeBytes(0x03) }
            KeyChip("C-d") { viewModel.writeBytes(0x04) }
            KeyChip("/") { viewModel.write("/") }
            KeyChip("-") { viewModel.write("-") }
        }
    }
}

@Composable
private fun KeyChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .height(36.dp)
            .widthChip(label)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/** 单字符按键给固定宽度，多字符按键自适应。 */
private fun Modifier.widthChip(label: String): Modifier =
    if (label.length <= 1) this.width(40.dp) else this
