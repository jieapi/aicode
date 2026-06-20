package com.aicodeeditor.feature.agent.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aicodeeditor.core.theme.Brand
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.core.ui.rememberImeBottomInset
import com.aicodeeditor.feature.agent.domain.model.CodeChange
import com.aicodeeditor.feature.agent.domain.model.ChangeType
import com.aicodeeditor.feature.agent.domain.permission.PermissionChoice
import com.aicodeeditor.feature.agent.domain.tool.PendingToolPermission
import com.aicodeeditor.feature.agent.presentation.AgentUIMessage
import com.aicodeeditor.feature.agent.presentation.AgentUIState
import com.aicodeeditor.feature.agent.presentation.AIAgentViewModel
import com.aicodeeditor.feature.agent.presentation.hasVisibleContent
import com.aicodeeditor.feature.agent.presentation.MessageRole
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.presentation.SettingsViewModel
import com.aicodeeditor.feature.workspace.presentation.WorkspaceViewModel
import com.aicodeeditor.feature.workspace.presentation.component.WorkspaceChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 差异视图配色：跨明暗主题固定的「新增绿 / 删除红」，带低透明度底色与高对比文字。
private val DiffAddBg = androidx.compose.ui.graphics.Color(0x3322C55E)
private val DiffAddText = androidx.compose.ui.graphics.Color(0xFF22C55E)
private val DiffRemoveBg = androidx.compose.ui.graphics.Color(0x33EF4444)
private val DiffRemoveText = androidx.compose.ui.graphics.Color(0xFFEF4444)

// 差异视图默认折叠阈值：单个 diff 超过该行数时默认收起，仅渲染前若干行，点页脚可展开/收起。
private const val DIFF_COLLAPSE_THRESHOLD = 20

