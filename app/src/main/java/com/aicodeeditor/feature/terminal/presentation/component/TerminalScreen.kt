package com.aicodeeditor.feature.terminal.presentation.component

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.collectAsState
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
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.terminal.presentation.TerminalViewModel
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("终端 · SSH") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reconnect() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重连",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val state = uiState) {
                    is TerminalViewModel.UiState.Connecting -> StatusView(
                        loading = true,
                        message = "正在准备 SSH 环境…\n首次运行会在容器内安装 SSH 组件（需联网），请稍候"
                    )

                    is TerminalViewModel.UiState.Error -> StatusView(
                        loading = false,
                        message = "无法启动 SSH 终端：\n${state.message}",
                        actionLabel = "重试",
                        onAction = { viewModel.connect() }
                    )

                    is TerminalViewModel.UiState.Disconnected -> StatusView(
                        loading = false,
                        message = "SSH 会话已结束",
                        actionLabel = "重新连接",
                        onAction = { viewModel.reconnect() }
                    )

                    is TerminalViewModel.UiState.Ready ->
                        // session 变化（重连）时用 key 强制重建 TerminalView 并重新挂载会话
                        key(state.session) {
                            TerminalSurface(session = state.session, viewModel = viewModel)
                        }
                }
            }

            if (uiState is TerminalViewModel.UiState.Ready) {
                ExtraKeysRow(viewModel)
            }
        }
    }
}

/** Termux TerminalView 的 Compose 包装：渲染与输入全部由该开源组件负责。 */
@Composable
private fun TerminalSurface(session: TerminalSession, viewModel: TerminalViewModel) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        factory = { ctx ->
            val view = TerminalView(ctx, null)
            val density = ctx.resources.displayMetrics.density
            view.setTextSize((14f * density).toInt())
            view.setTerminalViewClient(
                AppTerminalViewClient(
                    context = ctx,
                    viewProvider = { view },
                    modifiers = viewModel.modifiers
                )
            )
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            viewModel.terminalView = view
            view.attachSession(session)
            view.requestFocus()
            view
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

/** 手机软键盘缺失的常用按键：Esc / Tab / Ctrl(预置) / 方向键 / Ctrl-C / Ctrl-D。 */
@Composable
private fun ExtraKeysRow(viewModel: TerminalViewModel) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            KeyChip("Esc") { viewModel.write("") }
            KeyChip("Tab") { viewModel.write("\t") }
            // 预置 Ctrl：下一个字符将作为 Ctrl 组合发送（如按 Ctrl 再按 c）
            KeyChip("Ctrl") { viewModel.modifiers.ctrl = true }
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
private fun KeyChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .widthChip(label)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/** 单字符按键给固定宽度，多字符按键自适应。 */
private fun Modifier.widthChip(label: String): Modifier =
    if (label.length <= 1) this.width(40.dp) else this
