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

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.aicodeeditor.feature.agent.domain.tool.question.UserQuestionAnswer
import com.aicodeeditor.feature.agent.presentation.AgentUIMessage
import com.aicodeeditor.feature.agent.presentation.AgentUIState
import com.aicodeeditor.feature.agent.presentation.AIAgentViewModel
import com.aicodeeditor.feature.agent.presentation.hasVisibleContent
import com.aicodeeditor.feature.agent.presentation.MessageRole
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.presentation.SettingsViewModel
import com.aicodeeditor.feature.workspace.presentation.WorkspaceViewModel
import com.aicodeeditor.feature.workspace.presentation.component.WorkspaceChip
import com.aicodeeditor.feature.workspace.presentation.component.WorkspaceIconButton
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
import com.aicodeeditor.feature.agent.presentation.MdBlock
import com.aicodeeditor.feature.agent.presentation.parseMarkdownBlocks
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

private val DiffAddBg = Color(0x3322C55E)
private val DiffAddText = Color(0xFF22C55E)
private val DiffRemoveBg = Color(0x33EF4444)
private val DiffRemoveText = Color(0xFFEF4444)

private const val DIFF_COLLAPSE_THRESHOLD = 20
private const val TOOL_SECTION_LINE_LIMIT = 20

private val brandGradient = Brush.linearGradient(listOf(Brand.Blue, Brand.Sky))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatPanel(
    viewModel: AIAgentViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToGit: () -> Unit = {},
    settingsViewModel: SettingsViewModel? = null,
    workspaceViewModel: WorkspaceViewModel? = null,
    drawerState: DrawerState,
    currentFile: String? = null,
    selectedCode: String? = null,
    modifier: Modifier = Modifier
) {
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val messages = messagesState.messages
    val changes by viewModel.changes.collectAsStateWithLifecycle()

    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val sessionTitle = sessions.find { it.id == currentSessionId }?.title?.takeIf { it.isNotBlank() } ?: "新会话"
    // 这批消息确属当前会话且已读完数据库时才算「就绪」；切换会话/冷启动加载期间为 false。
    val messagesReady = messagesState.loaded && messagesState.sessionId == currentSessionId
    val runningTool by viewModel.runningTool.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoning by viewModel.streamingReasoning.collectAsStateWithLifecycle()
    val pendingPermission by viewModel.pendingToolPermission.collectAsStateWithLifecycle()
    val pendingQuestion by viewModel.pendingUserQuestion.collectAsStateWithLifecycle()
    val activeProvider = settingsViewModel?.activeProvider?.collectAsStateWithLifecycle()?.value
    val providers = (settingsViewModel?.providers?.collectAsStateWithLifecycle()?.value ?: emptyList()).filter { it.isEnabled }
    val currentWorkspace = workspaceViewModel?.current?.collectAsStateWithLifecycle()?.value
    // 工作区路径作为 AI 文件/命令操作的根目录；尚未就绪时为空串。
    val projectRoot = currentWorkspace?.path ?: ""
    val currentMode by viewModel.currentSessionMode.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

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
        pendingQuestion,
        isBusy
    ) {
        // 尚未就绪（切换/冷启动加载中）时不滚动，避免对旧会话或空列表误操作。
        if (!messagesReady) return@LaunchedEffect
        // 底部可能额外存在临时气泡：思考气泡 / 流式文字气泡 /「思考中」占位气泡。
        // 判定需与渲染逻辑一致。
        val hasReasoning = streamingReasoning?.isNotEmpty() == true
        val hasStreamingText = streamingText?.hasVisibleContent() == true
        val hasTrailing = hasReasoning || hasStreamingText || (isBusy && runningTool == null && pendingPermission == null && pendingQuestion == null)
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

    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItemIndex, messagesReady, messagesState.hasMore, messagesState.isLoadingMore) {
        if (messagesReady && firstVisibleItemIndex <= 3 && messagesState.hasMore && !messagesState.isLoadingMore) {
            viewModel.loadMoreMessages()
        }
    }

    // Drawer 已提升到 NavHost 外层（AppNavigation），这里只负责打开/关闭。
    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatHeader(
                workspaceViewModel = workspaceViewModel,
                sessionTitle = sessionTitle,
                modelName = activeProvider?.effectiveModel,
                onOpenDrawer = {
                    keyboardController?.hide()
                    scope.launch { drawerState.open() }
                },
                onNewChat = { viewModel.newSession() },
                onNavigateToTerminal = onNavigateToTerminal,
                onNavigateToGit = onNavigateToGit,
                currentMode = currentMode,
                onToggleMode = { viewModel.setSessionMode(it) }
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
                    // 会话/历史尚未就绪：留空
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
                            val live = runningTool?.takeIf { it.messageId == message.id }?.text
                            AgentMessageItem(message = message, liveOutput = live)
                        }
                        val reasoning = streamingReasoning
                        val streaming = streamingText
                        val showReasoning = reasoning != null && reasoning.isNotEmpty()
                        val showStreaming = streaming != null && streaming.hasVisibleContent()
                        if (showReasoning) {
                            item(key = "__reasoning__") { ReasoningBubble(text = reasoning!!) }
                        }
                        if (showStreaming) {
                            item(key = "__streaming__") { StreamingBubble(text = streaming!!) }
                        } else if (!showReasoning && isBusy && runningTool == null && pendingPermission == null && pendingQuestion == null) {
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

            AnimatedVisibility(
                visible = pendingQuestion != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                pendingQuestion?.let { question ->
                    AskUserQuestionPanel(
                        question = question,
                        onConfirm = { answer -> viewModel.resolveUserQuestion(question.id, answer) },
                        onSkip = { viewModel.resolveUserQuestion(question.id, UserQuestionAnswer(emptyList())) }
                    )
                }
            }

            // 输入栏
            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = sendMessage,
                onStop = { viewModel.stopAgent() },
                isBusy = isBusy,
                workspaceViewModel = workspaceViewModel,
                activeProvider = activeProvider,
                providers = providers,
                onSelectModel = { p, m -> settingsViewModel?.selectModel(p, m) },
                onNavigateToSettings = onNavigateToSettings,
                currentMode = currentMode,
                onToggleMode = { viewModel.setSessionMode(it) }
            )
        }
    }
}