private val brandGradient = Brush.linearGradient(listOf(Brand.Blue, Brand.Sky))

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
    val messagesState by viewModel.messagesState.collectAsState()
    val messages = messagesState.messages
    val changes by viewModel.changes.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    // 这批消息确属当前会话且已读完数据库时才算「就绪」；切换会话/冷启动加载期间为 false。
    val messagesReady = messagesState.loaded && messagesState.sessionId == currentSessionId
    val runningTool by viewModel.runningTool.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val streamingReasoning by viewModel.streamingReasoning.collectAsState()
    val pendingPermission by viewModel.pendingToolPermission.collectAsState()
    val activeProvider = settingsViewModel?.activeProvider?.collectAsState()?.value
    val currentWorkspace = workspaceViewModel?.current?.collectAsState()?.value
    // 工作区路径作为 AI 文件/命令操作的根目录；尚未就绪时为空串。
    val projectRoot = currentWorkspace?.path ?: ""

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val isBusy = agentState is AgentUIState.Loading || agentState is AgentUIState.Streaming

    val sendMessage: () -> Unit = {
        val text = inputText.trim()
        if (text.isNotEmpty() && !isBusy) {
            viewModel.executeAgentRequestStream(text, currentFile, selectedCode, projectRoot)
            inputText = ""
        }
    }

    // 自动滚动跟随。把视口「钉」在内容底部：只按末项超出视口的溢出量微调，绝不做
    // animateScrollToItem 那种「把目标项顶部对齐视口顶」的跳转——长消息比屏幕高时，顶部
    // 对齐会把正在生成的底部顶出屏幕（看似跳到气泡顶、滑不下去），且 trailing 气泡增删
    // 导致目标索引变化时会再猛跳一下。
    //
    // followBottom：是否处于「跟随底部」状态。流式增长时持续把底部带进视口；一旦用户主动
    // 上滑离开底部就停止跟随，让其自由阅读；用户重新滚回底部则恢复跟随。
    var positionedSession by remember { mutableStateOf<String?>(null) }
    var followBottom by remember { mutableStateOf(true) }

    // 「当前是否贴近底部」：末项可见且其底部基本落在视口内（留一点阈值容差）。
    val isAtBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true
            val lastIndex = layout.totalItemsCount - 1
            // 视口下沿。
            val viewportBottom = layout.viewportEndOffset
            lastVisible.index >= lastIndex &&
                (lastVisible.offset + lastVisible.size) <= viewportBottom + 4
        }
    }

    // 用户滚动意图。关键：只有「用户主动拖拽」才退出跟随——流式增长会让内容溢出、令 isAtBottom
    // 短暂变 false，若据此退出跟随会半路断开。故拖拽开始即停跟随；拖拽结束时若停在底部则恢复跟随。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> followBottom = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> followBottom = isAtBottom
            }
        }
    }

    LaunchedEffect(
        messagesReady,
        currentSessionId,
        messages.size,
        messages.lastOrNull()?.content,
        runningTool?.text,
        streamingText,
        streamingReasoning,
        pendingPermission,
        isBusy
    ) {
        // 尚未就绪（切换/冷启动加载中）时不滚动，避免对旧会话或空列表误操作。
        if (!messagesReady) return@LaunchedEffect
        // 底部可能额外存在临时气泡：思考气泡 / 流式文字气泡 /「思考中」占位气泡。
        // 判定需与渲染逻辑一致。
        val hasReasoning = streamingReasoning?.isNotEmpty() == true
        val hasStreamingText = streamingText?.hasVisibleContent() == true
        val hasTrailing = hasReasoning || hasStreamingText || (isBusy && runningTool == null && pendingPermission == null)
        val target = messages.size - 1 + (if (hasTrailing) 1 else 0)
        if (target < 0) {
            // 空会话也算已定位，后续首条消息走跟随逻辑。
            positionedSession = currentSessionId
            followBottom = true
            return@LaunchedEffect
        }
        if (positionedSession != currentSessionId) {
            // 切换会话 / 冷启动首次加载：瞬时定位到底部，不逐项动画。
            listState.scrollToItem(target)
            positionedSession = currentSessionId
            followBottom = true
        } else if (followBottom) {
            // 跟随状态下消息新增 / 流式增长：把末项底部带进视口。
            // 先确保末项已被布局到（超长跳跃时先 scrollToItem 再按溢出量微调），
            // 随后按「末项底部 − 视口下沿」的溢出量平滑滚动，不做顶部对齐式跳转。
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            if (lastVisible == null || lastVisible.index < target - 1) {
                // 末项还远在视口外（如刚切到长会话末尾追加）：先瞬时跳到目标项附近。
                listState.scrollToItem(target)
            }
            val overflow = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                (it.offset + it.size) - listState.layoutInfo.viewportEndOffset
            } ?: 0
            if (overflow > 0) listState.animateScrollBy(overflow.toFloat())
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
            // 状态栏由 ChatHeader、底部由输入栏各自消费 inset，这里清零避免双重内边距。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                ChatHeader(
                    workspaceViewModel = workspaceViewModel,
                    modelName = activeProvider?.effectiveModel,
                    onOpenDrawer = {
                        // 先收起输入法再展开侧边栏：避免键盘与抽屉同时占屏造成的跳变。
                        focusManager.clearFocus()
                        scope.launch { drawerState.open() }
                    },
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
                    if (!messagesReady) {
                        // 会话/历史尚未就绪：留空，避免先闪 Welcome 或上一个会话的消息再突然刷新。
                    } else if (messages.isEmpty()) {
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
                            // 底部临时气泡：
                            // 1) 模型正在吐思考 → 可折叠「思考」气泡；
                            // 2) 模型正在流式吐字 → 实时文字气泡（可与思考气泡同时存在）；
                            // 3) 二者皆无但仍在等待模型返回（无工具流式输出）→「思考中」占位气泡，
                            //    避免发送后聊天区一片空白、像没反应。
                            val reasoning = streamingReasoning
                            val streaming = streamingText
                            val showReasoning = reasoning != null && reasoning.isNotEmpty()
                            val showStreaming = streaming != null && streaming.hasVisibleContent()
                            if (showReasoning) {
                                item(key = "__reasoning__") { ReasoningBubble(text = reasoning!!) }
                            }
                            if (showStreaming) {
                                item(key = "__streaming__") { StreamingBubble(text = streaming!!) }
                            } else if (!showReasoning && isBusy && runningTool == null && pendingPermission == null) {
                                item(key = "__thinking__") { ThinkingBubble() }
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

                AnimatedVisibility(
                    visible = pendingPermission != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    pendingPermission?.let { request ->
                        ToolPermissionPanel(
                            request = request,
                            onChoice = { choice -> viewModel.resolveToolPermission(request.id, choice) }
                        )
                    }
                }

                // 输入栏（两行容器：上行输入，下行工作区/模型切换 + 发送）
                ChatInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = sendMessage,
                    onStop = { viewModel.stopAgent() },
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
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
            .clip(RoundedCornerShape(Radius.lg))
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
    val hasReasoning = message.role == MessageRole.ASSISTANT && !message.reasoning.isNullOrEmpty()
    val hasContent = message.content.hasVisibleContent()
    // 纯工具调用的 assistant 行（无可见文字、仅用于回放配对）通常不渲染气泡，避免空白「…」。
    // 但若该行携带思考过程，则仍要渲染出可折叠的「思考过程」气泡。
    // 用 hasVisibleContent 而非 isBlank：零宽空格/BOM 等不可见字符也算空，否则会漏出空气泡。
    if (message.role == MessageRole.ASSISTANT && !hasContent && !hasReasoning) return

    val isUser = message.role == MessageRole.USER

    // 助手行可能由「折叠的思考过程气泡」+「正式回复气泡」上下叠放组成。
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // 历史思考过程：默认折叠，点击展开。
        if (hasReasoning) {
            ReasoningBubble(text = message.reasoning!!, initiallyExpanded = false)
        }
        // 思考过程独占的 assistant 行（无正式文字）只显示上面的思考气泡，不再画空白回复气泡。
        if (hasContent || message.role != MessageRole.ASSISTANT) {
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
                        val textColor = when (message.role) {
                            MessageRole.USER -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        SelectionContainer {
                            MarkdownContent(
                                text = message.content.ifEmpty { "…" },
                                color = textColor,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 渲染助手文本里的 Markdown：当前聚焦「围栏代码块」（```lang ... ```）——
 * 解析为等宽、带底色、可横向滚动的代码卡片；块外文本按段落渲染，并把行内
 * `code` 着色为等宽高亮片段。没有任何代码块时退化为单段普通文本。
 * 解析按行进行，因此流式途中尚未闭合的代码块也能即时按代码样式呈现。
 */
@Composable
private fun MarkdownContent(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(Spacing.sm))
            when (block) {
                is MdBlock.Code -> CodeBlock(lang = block.lang, code = block.code)
                is MdBlock.Text -> Text(
                    text = renderInlineMarkdown(block.text),
                    color = color,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** 围栏代码块卡片：可选语言标签 + 等宽、可横向滚动的代码体。 */
@Composable
private fun CodeBlock(lang: String, code: String) {
    val mono = MaterialTheme.typography.bodySmall.copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(Radius.sm)
            )
    ) {
        if (lang.isNotBlank()) {
            Text(
                text = lang,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                modifier = Modifier.padding(
                    start = Spacing.sm,
                    end = Spacing.sm,
                    top = Spacing.xs
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = code,
                color = MaterialTheme.colorScheme.onSurface,
                style = mono,
                softWrap = false,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            )
        }
    }
}

/** Markdown 渲染块：纯文本段或围栏代码块。 */
private sealed interface MdBlock {
    data class Text(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
}

/**
 * 按行扫描，把文本切成「文本段」与「围栏代码块」。以 ``` 开头的行进入代码模式，
 * 直到下一条 ``` 行或文本结束（流式途中未闭合也按代码块收尾）。围栏行本身被丢弃，
 * 其后的标识符作为语言标签。相邻文本被合并为一段，纯空白段丢弃。
 */
private fun parseMarkdownBlocks(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = src.split("\n")
    val textBuf = StringBuilder()

    fun flushText() {
        val t = textBuf.toString().trim('\n')
        if (t.isNotBlank()) blocks.add(MdBlock.Text(t))
        textBuf.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            flushText()
            val lang = line.trimStart().removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (code.isNotEmpty()) code.append("\n")
                code.append(lines[i])
                i++
            }
            // 跳过闭合围栏行（若存在）。
            if (i < lines.size) i++
            blocks.add(MdBlock.Code(lang, code.toString()))
        } else {
            if (textBuf.isNotEmpty()) textBuf.append("\n")
            textBuf.append(line)
            i++
        }
    }
    flushText()
    // 整段无任何内容时（如纯占位「…」）至少回退一段，避免渲染出空气泡。
    if (blocks.isEmpty()) blocks.add(MdBlock.Text(src))
    return blocks
}

/** 把文本段里的行内 `code` 渲染成等宽高亮片段，其余原样保留。 */
@Composable
private fun renderInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    val codeColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    return remember(text, codeColor, codeBg) {
        androidx.compose.ui.text.buildAnnotatedString {
            val regex = Regex("`([^`\\n]+)`")
            var last = 0
            for (m in regex.findAll(text)) {
                append(text.substring(last, m.range.first))
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = codeColor,
                        background = codeBg,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                ) {
                    append(m.groupValues[1])
                }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}

/** 等待模型返回时的占位气泡：左对齐、与助手气泡同款，内含三个循环跳动的点。 */
@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.md, Radius.md, Radius.md, Radius.xs),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                TypingDots(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 模型流式吐字时的实时气泡：左对齐、与助手气泡同款。文字经打字机平滑处理后逐字浮现
 * （把网络分片的跳变抹平），尾部带三个跳动的点表示仍在生成。本轮结束后由落库的助手气泡接管。
 */
@Composable
private fun StreamingBubble(text: String) {
    // 打字机平滑：以稳定节奏「追赶」目标文本，把一次性到达的大块分片抹成逐字浮现。
    val display = rememberTypewriterText(text)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.md, Radius.md, Radius.md, Radius.xs),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp)) {
                MarkdownContent(
                    text = display,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.sm))
                TypingDots(color = MaterialTheme.colorScheme.primary, dotSize = 5.dp)
            }
        }
    }
}

/**
 * 思考过程可折叠气泡：左对齐、浅色弱化，与正式回复区分。点击标题栏折叠/展开。
 * [initiallyExpanded]：流式实时展示时默认展开以便边想边看；落库后的历史气泡默认折叠，避免刷屏。
 */
@Composable
private fun ReasoningBubble(text: String, initiallyExpanded: Boolean = true) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.md, Radius.md, Radius.md, Radius.xs),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "思考过程",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "折叠" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 打字机平滑：把会「跳变」的目标文本（流式分片经 StateFlow 合流后常常一次到达一大块）
 * 转成以稳定帧率逐字浮现的显示文本。落后越多步进越大（按比例追赶），既不会越拖越远，
 * 临近追平时又退化为逐字，呈现自然的打字手感。目标被清空/缩短（新一轮）时立即对齐。
 */
@Composable
private fun rememberTypewriterText(target: String): String {
    var visibleCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) {
        if (target.length < visibleCount) visibleCount = target.length
        while (visibleCount < target.length) {
            val remaining = target.length - visibleCount
            // 按比例追赶：远落后时快速逼近，临近时退化为逐字，末尾不会出现长尾延迟。
            val step = (remaining / 8).coerceIn(1, 40)
            visibleCount += step
            delay(16)
        }
    }
    return target.take(visibleCount.coerceAtMost(target.length))
}

