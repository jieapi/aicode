package com.aicode.feature.agent.presentation.component

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.core.theme.Brand
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.domain.model.AgentImage
import com.aicode.feature.agent.domain.model.AgentMode
import com.aicode.feature.agent.domain.tool.question.UserQuestionAnswer
import com.aicode.feature.agent.presentation.AgentAttachment
import com.aicode.feature.agent.presentation.AgentUIState
import com.aicode.feature.agent.presentation.AIAgentViewModel
import com.aicode.feature.agent.presentation.hasVisibleContent
import com.aicode.feature.agent.presentation.MessageRole
import com.aicode.feature.settings.presentation.SettingsViewModel
import com.aicode.feature.workspace.presentation.WorkspaceViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Menu
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Star
import compose.icons.feathericons.Terminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

internal val brandGradient = Brush.linearGradient(listOf(Brand.Blue, Brand.Sky))
private const val REASONING_SCROLL_BUCKET_CHARS = 120

/**
 * 流式尾巴的三种状态，用于 [when] 分支分发。
 *
 * 早先用 [androidx.compose.animation.Crossfade] 做淡入，但 Crossfade 按 targetState
 * 缓存 content 子组合——流式期间 targetState 一直不变，文本增长时不会重新调用 content，
 * 导致 [StreamingBubble] 收不到后续文本、停在首句。故改用枚举 + 直接 [when] 分发。
 */
private enum class TailKind { THINKING, STREAMING, COMPACTING, NONE }

private data class AutoScrollSignal(
    val streamingTextLength: Int,
    val streamingReasoningBucket: Int,
    val runningToolMessageId: String?,
    val runningToolTextLength: Int,
    val isCompacting: Boolean,
    val messageCount: Int
)

private fun reasoningScrollBucket(text: String?): Int =
    text?.length?.let { (it / REASONING_SCROLL_BUCKET_CHARS) + 1 } ?: 0

private data class UploadedWorkspaceFile(
    val fileName: String,
    val containerPath: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val image: AgentImage? = null
)

internal data class PendingUploadAttachment(
    val fileName: String,
    val containerPath: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val image: AgentImage? = null,
)

internal val PendingUploadAttachment.isImage: Boolean
    get() = image != null

private fun List<PendingUploadAttachment>.toAttachmentText(): String {
    if (isEmpty()) return ""
    return buildString {
        append("附件：")
        this@toAttachmentText.forEach { attachment ->
            append('\n')
            append("- ")
            append(attachment.fileName)
            append("：")
            append(attachment.containerPath)
        }
    }
}

private fun appendAttachmentsToRequest(
    request: String,
    attachments: List<PendingUploadAttachment>
): String {
    val attachmentText = attachments.toAttachmentText()
    if (attachmentText.isBlank()) return request
    if (request.isBlank()) return attachmentText
    return request.trimEnd() + "\n\n" + attachmentText
}

private fun UploadedWorkspaceFile.toPendingAttachment(): PendingUploadAttachment =
    PendingUploadAttachment(
        fileName = fileName,
        containerPath = containerPath,
        localPath = localPath,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        image = image
    )

private fun PendingUploadAttachment.toAgentAttachment(): AgentAttachment =
    AgentAttachment(
        fileName = fileName,
        containerPath = containerPath,
        localPath = localPath,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        isImage = isImage
    )

private fun List<PendingUploadAttachment>.toAgentAttachments(): List<AgentAttachment> =
    map { it.toAgentAttachment() }

private fun PendingUploadAttachment.toAgentImage(): AgentImage? = image

private fun List<PendingUploadAttachment>.toAgentImages(): List<AgentImage> =
    mapNotNull { it.toAgentImage() }

private fun maxAttachmentMessage(max: Int): String = "最多可同时发送 $max 个附件"

private fun uploadSuccessMessage(count: Int): String = "已上传 $count 个附件"

private fun partialUploadMessage(count: Int): String = "已上传 $count 个附件，部分附件未加入"

private fun selectedAttachments(
    uris: List<Uri>,
    currentCount: Int
): List<Uri> = uris.take((MAX_PENDING_ATTACHMENTS - currentCount).coerceAtLeast(0))

private fun hasAttachmentSlots(currentCount: Int): Boolean =
    currentCount < MAX_PENDING_ATTACHMENTS

private fun remainingAttachmentSlots(currentCount: Int): Int =
    (MAX_PENDING_ATTACHMENTS - currentCount).coerceAtLeast(0)

private fun imageLimitError(): String =
    "图片超过 ${formatBytes(MAX_IMAGE_UPLOAD_BYTES)}，请压缩后重试"

private fun attachmentsRoot(workspace: File): File =
    File(File(workspace, ".aicode"), "attachments").apply { mkdirs() }

private fun workspaceContainerPath(relativePath: String): String =
    "/workspace/$relativePath"

