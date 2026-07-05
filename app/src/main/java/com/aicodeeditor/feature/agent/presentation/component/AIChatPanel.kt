package com.aicodeeditor.feature.agent.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicodeeditor.core.theme.Brand
import com.aicodeeditor.core.theme.Radius
import com.aicodeeditor.core.theme.Spacing
import com.aicodeeditor.feature.agent.domain.model.AgentMode
import com.aicodeeditor.feature.agent.domain.tool.question.UserQuestionAnswer
import com.aicodeeditor.feature.agent.presentation.AgentUIState
import com.aicodeeditor.feature.agent.presentation.AIAgentViewModel
import com.aicodeeditor.feature.agent.presentation.hasVisibleContent
import com.aicodeeditor.feature.agent.presentation.MessageRole
import com.aicodeeditor.feature.settings.presentation.SettingsViewModel
import com.aicodeeditor.feature.workspace.presentation.WorkspaceViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Menu
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Star
import compose.icons.feathericons.Terminal
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal val brandGradient = Brush.linearGradient(listOf(Brand.Blue, Brand.Sky))

/**
 * 流式尾巴的三种状态，用于 [when] 分支分发。
 *
 * 早先用 [androidx.compose.animation.Crossfade] 做淡入，但 Crossfade 按 targetState
 * 缓存 content 子组合——流式期间 targetState 一直不变，文本增长时不会重新调用 content，
 * 导致 [StreamingBubble] 收不到后续文本、停在首句。故改用枚举 + 直接 [when] 分发。
 */
private enum class TailKind { THINKING, STREAMING, NONE }