/**
 * 三个循环跳动的点：通用「正在输入/生成」指示器，取代转圈 spinner。
 * 三点以固定相位差依次上下弹跳，形成波浪式律动。
 */
@Composable
private fun TypingDots(
    color: androidx.compose.ui.graphics.Color,
    dotSize: androidx.compose.ui.unit.Dp = 6.dp
) {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0f at 0
                        -5f at 180
                        0f at 360
                        0f at 900
                    },
                    repeatMode = RepeatMode.Restart,
                    // 每个点错开 150ms，形成依次弹跳的波浪。
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "dot-$index"
            )
            Box(
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color)
            )
            if (index < 2) Spacer(Modifier.width(4.dp))
        }
    }
}

/**
 * 工具消息：默认折叠为一行「状态圆点 + 工具名 + 参数摘要 + 箭头」，点击展开查看「指令」与「结果」。
 * 状态圆点仿 Claude Code：运行中=白点闪烁、成功=绿、失败=红（不再用 emoji）。
 * [liveOutput] 非空时进入「实时输出」模式：显示逐行累积输出。
 * 对 edit_file / write_file 这类带结构化差异的结果，展开后以「+新增/−删除」的彩色差异视图呈现。
 */
@Composable
private fun ToolMessageBody(message: AgentUIMessage, liveOutput: String? = null) {
    val streaming = liveOutput != null
    val running = streaming || message.content.startsWith("⏳")
    // edit_file / write_file 成功时尝试解析结构化差异，用于彩色 diff 渲染（运行中/出错时不解析）。
    // 两者结果同构（都带 hunks 数组），故共用 parseEditDiff。
    val edit = if (!running && !message.isError &&
        (message.toolName == "edit_file" || message.toolName == "write_file")
    ) {
        remember(message.id, message.content) { parseEditDiff(message.content) }
    } else null

    // 结果清洗：剥掉 Success(data=...) 外壳、还原真实换行；运行中尚无最终结果。
    val resultText = if (!running) {
        remember(message.id, message.content) { formatToolResult(message.content) }
    } else null
    // 标题行内联的参数摘要 + 展开区的完整参数。
    val argHint = remember(message.toolArgs) { toolArgHint(message.toolArgs) }
    val argsFull = remember(message.toolArgs) { formatToolArgs(message.toolArgs) }

    // 可展开：非运行态且有可展开内容（差异 / 结果 / 参数）。
    val expandable = !running && (edit != null || !resultText.isNullOrBlank() || !argsFull.isNullOrBlank())
    // 有差异的编辑默认展开，让变更一眼可见（与 Claude 一致）。
    var expanded by remember(message.id) { mutableStateOf(edit != null) }

    // 折叠标题：编辑结果显示文件名，其余显示工具名。
    val toolLabel = if (edit != null) edit.path.substringAfterLast('/') else (message.toolName ?: "工具")

    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expandable) Modifier.clickable { expanded = !expanded } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolStatusDot(running = running, isError = message.isError)
            Spacer(Modifier.width(Spacing.sm))
            // 工具名（醒目）+ 参数摘要（次要、单行省略），共占一行剩余宽度。
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = toolLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (edit == null && !argHint.isNullOrBlank()) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = argHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            // 编辑结果在标题行右侧附带「+N −M」增删统计。
            if (edit != null) {
                DiffStat(added = edit.added, removed = edit.removed)
                Spacer(Modifier.width(Spacing.sm))
            }
            if (streaming) {
                TypingDots(color = MaterialTheme.colorScheme.onSurfaceVariant, dotSize = 5.dp)
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
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }
            }
        } else if (expanded) {
            if (edit != null) {
                // 多处编辑逐个渲染；每个 hunk 之间留一点间距以便区分。
                edit.hunks.forEach { h ->
                    Spacer(Modifier.height(Spacing.xs))
                    DiffView(diff = h.diff, startLine = h.startLine)
                }
            } else {
                // 先「指令」（传入参数）后「结果」，两段都用等宽字体、真实换行。
                if (!argsFull.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.sm))
                    ToolSection(label = "指令", content = argsFull)
                }
                if (!resultText.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.sm))
                    ToolSection(label = "结果", content = resultText)
                }
            }
        }
    }
}