private fun attachmentRelativePath(workspace: File, target: File): String =
    target.relativeTo(workspace).invariantSeparatorsPath

private fun pickedFileToastPath(path: String): String = "已上传到 $path"

private fun emptyWorkspaceMessage(): String = "请先选择工作区"

private fun unreadableFileMessage(): String = "无法读取所选文件"

private fun unavailableWorkspaceMessage(): String = "工作区不可用"

private fun uploadFallbackError(): String = "上传失败"

private fun unsupportedImageTypeError(): String = "无法识别图片类型，仅支持 jpg/png/gif/webp"

private fun uploadFileName(context: Context, uri: Uri): String =
    context.displayName(uri).ifBlank { "upload" }

private fun safeUploadFileName(context: Context, uri: Uri): String =
    sanitizeUploadFileName(uploadFileName(context, uri))

private fun imageMimeType(context: Context, uri: Uri, fileName: String): String =
    resolveImageMimeType(context, uri, fileName)

private fun fileMimeType(context: Context, uri: Uri, fileName: String): String =
    context.contentResolver.getType(uri)?.lowercase()
        ?: when (fileName.substringAfterLast('.', "").lowercase()) {
            "txt", "md", "kt", "java", "js", "ts", "json", "xml", "html", "css", "py", "sh", "gradle", "kts" -> "text/plain"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }

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
    val currentSession = sessions.find { it.id == currentSessionId }
    val sessionTitle = currentSession?.title?.takeIf { it.isNotBlank() } ?: "新会话"
    val sessionInputTokens = currentSession?.totalInputTokens ?: 0
    val sessionOutputTokens = currentSession?.totalOutputTokens ?: 0
    val messagesReady = messagesState.loaded && messagesState.sessionId == currentSessionId
    val runningTool by viewModel.runningTool.collectAsStateWithLifecycle()
    val isCompacting by viewModel.isCompacting.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoning by viewModel.streamingReasoning.collectAsStateWithLifecycle()
    val pendingPermission by viewModel.pendingToolPermission.collectAsStateWithLifecycle()
    val pendingQuestion by viewModel.pendingUserQuestion.collectAsStateWithLifecycle()
    val globalActiveProvider = settingsViewModel?.activeProvider?.collectAsStateWithLifecycle()?.value
    val providers = (settingsViewModel?.providers?.collectAsStateWithLifecycle()?.value ?: emptyList()).filter { it.isEnabled }
    val modelMetadata = settingsViewModel?.modelMetadata?.collectAsStateWithLifecycle()?.value.orEmpty()
    val sessionProviderModel by viewModel.currentSessionProviderModel.collectAsStateWithLifecycle()
    val activeProvider = run {
        val (boundProviderId, boundModel) = sessionProviderModel
        if (!boundProviderId.isNullOrBlank()) {
            // 与 workflow.resolveProviderConfig 保持一致：绑定 provider 须启用且已填 apiKey，否则回退全局
            providers.find { it.id == boundProviderId }?.takeIf { it.apiKey.isNotBlank() }?.let {
                if (!boundModel.isNullOrBlank()) it.copy(selectedModel = boundModel) else it
            } ?: globalActiveProvider
        } else {
            globalActiveProvider
        }
    }
    val currentWorkspace = workspaceViewModel?.current?.collectAsStateWithLifecycle()?.value
    val projectRoot = currentWorkspace?.path ?: ""
    val currentMode by viewModel.currentSessionMode.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val inputDraft by viewModel.inputDraft.collectAsStateWithLifecycle()
    LaunchedEffect(inputDraft) {
        if (inputText != inputDraft) inputText = inputDraft
    }
    var pendingAttachments by remember { mutableStateOf<List<PendingUploadAttachment>>(emptyList()) }
    val listState = rememberLazyListState()
    val markdownCache = remember { MarkdownRenderCache() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val isBusy = agentState is AgentUIState.Loading || agentState is AgentUIState.Streaming
    val activeModel = activeProvider?.effectiveModel.orEmpty()
    val activeModelMetadata = modelMetadata[activeModel]
    val canUploadFiles = projectRoot.isNotBlank() && activeModelMetadata?.supportsTools == true
    val canUploadImages = projectRoot.isNotBlank() && activeModelMetadata?.supportsVision == true

    LaunchedEffect(activeProvider?.type, activeModel) {
        val provider = activeProvider ?: return@LaunchedEffect
        if (activeModel.isNotBlank()) {
            settingsViewModel?.resolveModelMetadata(provider.type, listOf(activeModel))
        }
    }

    fun removePendingAttachment(index: Int) {
        pendingAttachments = pendingAttachments.filterIndexed { i, _ -> i != index }
    }

    fun handlePickedAttachments(uris: List<Uri>, images: Boolean) {
        if (uris.isEmpty()) return
        if (projectRoot.isBlank()) {
            Toast.makeText(context, emptyWorkspaceMessage(), Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasAttachmentSlots(pendingAttachments.size)) {
            Toast.makeText(context, maxAttachmentMessage(MAX_PENDING_ATTACHMENTS), Toast.LENGTH_SHORT).show()
            return
        }
        val selected = selectedAttachments(uris, pendingAttachments.size)
        scope.launch {
            var successCount = 0
            val failures = mutableListOf<String>()
            selected.forEach { uri ->
                runCatching {
                    copyUriToWorkspace(context, uri, projectRoot, includeImageData = images)
                }.onSuccess { uploaded ->
                    pendingAttachments = pendingAttachments + uploaded.toPendingAttachment()
                    successCount += 1
                }.onFailure { error ->
                    failures += (error.message ?: uploadFallbackError())
                }
            }

            when {
                successCount > 0 && failures.isEmpty() && uris.size <= remainingAttachmentSlots(pendingAttachments.size - successCount) ->
                    Toast.makeText(context, uploadSuccessMessage(successCount), Toast.LENGTH_SHORT).show()
                successCount > 0 ->
                    Toast.makeText(context, partialUploadMessage(successCount), Toast.LENGTH_LONG).show()
                failures.isNotEmpty() ->
                    Toast.makeText(context, failures.first(), Toast.LENGTH_LONG).show()
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        handlePickedAttachments(uris, images = false)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        handlePickedAttachments(uris, images = true)
    }

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
            streamingReasoningBucket = reasoningScrollBucket(streamingReasoning),
            runningToolMessageId = runningTool?.messageId,
            runningToolTextLength = runningTool?.text?.length ?: 0,
            isCompacting = isCompacting,
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
        if ((text.isNotEmpty() || pendingAttachments.isNotEmpty()) && !isBusy) {
            val attachments = pendingAttachments
            val modelRequest = appendAttachmentsToRequest(text, attachments)
            val images = attachments.toAgentImages()
            viewModel.executeAgentRequestStream(
                request = text,
                modelRequest = modelRequest,
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                inputImages = images,
                inputAttachments = attachments.toAgentAttachments()
            )
            inputText = ""
            viewModel.clearInputDraft()
            pendingAttachments = emptyList()
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
    // 注意：不能用 distinctUntilChanged() 包布尔谓词，只触发一次后去重，不再跟随（旧根因）。
    // 注意：不能删 __active__ item（让 totalItemsCount 突减），anchor clamp 上跳（旧根因）。
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatHeader(
                sessionTitle = sessionTitle,
                modelName = activeProvider?.effectiveModel,
                inputTokens = sessionInputTokens,
                outputTokens = sessionOutputTokens,
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
                            item(key = "__reasoning__", contentType = "tail") {
                                // 流式实时：短文本默认展开边想边看，过长（超 REASONING_COLLAPSE_LINE_LIMIT）时由气泡内部自动折叠，不刷屏
                                ReasoningBubble(text = reasoning!!, initiallyExpanded = true)
                            }
                        }
                        val streaming = streamingText
                        val showStreaming = streaming != null && streaming.hasVisibleContent()
                        val showThinking = !showReasoning && !showStreaming && !isCompacting && isBusy && runningTool == null && pendingPermission == null && pendingQuestion == null
                        val tailKind = when {
                            showStreaming -> TailKind.STREAMING
                            isCompacting -> TailKind.COMPACTING
                            showThinking -> TailKind.THINKING
                            else -> TailKind.NONE
                        }
                        // 尾巴气泡：永远挂载 item，NONE 时为空 Box（0 高度）。
                        // 注意：不能按 tailKind 增删 item；流结束时 __active__ 移除会让 totalItemsCount
                        // 突减，LazyColumn 把 firstVisibleItemIndex 向下 clamp → 视口上跳（旧症状2根因）。
                        // 永远挂载则 item 数量稳定，只在 StreamingBubble 和空 Box 间切换，
                        // anchor 不会被 clamp。流结束落库后跟随 effect 会把新消息贴底。
                        item(key = "__active__", contentType = "tail") {
                            when (tailKind) {
                                TailKind.THINKING -> ThinkingBubble()
                                TailKind.STREAMING -> StreamingBubble(text = streaming ?: "")
                                TailKind.COMPACTING -> CompactionProgressBubble()
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
                onValueChange = { inputText = it; viewModel.updateInputDraft(it) },
                onSend = sendMessage,
                onStop = { viewModel.stopAgent() },
                isBusy = isBusy,
                workspaceViewModel = workspaceViewModel,
                hasRunningSessions = { viewModel.hasRunningSessionsInCurrentWorkspace() },
                onSwitchWorkspaceConfirmed = { viewModel.stopAllAndCloseTerminal() },
                activeProvider = activeProvider,
                providers = providers,
                onSelectModel = { p, m ->
                    viewModel.setSessionProviderModel(p, m)
                    settingsViewModel?.applyModelGlobally(p, m)
                },
                onNavigateToSettings = onNavigateToSettings,
                currentMode = currentMode,
                onToggleMode = { viewModel.setSessionMode(it) },
                pendingAttachments = pendingAttachments,
                onRemoveAttachment = ::removePendingAttachment,
                canUploadFiles = canUploadFiles,
                canUploadImages = canUploadImages,
                onUploadFile = { filePicker.launch(arrayOf("*/*")) },
                onUploadImage = { imagePicker.launch(arrayOf("image/*")) },
                tokenProgress = run {
                    val contextLimit = activeModelMetadata?.contextTokens ?: 0
                    if (contextLimit > 0) {
                        (sessionInputTokens + sessionOutputTokens).toFloat() / contextLimit
                    } else 0f
                }
            )
        }
    }
}

private suspend fun copyUriToWorkspace(
    context: Context,
    uri: Uri,
    workspacePath: String,
    includeImageData: Boolean = false
): UploadedWorkspaceFile = withContext(Dispatchers.IO) {
    val workspace = File(workspacePath)
    require(workspace.isDirectory) { unavailableWorkspaceMessage() }

    val uploadsDir = attachmentsRoot(workspace)
    val target = uniqueUploadFile(uploadsDir, safeUploadFileName(context, uri))

    val input = context.contentResolver.openInputStream(uri) ?: error(unreadableFileMessage())
    input.use { source ->
        target.outputStream().use { output ->
            source.copyTo(output)
        }
    }

    val relativePath = attachmentRelativePath(workspace, target)
    val mimeType = if (includeImageData) {
        runCatching { imageMimeType(context, uri, target.name) }
            .getOrElse { error ->
                runCatching { target.delete() }
                throw error
            }
    } else {
        fileMimeType(context, uri, target.name)
    }
    val image = if (includeImageData) {
        if (target.length() > MAX_IMAGE_UPLOAD_BYTES) {
            runCatching { target.delete() }
            error(imageLimitError())
        }
        AgentImage(
            mimeType = mimeType,
            base64Data = Base64.getEncoder().encodeToString(target.readBytes()),
            path = workspaceContainerPath(relativePath)
        )
    } else {
        null
    }
    UploadedWorkspaceFile(
        fileName = target.name,
        containerPath = workspaceContainerPath(relativePath),
        localPath = target.absolutePath,
        mimeType = mimeType,
        sizeBytes = target.length(),
        image = image
    )
}

private fun Context.displayName(uri: Uri): String {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
    }.orEmpty()
}

private fun sanitizeUploadFileName(name: String): String {
    val cleaned = name
        .map { ch -> if (ch.code < 32 || ch in "\\/:*?\"<>|") '_' else ch }
        .joinToString("")
        .trim()
        .trim('.')
    return cleaned.ifBlank { "upload" }.take(160)
}

private fun uniqueUploadFile(dir: File, fileName: String): File {
    var candidate = File(dir, fileName)
    if (!candidate.exists()) return candidate

    val dotIndex = fileName.lastIndexOf('.')
    val stem = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
    val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""
    var index = 1
    while (candidate.exists()) {
        candidate = File(dir, "$stem-$index$extension")
        index += 1
    }
    return candidate
}

private fun resolveImageMimeType(context: Context, uri: Uri, fileName: String): String {
    val mime = context.contentResolver.getType(uri)?.lowercase()
    if (mime in SUPPORTED_IMAGE_MIME_TYPES) return mime!!

    val byExtension = when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> null
    }
    if (byExtension != null) return byExtension
    error(unsupportedImageTypeError())
}

private val SUPPORTED_IMAGE_MIME_TYPES = setOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp"
)

private const val MAX_IMAGE_UPLOAD_BYTES = 5L * 1024 * 1024
private const val MAX_PENDING_ATTACHMENTS = 8

internal fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        bytes >= mb -> String.format(java.util.Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(java.util.Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

@Composable
internal fun ChatHeader(
    sessionTitle: String,
    modelName: String?,
    inputTokens: Int,
    outputTokens: Int,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToGit: () -> Unit,
    currentMode: AgentMode,
    onToggleMode: (AgentMode) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sessionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = modelName?.takeIf { it.isNotBlank() } ?: "未选择模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onNewChat) {
                    Icon(
                        FeatherIcons.Plus,
                        contentDescription = "新建会话",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onNavigateToGit) {
                    Icon(
                        FeatherIcons.GitBranch,
                        contentDescription = "打开版本控制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onNavigateToTerminal) {
                    Icon(
                        FeatherIcons.Terminal,
                        contentDescription = "打开终端",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            tint = MaterialTheme.colorScheme.onPrimary,
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