private data class AutoScrollSignal(
    val streamingTextLength: Int,
    val streamingReasoningLength: Int,
    val runningToolMessageId: String?,
    val runningToolTextLength: Int,
    val messageCount: Int
)

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
    val messagesReady = messagesState.loaded && messagesState.sessionId == currentSessionId
    val runningTool by viewModel.runningTool.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoning by viewModel.streamingReasoning.collectAsStateWithLifecycle()
    val pendingPermission by viewModel.pendingToolPermission.collectAsStateWithLifecycle()
    val pendingQuestion by viewModel.pendingUserQuestion.collectAsStateWithLifecycle()
    val activeProvider = settingsViewModel?.activeProvider?.collectAsStateWithLifecycle()?.value
    val providers = (settingsViewModel?.providers?.collectAsStateWithLifecycle()?.value ?: emptyList()).filter { it.isEnabled }
    val currentWorkspace = workspaceViewModel?.current?.collectAsStateWithLifecycle()?.value
    val projectRoot = currentWorkspace?.path ?: ""
    val currentMode by viewModel.currentSessionMode.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val markdownCache = remember { MarkdownRenderCache() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val isBusy = agentState is AgentUIState.Loading || agentState is AgentUIState.Streaming

    // 自动滚动跟随
    var positionedSession by remember { mutableStateOf<String?>(null) }
    var followBottom by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf {
            if (!listState.canScrollForward) return@derivedStateOf true
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true
            val lastIndex = layout.totalItemsCount - 1
            val viewportBottom = layout.viewportEndOffset
            lastVisible.index >= lastIndex &&
                (lastVisible.offset + lastVisible.size) <= viewportBottom + 4
        }
    }

    val autoScrollSignal by rememberUpdatedState(
        AutoScrollSignal(
            streamingTextLength = streamingText?.length ?: 0,
            streamingReasoningLength = streamingReasoning?.length ?: 0,
            runningToolMessageId = runningTool?.messageId,
            runningToolTextLength = runningTool?.text?.length ?: 0,
            messageCount = messages.size
        )
    )

    // 用户开始拖拽：停止跟随。松手时若已到底则恢复跟随（旧逻辑）。
    // 额外：流式输出时内容持续增长，用户可能松手后又被「顶」离底部——
    // 用 snapshotFlow { isAtBottom } 持续监测，只要滑到底部就恢复跟随，
    // 满足「流式中滚到底部自动继续跟随」。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> followBottom = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> followBottom = isAtBottom
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { isAtBottom }.collect { atBottom ->
            if (atBottom) followBottom = true
        }
    }

    fun lastItemBottomOffset(): Int {
        val layout = listState.layoutInfo
        val lastIndex = layout.totalItemsCount - 1
        val viewportH = layout.viewportEndOffset - layout.viewportStartOffset
        val lastSize = layout.visibleItemsInfo.firstOrNull { it.index == lastIndex }?.size ?: 0
        return lastSize - viewportH
    }

    suspend fun ensureLastItemMeasured(lastIndex: Int) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) {
            listState.scrollToItem(lastIndex)
            withFrameNanos { }
        }
    }

    // 平滑贴底跟随。末条流式消息可能高于一屏，必须滚到末项底部；
    // 只 animateScrollToItem(lastIndex) 会把末项顶部对齐到视口顶部。
    val snapToBottom: suspend () -> Unit = {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            ensureLastItemMeasured(lastIndex)
            listState.animateScrollToItem(lastIndex, lastItemBottomOffset())
        }
    }

    // 瞬时贴底（用于发送消息、会话切换等需要立刻到位、不留动画的场景）。
    val snapToBottomInstant: suspend () -> Unit = {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            ensureLastItemMeasured(lastIndex)
            listState.scrollToItem(lastIndex, lastItemBottomOffset())
        }
    }

    val sendMessage: () -> Unit = {
        val text = inputText.trim()
        if (text.isNotEmpty() && !isBusy) {
            viewModel.executeAgentRequestStream(text, currentFile, selectedCode, projectRoot)
            inputText = ""
            followBottom = true
            scope.launch {
                kotlinx.coroutines.delay(0)
                snapToBottomInstant()
            }
        }
    }

    // 切换会话：把列表定位到最新一条，并恢复跟随。
    LaunchedEffect(currentSessionId, messagesReady) {
        if (!messagesReady) return@LaunchedEffect
        val target = messages.size - 1
        if (target < 0) {
            positionedSession = currentSessionId
            followBottom = true
            return@LaunchedEffect
        }
        if (positionedSession != currentSessionId) {
            snapToBottomInstant()
            positionedSession = currentSessionId
            followBottom = true
        }
    }

    // 流式贴底跟随（聊天标准做法）。
    // 监听 (流式文本长度, 消息条数) 元组：每个吐字 delta（length 变）和每次落库（size 变）
    // 都触发一次 animateScrollToItem —— collectLatest 自动取消上一个未跑完的动画，
    // 新动画立刻从当前位置平滑接管，视觉上形成连续流畅的底部跟随。
    // ⚠️ 不能用 distinctUntilChanged() 包布尔谓词——只触发一次后去重，不再跟随（旧根因）。
    // ⚠️ 不能删 __active__ item（让 totalItemsCount 突减）——anchor clamp 上跳（旧根因）。
    LaunchedEffect(listState, messagesReady) {
        if (!messagesReady) return@LaunchedEffect
        snapshotFlow { autoScrollSignal }.collectLatest {
            if (!followBottom) return@collectLatest
            // 等一帧让新文本/新落库消息完成测量，animateScrollToItem 读到正确布局。
            kotlinx.coroutines.delay(0)
            snapToBottom()
        }
    }

    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItemIndex, messagesReady, messagesState.hasMore, messagesState.isLoadingMore) {
        if (messagesReady && firstVisibleItemIndex <= 3 && messagesState.hasMore && !messagesState.isLoadingMore) {
            viewModel.loadMoreMessages()
        }
    }

    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatHeader(
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
                    // 会话/历史尚未就绪
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(messages, key = { it.id }, contentType = { it.role.name }) { message ->
                            val live = runningTool?.takeIf { it.messageId == message.id }?.text
                            AgentMessageItem(
                                message = message,
                                liveOutput = live,
                                markdownCache = markdownCache
                            )
                        }
                        val reasoning = streamingReasoning
                        val showReasoning = reasoning != null && reasoning.isNotEmpty()
                        if (showReasoning) {
                            item(key = "__reasoning__", contentType = "tail") { ReasoningBubble(text = reasoning!!) }
                        }
                        val streaming = streamingText
                        val showStreaming = streaming != null && streaming.hasVisibleContent()
                        val showThinking = !showReasoning && !showStreaming && isBusy && runningTool == null && pendingPermission == null && pendingQuestion == null
                        val tailKind = when {
                            showStreaming -> TailKind.STREAMING
                            showThinking -> TailKind.THINKING
                            else -> TailKind.NONE
                        }
                        // 尾巴气泡：永远挂载 item，NONE 时为空 Box（0 高度）。
                        // ⚠️ 不能按 tailKind 增删 item：流结束时 __active__ 移除会让 totalItemsCount
                        // 突减，LazyColumn 把 firstVisibleItemIndex 向下 clamp → 视口上跳（旧症状2根因）。
                        // 永远挂载则 item 数量稳定，只在内容（StreamingBubble↔空Box）间切换，
                        // anchor 不会被 clamp。流结束落库后跟随 effect 会把新消息贴底。
                        item(key = "__active__", contentType = "tail") {
                            when (tailKind) {
                                TailKind.THINKING -> ThinkingBubble()
                                TailKind.STREAMING -> StreamingBubble(text = streaming ?: "")
                                TailKind.NONE -> Box(Modifier)
                            }
                        }
                    }
                }
            }

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

            val planApproval by viewModel.pendingPlanApproval.collectAsStateWithLifecycle()
            AnimatedVisibility(
                visible = planApproval != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                planApproval?.let { state ->
                    PlanApprovalPanel(
                        state = state,
                        onApprove = { viewModel.approvePlanAndBuild() },
                        onRefine = { viewModel.refinePlan() }
                    )
                }
            }

            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = sendMessage,
                onStop = { viewModel.stopAgent() },
                isBusy = isBusy,
                workspaceViewModel = workspaceViewModel,
                hasRunningSessions = { viewModel.hasRunningSessionsInCurrentWorkspace() },
                activeProvider = activeProvider,
                providers = providers,
                onSelectModel = { p, m ->
                    val svm = settingsViewModel ?: return@ChatInputBar
                    if (p != activeProvider?.id) {
                        svm.setActiveProvider(p)
                    }
                    svm.selectModel(p, m)
                },
                onNavigateToSettings = onNavigateToSettings,
                currentMode = currentMode,
                onToggleMode = { viewModel.setSessionMode(it) }
            )
        }
    }
}

@Composable
internal fun ChatHeader(
    sessionTitle: String,
    modelName: String?,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToGit: () -> Unit,
    currentMode: AgentMode,
    onToggleMode: (AgentMode) -> Unit
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
internal fun BrandMark(size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
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
internal fun WelcomeState(modifier: Modifier = Modifier) {
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