/**
 * 工具状态圆点（仿 Claude Code）：运行中=主题中性「白点」并循环闪烁，成功=绿，失败=红。
 * 「白点」用 onSurface 而非硬编码纯白，浅色主题下仍可见。
 */
@Composable
private fun ToolStatusDot(running: Boolean, isError: Boolean) {
    val baseColor = when {
        running -> MaterialTheme.colorScheme.onSurface
        isError -> DiffRemoveText
        else -> DiffAddText
    }
    val alpha = if (running) {
        val transition = rememberInfiniteTransition(label = "tool-status-dot")
        val a by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse),
            label = "tool-status-dot-alpha"
        )
        a
    } else 1f
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(baseColor.copy(alpha = alpha))
    )
}

/** 展开区的一段带小标题的内容块（如「指令」「结果」）：标题灰小字 + 等宽正文，真实换行。 */
@Composable
private fun ToolSection(label: String, content: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(2.dp))
    SelectionContainer {
        Text(
            text = content,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )
    }
}

/** 增删统计胶囊：绿色「+N」与红色「−M」。 */
@Composable
private fun DiffStat(added: Int, removed: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (added > 0) {
            Text(
                text = "+$added",
                color = DiffAddText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (added > 0 && removed > 0) Spacer(Modifier.width(Spacing.xs))
        if (removed > 0) {
            Text(
                text = "−$removed",
                color = DiffRemoveText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 彩色行级差异视图：每行左侧为行号槽，右侧按首列标记着色——
 * `+` 新增（绿底绿字）、`-` 删除（红底红字）、其余为上下文（灰字）。
 * 行号同时跟踪「原文件」与「结果文件」两套编号（均从 [startLine] 起）：删除行显示其
 * 在原文件中的行号，新增行显示其在结果文件中的行号，上下文两者一致。等宽字体、可横向滚动。
 */
@Composable
private fun DiffView(diff: String, startLine: Int) {
    val lines = remember(diff) { diff.split("\n") }
    // 差异过长时默认收起：只渲染前 DIFF_COLLAPSE_THRESHOLD 行，避免大段改动刷屏；点页脚展开全部 / 再收起。
    val collapsible = lines.size > DIFF_COLLAPSE_THRESHOLD
    var expanded by remember(diff) { mutableStateOf(false) }
    val visibleLines = if (collapsible && !expanded) lines.take(DIFF_COLLAPSE_THRESHOLD) else lines

    val mono = MaterialTheme.typography.bodySmall.copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    )
    // 行号槽宽度按最大行号位数估算，保证数字右对齐且不挤压内容。
    // 原文件号末值 = start + 上下文 + 删除；结果文件号末值 = start + 上下文 + 新增，取较大者。
    val removeCount = lines.count { it.startsWith("-") }
    val addCount = lines.count { it.startsWith("+") }
    val ctxCount = lines.size - removeCount - addCount
    val maxLineNo = startLine + ctxCount + maxOf(removeCount, addCount)
    val gutterChars = maxOf(2, maxLineNo.toString().length)
    val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Column(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.background)
                    .horizontalScroll(rememberScrollState())
            ) {
                var oldLineNo = startLine
                var newLineNo = startLine
                visibleLines.forEach { line ->
                    val marker = line.firstOrNull()
                    val (bg, fg) = when (marker) {
                        '+' -> DiffAddBg to DiffAddText
                        '-' -> DiffRemoveBg to DiffRemoveText
                        else -> androidx.compose.ui.graphics.Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    // 删除行用原文件行号，新增行用结果文件行号，上下文行两者同步推进。
                    val lineNo = when (marker) {
                        '-' -> oldLineNo++
                        '+' -> newLineNo++
                        else -> { val n = newLineNo; oldLineNo++; newLineNo++; n }
                    }
                    val gutter = lineNo.toString().padStart(gutterChars)
                    // 行号用浅灰、代码用差异色：单个全宽 Text 保留整行底色铺满。
                    val styled = androidx.compose.ui.text.buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(color = gutterColor)) {
                            append(gutter)
                            append("  ")
                        }
                        withStyle(androidx.compose.ui.text.SpanStyle(color = fg)) {
                            append(line.ifEmpty { " " })
                        }
                    }
                    Text(
                        text = styled,
                        style = mono,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = Spacing.sm, vertical = 1.dp)
                    )
                }
            }
        }
        if (collapsible) {
            DiffExpandToggle(
                expanded = expanded,
                hiddenCount = lines.size - DIFF_COLLAPSE_THRESHOLD,
                onToggle = { expanded = !expanded }
            )
        }
    }
}