@Composable
private fun ChatHeader(
    workspaceViewModel: WorkspaceViewModel?,
    sessionTitle: String,
    modelName: String?,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToGit: () -> Unit,
    currentMode: com.aicodeeditor.feature.agent.domain.model.AgentMode,
    onToggleMode: (com.aicodeeditor.feature.agent.domain.model.AgentMode) -> Unit
) {
    Surface(
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        FeatherIcons.Menu,
                        contentDescription = "打开侧边栏",
                        tint = Brand.IconGray)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sessionTitle,
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
                        FeatherIcons.Plus,
                        contentDescription = "新建会话",
                        tint = Brand.IconGray)
                }
                if (workspaceViewModel != null) {
                    WorkspaceIconButton(viewModel = workspaceViewModel)
                }
                IconButton(onClick = onNavigateToGit) {
                    Icon(
                        FeatherIcons.GitBranch,
                        contentDescription = "打开版本控制",
                        tint = Brand.IconGray)
                }
                IconButton(onClick = onNavigateToTerminal) {
                    Icon(
                        FeatherIcons.Terminal,
                        contentDescription = "打开终端",
                        tint = Brand.IconGray)
                }
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
            FeatherIcons.Star,
            contentDescription = null,
            tint = Brand.IconGray,
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
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp),
                                preParsedBlocks = message.parsedBlocks.takeIf { it.isNotEmpty() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 使用 multiplatform-markdown-renderer-m3 库渲染 Markdown。
 */
@Composable
private fun MarkdownContent(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    preParsedBlocks: List<MdBlock>? = null
) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides color
    ) {
        com.mikepenz.markdown.m3.Markdown(
            content = text,
            modifier = modifier
        )
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
                        FeatherIcons.Star,
                        contentDescription = null,
                        tint = Brand.IconGray,
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
                        if (expanded) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                        contentDescription = if (expanded) "折叠" else "展开",
                        tint = Brand.IconGray,
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
    color: Color,
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
        (message.toolName == "editFile" || message.toolName == "writeFile")
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

    // todo 工具的待办数据（提取到顶层供标题行和展开区共用）
    val todoData = if (message.toolName == "todo" && !running && !message.isError) {
        remember(message.id, message.content) { parseTodoResult(message.content) }
    } else null

    // 可展开：非运行态且有可展开内容（差异 / 待办卡片 / 结果 / 参数）。
    val expandable = !running && (edit != null || !resultText.isNullOrBlank() || !argsFull.isNullOrBlank()
            || (todoData != null && todoData.items.isNotEmpty()))
    // 有差异的编辑或待办列表默认展开，让变更/进度一眼可见。
    var expanded by remember(message.id) { mutableStateOf(edit != null || todoData != null) }

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
            // todo 工具在标题行右侧附带进度统计 "N/M"。
            if (todoData != null && todoData.total > 0) {
                Text(
                    text = "${todoData.completed}/${todoData.total}",
                    color = if (todoData.completed == todoData.total) DiffAddText
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            if (streaming) {
                TypingDots(color = MaterialTheme.colorScheme.onSurfaceVariant, dotSize = 5.dp)
            } else if (expandable) {
                Icon(
                    if (expanded) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = Brand.IconGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // 实时输出：流式过程中始终展示累积内容（为空时不占位）；过长时只保留最新20行。
        if (streaming) {
            if (!liveOutput.isNullOrBlank()) {
                val liveLines = liveOutput.split("\n")
                val truncated = if (liveLines.size > TOOL_SECTION_LINE_LIMIT) {
                    liveLines.takeLast(TOOL_SECTION_LINE_LIMIT).joinToString("\n")
                } else liveOutput
                Spacer(Modifier.height(Spacing.xs))
                SelectionContainer {
                    Text(
                        text = truncated,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }
            }
        } else if (expanded) {
            if (todoData != null && todoData.items.isNotEmpty()) {
                // todo 工具使用结构化卡片渲染待办列表
                Spacer(Modifier.height(Spacing.sm))
                TodoCard(items = todoData.items)
            } else if (edit != null) {
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

/** 展开区的一段带小标题的内容块（如「指令」「结果」）：标题灰小字 + 等宽正文。行数超过 [TOOL_SECTION_LINE_LIMIT] 时默认只显示最新20行，底部提供折叠按钮。 */
@Composable
private fun ToolSection(label: String, content: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(2.dp))

    val lines = remember(content) { content.split("\n") }
    val collapsible = lines.size > TOOL_SECTION_LINE_LIMIT
    var expanded by remember(content) { mutableStateOf(false) }
    // 截断策略：保留末尾20行（最新输出）
    val visibleLines = if (collapsible && !expanded) lines.takeLast(TOOL_SECTION_LINE_LIMIT) else lines
    val hiddenCount = lines.size - TOOL_SECTION_LINE_LIMIT

    SelectionContainer {
        Text(
            text = visibleLines.joinToString("\n"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )
    }

    if (collapsible) {
        DiffExpandToggle(
            expanded = expanded,
            hiddenCount = hiddenCount,
            onToggle = { expanded = !expanded }
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
                        else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
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
            if (expanded) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
            contentDescription = if (expanded) "收起" else "展开",
            tint = Brand.IconGray,
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

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isBusy: Boolean,
    workspaceViewModel: WorkspaceViewModel?,
    activeProvider: AIProviderConfig?,
    providers: List<AIProviderConfig>,
    onSelectModel: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    currentMode: com.aicodeeditor.feature.agent.domain.model.AgentMode,
    onToggleMode: (com.aicodeeditor.feature.agent.domain.model.AgentMode) -> Unit
) {
    val canSend = value.isNotBlank() && !isBusy
    Surface(
        color = Color.Transparent,
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
                .background(Color.White)
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
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            // 下行：左侧真实功能图标，右侧圆形发送键。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isPlan = currentMode == com.aicodeeditor.feature.agent.domain.model.AgentMode.PLAN
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPlan) Color(0xFF8B5CF6) else Color(0xFF10B981),
                        modifier = Modifier
                            .clickable { 
                                val nextMode = if (isPlan) com.aicodeeditor.feature.agent.domain.model.AgentMode.BUILD else com.aicodeeditor.feature.agent.domain.model.AgentMode.PLAN
                                onToggleMode(nextMode)
                            }
                    ) {
                        Text(
                            text = if (isPlan) "PLAN" else "BUILD",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    
                    if (activeProvider != null) {
                        ModelChip(
                            provider = activeProvider,
                            providers = providers,
                            onSelectModel = onSelectModel,
                            onClick = onNavigateToSettings
                        )
                    }
                }
                SendButton(canSend = canSend, isBusy = isBusy, onSend = onSend, onStop = onStop)
            }
        }
    }
}

/**
 * 输入区下行的模型切换胶囊：显示当前模型名，点击导航到设置页。
 */
@Composable
private fun ModelChip(
    provider: AIProviderConfig,
    providers: List<AIProviderConfig>,
    onSelectModel: (String, String) -> Unit,
    onClick: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = { showSheet = true })
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
            FeatherIcons.MoreHorizontal,
            contentDescription = "切换模型",
            tint = Brand.IconGray,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showSheet) {
        ModelSheet(
            providers = providers,
            currentProviderId = provider.id,
            currentModel = provider.effectiveModel,
            onSelect = { pId, model ->
                onSelectModel(pId, model)
                showSheet = false
            },
            onManage = {
                onClick()
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(
    providers: List<AIProviderConfig>,
    currentProviderId: String,
    currentModel: String,
    onSelect: (String, String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模型",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManage) {
                    Icon(FeatherIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("管理")
                }
            }

            if (providers.all { it.models.isEmpty() }) {
                Text(
                    "暂无可用模型，请在「管理」中添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    providers.forEach { p ->
                        if (p.models.isNotEmpty()) {
                            item(key = "header_${p.id}") {
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs, start = Spacing.xs)
                                )
                            }
                            items(p.models, key = { "${p.id}_$it" }) { model ->
                                val selected = p.id == currentProviderId && model == currentModel
                                ModelRow(
                                    name = model,
                                    selected = selected,
                                    onClick = { onSelect(p.id, model) }
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
private fun ModelRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                FeatherIcons.Check,
                contentDescription = "当前",
                tint = Brand.IconGray,
                modifier = Modifier.size(20.dp)
            )
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
                FeatherIcons.Square,
                contentDescription = "停止",
                tint = Brand.IconGray,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                FeatherIcons.ArrowUp,
                contentDescription = "发送",
                tint = Brand.IconGray,
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
                !canRemember -> request.rememberDisabledReason ?: "含命令替换/管道/重定向，无法记住，仅可单次放行"
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
                        color = if (canRemember) Color.White
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
                icon = FeatherIcons.AlertCircle
            )

            is AgentUIState.Applied -> InfoBanner(
                text = "代码变更已应用",
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = FeatherIcons.Check
            )

            else -> {}
        }
    }
}

@Composable
private fun InfoBanner(
    text: String,
    container: Color,
    content: Color,
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
            Icon(icon, contentDescription = null, tint = Brand.IconGray, modifier = Modifier.size(18.dp))
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
                    Icon(FeatherIcons.X, contentDescription = null, modifier = Modifier.size(18.dp))
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
                            FeatherIcons.Check,
                            contentDescription = null,
                            tint = Brand.IconGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("应用", color = Color.White, fontWeight = FontWeight.Medium)
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

// ── Todo 工具结构化卡片渲染 ──────────────────────────────────────────

/** 解析后的待办列表快照 */
private data class ParsedTodoResult(
    val total: Int,
    val completed: Int,
    val items: List<ParsedTodoItem>
)

private data class ParsedTodoItem(
    val id: String,
    val subject: String,
    val description: String,
    val status: String,  // pending / in_progress / completed
    val priority: Int,
    val order: Int
)

/** 从工具结果的 JSON 文本中解析出待办列表 */
private fun parseTodoResult(content: String): ParsedTodoResult? {
    return try {
        // 先剥掉 Success(data=...) / Error(...) 外壳
        val s = content.trim().removePrefix("⏹").removePrefix("⏳").trim()
        val jsonStr = when {
            s.startsWith("Success(data=") -> s.removePrefix("Success(data=").removeSuffix(")")
            s.startsWith("Partial(data=") -> {
                val inner = s.removePrefix("Partial(data=")
                val msgIdx = inner.lastIndexOf(", message=")
                if (msgIdx >= 0) inner.substring(0, msgIdx) else inner.removeSuffix(")")
            }
            else -> s
        }
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val element = json.parseToJsonElement(jsonStr)
        val obj = element as? kotlinx.serialization.json.JsonObject ?: return null
        val total = obj["total"]?.toString()?.toIntOrNull() ?: return null
        val completed = obj["completed"]?.toString()?.toIntOrNull() ?: 0
        val itemsArr = obj["items"]?.jsonArray ?: return null
        val items = itemsArr.mapNotNull { item ->
            val itemObj = item?.jsonObject ?: return@mapNotNull null
            ParsedTodoItem(
                id = itemObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                subject = itemObj["subject"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                description = itemObj["description"]?.jsonPrimitive?.content ?: "",
                status = itemObj["status"]?.jsonPrimitive?.content ?: "pending",
                priority = itemObj["priority"]?.jsonPrimitive?.intOrNull ?: 0,
                order = itemObj["order"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }.sortedBy { it.order }
        ParsedTodoResult(total = total, completed = completed, items = items)
    } catch (_: Exception) {
        // 解析失败时静默返回 null，UI 会 fallback 到普通文本
        null
    }
}

/** 待办列表结构化卡片 */
@Composable
private fun TodoCard(items: List<ParsedTodoItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            TodoItemRow(item = item)
            if (item != items.last()) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

/** 单条待办项行：状态图标 + 标题 + 状态标签 */
@Composable
private fun TodoItemRow(item: ParsedTodoItem) {
    val isCompleted = item.status == "completed"
    val isInProgress = item.status == "in_progress"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态图标
        when {
            isCompleted -> {
                // 绿色勾
                Icon(
                    imageVector = FeatherIcons.Check,
                    contentDescription = "已完成",
                    tint = DiffAddText,
                    modifier = Modifier.size(16.dp)
                )
            }
            isInProgress -> {
                // 闪烁圆点（复用 ToolStatusDot 的动画逻辑）
                val transition = rememberInfiniteTransition(label = "todo-progress-dot")
                val alpha by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(650),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "todo-progress-dot-alpha"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                )
            }
            else -> {
                // 空心圆（pending）
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        // 标题
        Text(
            text = item.subject,
            color = if (isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        // 进行中标签
        if (isInProgress) {
            Spacer(Modifier.width(Spacing.xs))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "进行中",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
    // 描述行（如果有）
    if (item.description.isNotBlank() && !isCompleted) {
        Spacer(Modifier.height(2.dp))
        Row {
            Spacer(Modifier.width(24.dp))  // 对齐标题（图标+间距）
            Text(
                text = item.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
