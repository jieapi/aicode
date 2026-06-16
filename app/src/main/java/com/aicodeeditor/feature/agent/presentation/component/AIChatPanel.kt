package com.aicodeeditor.feature.agent.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Brand
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.agent.domain.model.CodeChange
import com.aicodeeditor.feature.agent.domain.model.ChangeType
import com.aicodeeditor.feature.agent.presentation.AgentUIMessage
import com.aicodeeditor.feature.agent.presentation.AgentUIState
import com.aicodeeditor.feature.agent.presentation.AIAgentViewModel
import com.aicodeeditor.feature.agent.presentation.MessageRole
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.presentation.SettingsViewModel
import com.aicodeeditor.feature.workspace.presentation.WorkspaceViewModel
import com.aicodeeditor.feature.workspace.presentation.component.WorkspaceChip
import kotlinx.coroutines.launch

private val brandGradient = Brush.linearGradient(listOf(Brand.Indigo, Brand.Violet))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatPanel(
    viewModel: AIAgentViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
    settingsViewModel: SettingsViewModel? = null,
    workspaceViewModel: WorkspaceViewModel? = null,
    currentFile: String? = null,
    selectedCode: String? = null,
    modifier: Modifier = Modifier
) {
    val agentState by viewModel.agentState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val changes by viewModel.changes.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val runningTool by viewModel.runningTool.collectAsState()
    val activeProvider = settingsViewModel?.activeProvider?.collectAsState()?.value
    val currentWorkspace = workspaceViewModel?.current?.collectAsState()?.value
    // 工作区路径作为 AI 文件/命令操作的根目录；尚未就绪时为空串。
    val projectRoot = currentWorkspace?.path ?: ""

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val isBusy = agentState is AgentUIState.Loading || agentState is AgentUIState.Streaming

    val sendMessage: () -> Unit = {
        val text = inputText.trim()
        if (text.isNotEmpty() && !isBusy) {
            viewModel.executeAgentRequestStream(text, currentFile, selectedCode, projectRoot)
            inputText = ""
        }
    }

    // 新消息、流式文字或工具实时输出更新时自动滚动到底部。
    LaunchedEffect(messages.size, messages.lastOrNull()?.content, runningTool?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // 侧边栏打开时，系统返回键先收起侧边栏，而非退出页面。
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                // 侧边栏约占屏幕四分之三宽。
                modifier = Modifier.fillMaxWidth(0.75f)
            ) {
                ChatDrawerContent(
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    onSelect = {
                        viewModel.selectSession(it.id)
                        scope.launch { drawerState.close() }
                    },
                    onCreate = {
                        viewModel.newSession()
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { viewModel.deleteSession(it.id) },
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ChatHeader(
                    workspaceViewModel = workspaceViewModel,
                    modelName = activeProvider?.effectiveModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewChat = { viewModel.newSession() },
                    onNavigateToTerminal = onNavigateToTerminal
                )
            }
        ) { padding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (messages.isEmpty()) {
                        WelcomeState(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = Spacing.lg,
                                vertical = Spacing.md
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                // 命中运行中工具的占位行时，传入实时累积输出叠加渲染。
                                val live = runningTool?.takeIf { it.messageId == message.id }?.text
                                AgentMessageItem(message = message, liveOutput = live)
                            }
                        }
                    }
                }

                // 代码变更预览
                AnimatedVisibility(
                    visible = changes.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ChangePreviewPanel(
                        changes = changes,
                        onApply = { viewModel.applyChanges(changes) },
                        onReject = { viewModel.rejectChanges() }
                    )
                }

                // 状态提示（错误 / 已应用）
                StatusBanner(state = agentState)

                // 输入栏（两行容器：上行输入，下行工作区/模型切换 + 发送）
                ChatInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = sendMessage,
                    isBusy = isBusy,
                    workspaceViewModel = workspaceViewModel,
                    activeProvider = activeProvider,
                    onSelectModel = { model ->
                        val vm = settingsViewModel
                        val provider = activeProvider
                        if (vm != null && provider != null) vm.selectModel(provider.id, model)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(
    workspaceViewModel: WorkspaceViewModel?,
    modelName: String?,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToTerminal: () -> Unit
) {
    val currentWorkspace = workspaceViewModel?.current?.collectAsState()?.value
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "打开侧边栏",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentWorkspace?.name ?: "AI Code Editor",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = modelName?.takeIf { it.isNotBlank() } ?: "未选择模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onNewChat) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "新建会话",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNavigateToTerminal) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = "终端",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.sm))
            .background(brandGradient),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun WelcomeState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark(size = 64.dp, iconSize = 34.dp)
        Spacer(Modifier.height(Spacing.xl))
        Text(
            text = "有什么可以帮你的？",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "描述你的需求，我来帮你编写、修改或解释代码",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AgentMessageItem(message: AgentUIMessage, liveOutput: String? = null) {
    // 纯工具调用的 assistant 行（无文字、仅用于回放配对）不渲染气泡，避免出现空白「…」。
    if (message.role == MessageRole.ASSISTANT && message.content.isBlank()) return

    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = if (isUser) {
                RoundedCornerShape(Radius.md, Radius.md, Radius.xs, Radius.md)
            } else {
                RoundedCornerShape(Radius.md, Radius.md, Radius.md, Radius.xs)
            },
            color = when (message.role) {
                MessageRole.USER -> MaterialTheme.colorScheme.primary
                MessageRole.ASSISTANT -> MaterialTheme.colorScheme.surface
                MessageRole.TOOL -> MaterialTheme.colorScheme.surfaceVariant
            },
            border = if (message.role == MessageRole.ASSISTANT) {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            } else null,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            if (message.role == MessageRole.TOOL) {
                ToolMessageBody(message, liveOutput = liveOutput)
            } else {
                SelectionContainer {
                    Text(
                        text = message.content.ifEmpty { "…" },
                        color = when (message.role) {
                            MessageRole.USER -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * 工具消息：默认折叠为一行「状态图标 + 工具名 + 箭头」，点击展开查看完整结果。
 * [liveOutput] 非空时进入「实时输出」模式：自动展开、显示 spinner 与逐行累积输出。
 */
@Composable
private fun ToolMessageBody(message: AgentUIMessage, liveOutput: String? = null) {
    val streaming = liveOutput != null
    val running = streaming || message.content.startsWith("⏳")
    val icon = when {
        running -> "⏳"
        message.isError -> "❌"
        else -> "✅"
    }
    // 流式过程中展示实时输出；否则展示最终结果。执行中（无实时输出）没有内容可展开。
    val expandable = !running && message.content.isNotBlank()
    var expanded by remember(message.id) { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expandable) Modifier.clickable { expanded = !expanded } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$icon ${message.toolName ?: "工具"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (streaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (expandable) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // 实时输出：流式过程中始终展示累积内容（为空时不占位）。
        if (streaming) {
            if (!liveOutput.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                SelectionContainer {
                    Text(
                        text = liveOutput,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else if (expandable && expanded) {
            Spacer(Modifier.height(Spacing.xs))
            SelectionContainer {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isBusy: Boolean,
    workspaceViewModel: WorkspaceViewModel?,
    activeProvider: AIProviderConfig?,
    onSelectModel: (String) -> Unit
) {
    val canSend = value.isNotBlank() && !isBusy
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 两行容器：上行输入框，下行操作图标 + 发送键，整体一个大圆角卡片。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(Radius.lg)
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp, max = 140.dp),
                placeholder = {
                    Text(
                        "输入消息与 AI 聊天…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                enabled = !isBusy,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )

            // 下行：左侧真实功能图标，右侧圆形发送键。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (workspaceViewModel != null) {
                    WorkspaceChip(viewModel = workspaceViewModel)
                    Spacer(Modifier.width(Spacing.sm))
                }
                if (activeProvider != null) {
                    ModelChip(provider = activeProvider, onSelectModel = onSelectModel)
                }
                Spacer(Modifier.weight(1f))
                SendButton(enabled = canSend, isBusy = isBusy, onClick = onSend)
            }
        }
    }
}

/**
 * 输入区下行的模型切换胶囊：显示当前模型名，点击弹出模型列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelChip(
    provider: AIProviderConfig,
    onSelectModel: (String) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { showSheet = true }
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(brandGradient)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = provider.effectiveModel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = "切换模型",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl)
            ) {
                Text(
                    text = "选择模型 · ${provider.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = Spacing.md)
                )
                if (provider.models.isEmpty()) {
                    Text(
                        "暂无模型，请到设置中拉取或添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.md)
                    )
                } else {
                    provider.models.forEach { model ->
                        val selected = model == provider.effectiveModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .clickable {
                                    onSelectModel(model)
                                    showSheet = false
                                }
                                .padding(horizontal = Spacing.md, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = model,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "当前",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, isBusy: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) {
        Modifier.background(brandGradient)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    }
    Box(
        modifier = Modifier
            .padding(Spacing.xs)
            .size(40.dp)
            .clip(CircleShape)
            .then(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = "发送",
                tint = if (enabled) androidx.compose.ui.graphics.Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusBanner(state: AgentUIState) {
    AnimatedVisibility(
        visible = state is AgentUIState.Error || state is AgentUIState.Applied,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        when (state) {
            is AgentUIState.Error -> InfoBanner(
                text = state.message,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                icon = Icons.Default.ErrorOutline
            )

            is AgentUIState.Applied -> InfoBanner(
                text = "代码变更已应用",
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = Icons.Default.Check
            )

            else -> {}
        }
    }
}

@Composable
private fun InfoBanner(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        color = container,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(text, color = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ChangePreviewPanel(
    changes: List<CodeChange>,
    onApply: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.md),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                "预览代码变更 · ${changes.size} 处",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.sm))

            LazyColumn(
                modifier = Modifier.heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(changes) { change -> ChangeItem(change) }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("拒绝")
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(brandGradient)
                        .clickable(onClick = onApply)
                        .padding(vertical = Spacing.sm + 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("应用", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeItem(change: CodeChange) {
    val accent = when (change.type) {
        ChangeType.CREATE -> MaterialTheme.colorScheme.tertiary
        ChangeType.DELETE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (change.type) {
                ChangeType.CREATE -> "+"
                ChangeType.DELETE -> "−"
                ChangeType.REPLACE -> "~"
                else -> "→"
            },
            modifier = Modifier.width(20.dp),
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${change.filePath.substringAfterLast('/')} · L${change.startLine}-${change.endLine}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