/** 长差异的页脚切换：收起态显示「展开剩余 N 行」，展开态显示「收起」。 */
@Composable
private fun DiffExpandToggle(expanded: Boolean, hiddenCount: Int, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onToggle)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = if (expanded) "收起" else "展开剩余 $hiddenCount 行",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/** edit_file 单处编辑的差异片段。 */
private data class EditHunk(val startLine: Int, val diff: String)

/** edit_file 结果中解析出的结构化差异（可含多处编辑）。added/removed 为全部编辑的合计。 */
private data class EditDiff(
    val path: String,
    val added: Int,
    val removed: Int,
    val hunks: List<EditHunk>
)

/**
 * 从持久化的 TOOL 内容中解析 edit_file / write_file 的结构化差异（两者结果同构）。
 * 内容形如 `Success(data={...valid json...})`：取首个 `{` 到末个 `}` 即内嵌 JSON 对象。
 * 新格式带 hunks 数组（可多处编辑）；老格式只有顶层 diff/start_line（单处），两者都兼容。
 * 解析失败返回 null（退回原始文本渲染）。
 */
private fun parseEditDiff(content: String): EditDiff? {
    val start = content.indexOf('{')
    val end = content.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching {
        val obj = Json.parseToJsonElement(content.substring(start, end + 1)).jsonObject
        val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
        val added = obj["added_lines"]?.jsonPrimitive?.intOrNull ?: 0
        val removed = obj["removed_lines"]?.jsonPrimitive?.intOrNull ?: 0

        val hunks = obj["hunks"]?.jsonArray?.mapNotNull { el ->
            val ho = el.jsonObject
            val d = ho["diff"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            EditHunk(startLine = ho["start_line"]?.jsonPrimitive?.intOrNull ?: 1, diff = d)
        } ?: run {
            // 兼容旧格式：顶层单个 diff。
            val d = obj["diff"]?.jsonPrimitive?.contentOrNull ?: return null
            listOf(EditHunk(startLine = obj["start_line"]?.jsonPrimitive?.intOrNull ?: 1, diff = d))
        }
        if (hunks.isEmpty()) return null
        EditDiff(path = path, added = added, removed = removed, hunks = hunks)
    }.getOrNull()
}

/**
 * 把落库的原始工具结果（`ToolResult.toString()`，形如 `Success(data={...})` /
 * `Error(message=..., code=...)`）清洗成「只含结果本身」的可读文本：剥掉外壳，并把 JSON
 * 转义的 `\n` 还原成真正换行。解析失败时回退原文。运行中/停止等占位文案原样保留（去掉前缀符号）。
 */
private fun formatToolResult(raw: String): String {
    // 去掉占位用的状态符号前缀（⏳ 执行中 / ⏹ 已停止），避免卡片里残留 emoji。
    val s = raw.trim().removePrefix("⏹").removePrefix("⏳").trim()
    when {
        s.startsWith("Error(") -> {
            val msgIdx = s.indexOf("message=")
            if (msgIdx >= 0) {
                var body = s.substring(msgIdx + "message=".length)
                val codeIdx = body.lastIndexOf(", code=")
                body = if (codeIdx >= 0) body.substring(0, codeIdx) else body.removeSuffix(")")
                return body.trim()
            }
        }
        s.startsWith("Success(data=") -> {
            val inner = s.removePrefix("Success(data=").removeSuffix(")")
            return formatJsonData(inner) ?: inner.trim()
        }
        s.startsWith("Partial(data=") -> {
            var inner = s.removePrefix("Partial(data=")
            val msgIdx = inner.lastIndexOf(", message=")
            inner = if (msgIdx >= 0) inner.substring(0, msgIdx) else inner.removeSuffix(")")
            return formatJsonData(inner) ?: inner.trim()
        }
    }
    return s
}

/**
 * 把 `data=` 里的 JsonElement 文本渲染成可读结果：
 * 字符串 → 其内容（真实换行、无引号转义）；对象 → 优先取 content/output/stdout/text 主负载，
 * 否则列成 `key: value` 多行；其余类型回退原文。解析失败返回 null。
 */
private fun formatJsonData(jsonStr: String): String? = runCatching {
    when (val el = Json.parseToJsonElement(jsonStr.trim())) {
        is JsonPrimitive -> el.contentOrNull ?: el.toString()
        is JsonObject -> {
            val main = el["content"] ?: el["output"] ?: el["stdout"] ?: el["text"]
            val mainStr = (main as? JsonPrimitive)?.contentOrNull
            mainStr ?: el.entries.joinToString("\n") { (k, v) ->
                val vv = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
                "$k: $vv"
            }
        }
        else -> jsonStr.trim()
    }
}.getOrNull()

/** 把传入参数 JSON 列成 `key: value` 多行，供展开区「指令」段显示。解析失败回退原文。 */
private fun formatToolArgs(argsJson: String?): String? {
    if (argsJson.isNullOrBlank()) return null
    return runCatching {
        val obj = Json.parseToJsonElement(argsJson).jsonObject
        if (obj.isEmpty()) return null
        obj.entries.joinToString("\n") { (k, v) ->
            val vv = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
            "$k: $vv"
        }
    }.getOrNull() ?: argsJson.trim()
}

/** 标题行内联的参数摘要：优先取关键参数值（命令/路径/查询…），换行压成空格。 */
private fun toolArgHint(argsJson: String?): String? {
    if (argsJson.isNullOrBlank()) return null
    return runCatching {
        val obj = Json.parseToJsonElement(argsJson).jsonObject
        val preferred = listOf("command", "cmd", "path", "file_path", "file", "query", "pattern", "url", "name")
        val v = preferred.firstNotNullOfOrNull { obj[it] } ?: obj.values.firstOrNull()
        val str = (v as? JsonPrimitive)?.contentOrNull ?: v?.toString()
        str?.replace("\n", " ")?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isBusy: Boolean,
    workspaceViewModel: WorkspaceViewModel?,
    activeProvider: AIProviderConfig?,
    onSelectModel: (String) -> Unit
) {
    val canSend = value.isNotBlank() && !isBusy
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 两行容器：上行输入框，下行操作图标 + 发送键，整体一个大圆角卡片。
        // ime ∪ navigationBars：键盘弹出时浮于键盘之上，收起时浮于导航栏之上，
        // 外层 Surface 透明，底色由 Scaffold 背景延伸到屏幕底部，与系统栏无缝衔接。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = rememberImeBottomInset())
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
                SendButton(canSend = canSend, isBusy = isBusy, onSend = onSend, onStop = onStop)
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
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { showSheet = true }
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(MaterialTheme.colorScheme.primary)
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
private fun SendButton(canSend: Boolean, isBusy: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    // 运行中：圆形按钮变为「停止」，点击打断；空闲：发送箭头，仅在可发送时点亮。
    val clickable = isBusy || canSend
    val bg = if (clickable) {
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
            .clickable(enabled = clickable, onClick = if (isBusy) onStop else onSend),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "停止",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = "发送",
                tint = if (canSend) androidx.compose.ui.graphics.Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ToolPermissionPanel(
    request: PendingToolPermission,
    onChoice: (PermissionChoice) -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = request.toolName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            SelectionContainer {
                Column(
                    modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = request.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (request.details.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = request.details,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 「始终允许」的记忆说明：可记忆则展示将记住的命令前缀；不可记忆则提示仅可单次放行。
            val canRemember = request.rememberablePatterns.isNotEmpty()
            val rememberLabel = when {
                !canRemember -> "含命令替换/管道/重定向，无法记住，仅可单次放行"
                request.rememberablePatterns == listOf("*") -> "「始终允许」将在本项目记住：该工具的全部调用"
                else -> "「始终允许」将在本项目记住：" + request.rememberablePatterns.joinToString("、")
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = rememberLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(
                    onClick = { onChoice(PermissionChoice.REJECT) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("拒绝", color = MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = { onChoice(PermissionChoice.ONCE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("本次")
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.sm))
                        .then(
                            if (canRemember) Modifier.background(brandGradient)
                            else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        .clickable(enabled = canRemember) { onChoice(PermissionChoice.ALWAYS) }
                        .padding(vertical = Spacing.sm + 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "始终允许",
                        color = if (canRemember) androidx.compose.ui.graphics.Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
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
