package com.aicode.feature.agent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.core.util.FileLogger
import com.aicode.core.util.toUserMessage
import com.aicode.feature.agent.data.local.dao.AgentMessageDao
import com.aicode.feature.agent.data.local.dao.ChatSessionDao
import com.aicode.feature.agent.data.CodeChangeTracker
import com.aicode.feature.agent.domain.container.ContainerInitState
import com.aicode.feature.agent.domain.container.LinuxContainerEngine
import com.aicode.feature.settings.domain.repository.AIProviderRepository
import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.model.AgentImage
import com.aicode.feature.agent.domain.model.AgentMessage
import com.aicode.feature.agent.domain.model.ChatSession
import com.aicode.feature.agent.domain.model.CodeChange
import com.aicode.feature.agent.domain.model.WorkflowStatus
import com.aicode.feature.agent.domain.permission.PermissionChoice
import com.aicode.feature.agent.domain.workflow.AgentWorkflow
import com.aicode.feature.terminal.domain.TerminalSessionManager
import com.aicode.feature.agent.domain.workflow.AgentEvent
import com.aicode.feature.agent.domain.tool.ToolPermissionManager
import com.aicode.feature.agent.domain.tool.ToolRegistry
import com.aicode.feature.agent.domain.tool.question.AskUserQuestionManager
import com.aicode.feature.agent.domain.tool.question.UserQuestionAnswer
import com.aicode.feature.agent.domain.session.SessionUseCase
import com.aicode.feature.agent.domain.session.MessagePersistenceUseCase
import com.aicode.feature.agent.domain.command.SlashCommandContext
import com.aicode.feature.agent.domain.command.SlashCommandRegistry
import com.aicode.feature.agent.domain.command.SlashCommandHandler
import com.aicode.feature.agent.presentation.AgentAttachment
import com.aicode.feature.agent.presentation.component.formatTokenCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AIAgentViewModel @Inject constructor(
    private val agentWorkflow: AgentWorkflow,
    private val toolRegistry: ToolRegistry,
    private val codeChangeTracker: CodeChangeTracker,
    private val agentMessageDao: AgentMessageDao,
    private val chatSessionDao: ChatSessionDao,
    private val aiProviderRepository: AIProviderRepository,
    private val toolPermissionManager: ToolPermissionManager,
    private val askUserQuestionManager: AskUserQuestionManager,
    private val containerEngine: LinuxContainerEngine,
    private val sessionUseCase: SessionUseCase,
    private val messagePersistenceUseCase: MessagePersistenceUseCase,
    private val planApprovalManager: com.aicode.feature.agent.domain.tool.mode.PlanApprovalManager,
    private val terminalSessionManager: com.aicode.feature.terminal.domain.TerminalSessionManager,
    private val slashCommandRegistry: SlashCommandRegistry
) : ViewModel(), SlashCommandContext {

    private val sessionJobs = mutableMapOf<String, Job>()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _agentStates = MutableStateFlow<Map<String, AgentUIState>>(emptyMap())
    val agentStates: StateFlow<Map<String, AgentUIState>> = _agentStates.asStateFlow()

    val agentState: StateFlow<AgentUIState> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(AgentUIState.Idle)
            else _agentStates.map { it[id] ?: AgentUIState.Idle }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AgentUIState.Idle)

    private fun setAgentState(sessionId: String, state: AgentUIState) {
        _agentStates.value = _agentStates.value + (sessionId to state)
    }

    private val _messageLimit = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val defaultLimit = 30

    private val _inputDraft = MutableStateFlow("")
    val inputDraft: StateFlow<String> = _inputDraft.asStateFlow()
    fun updateInputDraft(text: String) { _inputDraft.value = text }
    fun clearInputDraft() { _inputDraft.value = "" }

    fun loadMoreMessages() {
        val sid = _currentSessionId.value ?: return
        val currentLimit = _messageLimit.value[sid] ?: defaultLimit
        _messageLimit.value = _messageLimit.value + (sid to (currentLimit + 30))
    }

    /** 容器初始化实时进度（解压/部署/装包），AI 页底部气泡展示。 */
    val containerInit: StateFlow<ContainerInitState> = containerEngine.initProgress

    private val _currentWorkspace = MutableStateFlow<String>("")
    fun setWorkspace(path: String) {
        if (_currentWorkspace.value == path) return
        _currentWorkspace.value = path
    }

    val sessions: StateFlow<List<ChatSession>> = _currentWorkspace
        .flatMapLatest { path ->
            chatSessionDao.getAllSessionsByWorkspace(path)
                .map { list -> list.map { it.toDomain() } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentSessionMode: StateFlow<com.aicode.feature.agent.domain.model.AgentMode> = kotlinx.coroutines.flow.combine(
        _currentSessionId, sessions
    ) { id, list ->
        list.find { it.id == id }?.mode ?: com.aicode.feature.agent.domain.model.AgentMode.BUILD
    }.stateIn(viewModelScope, SharingStarted.Eagerly, com.aicode.feature.agent.domain.model.AgentMode.BUILD)

    /** 当前会话绑定的 providerId/model（null 表示未绑定，回退全局 active provider）。 */
    val currentSessionProviderModel: StateFlow<Pair<String?, String?>> = kotlinx.coroutines.flow.combine(
        _currentSessionId, sessions
    ) { id, list ->
        val s = list.find { it.id == id }
        (s?.providerId ?: "") to (s?.model ?: "")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null as String? to null as String?)

    /**
     * 当前会话的消息状态：会话切换时自动切换到对应历史，并携带所属会话 id 与 loaded 标志，
     * 使 UI 能区分「切换/冷启动加载中」与「空会话」——避免先闪 Welcome 或上一个会话的消息再突然刷新。
     * 过滤掉「纯工具调用」的空助手行（content 为空、仅用于回放配对，不应显示为气泡）。
     */
    val messagesState: StateFlow<ChatMessagesState> = kotlinx.coroutines.flow.combine(
        _currentSessionId,
        _messageLimit
    ) { id, limitMap -> id to (limitMap[id] ?: defaultLimit) }
        .flatMapLatest { (id, limit) ->
            if (id == null) flowOf(ChatMessagesState(null, emptyList(), loaded = false))
            else agentMessageDao.getMessagesBySessionPaged(id, limit).map { list ->
                ChatMessagesState(
                    sessionId = id,
                    messages = list.asSequence()
                        .filterNot { it.isContextSummary }
                        .filterNot {
                            it.role == MessageRole.ASSISTANT.name &&
                                !it.content.hasVisibleContent() &&
                                it.reasoning.isNullOrEmpty()
                        }
                        .map { entity -> entity.toUIMessage() }
                        .toList(),
                    loaded = true,
                    hasMore = list.size >= limit,
                    isLoadingMore = false
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatMessagesState(null, emptyList(), loaded = false))


    private val _changesMap = MutableStateFlow<Map<String, List<CodeChange>>>(emptyMap())
    val changes: StateFlow<List<CodeChange>> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else _changesMap.map { it[id] ?: emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun setChanges(sessionId: String, changes: List<CodeChange>) {
        _changesMap.value = if (changes.isEmpty()) _changesMap.value - sessionId else _changesMap.value + (sessionId to changes)
    }

    private val _runningTools = MutableStateFlow<Map<String, RunningToolOutput?>>(emptyMap())
    val runningTool: StateFlow<RunningToolOutput?> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else _runningTools.map { it[id] }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun setRunningTool(sessionId: String, tool: RunningToolOutput?) {
        _runningTools.value = if (tool == null) _runningTools.value - sessionId else _runningTools.value + (sessionId to tool)
    }

    private val _compactingSessions = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isCompacting: StateFlow<Boolean> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(false)
            else _compactingSessions.map { it[id] == true }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun setCompacting(sessionId: String, compacting: Boolean) {
        _compactingSessions.value = if (compacting) {
            _compactingSessions.value + (sessionId to true)
        } else {
            _compactingSessions.value - sessionId
        }
    }

    private val _streamingTexts = MutableStateFlow<Map<String, String?>>(emptyMap())
    val streamingText: StateFlow<String?> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else _streamingTexts.map { it[id] }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun setStreamingText(sessionId: String, text: String?) {
        _streamingTexts.value = if (text == null) _streamingTexts.value - sessionId else _streamingTexts.value + (sessionId to text)
    }

    private val _streamingReasonings = MutableStateFlow<Map<String, String?>>(emptyMap())
    val streamingReasoning: StateFlow<String?> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else _streamingReasonings.map { it[id] }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun setStreamingReasoning(sessionId: String, text: String?) {
        _streamingReasonings.value = if (text == null) _streamingReasonings.value - sessionId else _streamingReasonings.value + (sessionId to text)
    }

    /** 按 sessionId 维护的重试状态；流式恢复或结束后置 null。 */
    private val _retryStates = MutableStateFlow<Map<String, RetryState?>>(emptyMap())
    val retryState: StateFlow<RetryState?> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else _retryStates.map { it[id] }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun setRetryState(sessionId: String, state: RetryState?) {
        _retryStates.value = if (state == null) _retryStates.value - sessionId else _retryStates.value + (sessionId to state)
    }

    val pendingToolPermission = toolPermissionManager.pendingRequest

    val pendingUserQuestion = askUserQuestionManager.pendingQuestion

    private val _queuedRequests = MutableStateFlow<Map<String, List<QueuedRequest>>>(emptyMap())
    val queuedRequests: StateFlow<List<QueuedRequest>> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else _queuedRequests.map { it[id] ?: emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingPlanApproval: StateFlow<com.aicode.feature.agent.domain.tool.mode.PlanApprovalRequest?> = planApprovalManager.pendingApproval

    // 工具调用传入参数（argsPreview）按落库消息 id 暂存：ToolCallStarted 落库后，
    // ToolCallFinished / 用户停止会用同 id REPLACE 整行，需在此把参数带到后续落库。
    private val toolArgsByMsgId = mutableMapOf<String, String>()

    /** 是否有正在运行、可被打断的 agent 任务。 */
    val isRunning: Boolean get() {
        val sid = _currentSessionId.value ?: return false
        return sessionJobs[sid]?.isActive == true
    }

    fun hasRunningSessionsInCurrentWorkspace(): Boolean {
        return sessions.value.any { sessionJobs[it.id]?.isActive == true }
    }

    private companion object {
        const val TAG = "AIAgentViewModel"
        /** 用户主动停止时的收尾文案（追加在已输出内容之后，而非整体替换）。 */
        const val STOPPED_TOOL_TEXT = "已被用户停止"
    }

    init {
        viewModelScope.launch {
            // 冷启动收尾：上次进程被杀时若有工具正在执行，其占位行会永久显示「执行中」。
            // 这些工具不可能还在跑，统一回填为「已中断」。放在设置会话之前完成，使首帧不再闪转圈。
            sessionUseCase.initColdStartCleanup()

            _currentWorkspace.collectLatest { path ->
                val existing = sessionUseCase.getFirstSessionOfWorkspace(path)
                _currentSessionId.value = if (existing != null) {
                    existing.id // ORDER BY updatedAt DESC：最近一条
                } else {
                    val s = sessionUseCase.newSessionEntity(path)
                    sessionUseCase.upsertSession(s)
                    s.id
                }
            }
        }

        // 订阅后台命令完成事件：notify=true 的命令结束后自动注入消息对并触发 AI 新一轮
        viewModelScope.launch {
            terminalSessionManager.tabFinishedEvents.collect { event ->
                handleBackgroundCommandFinished(event)
            }
        }
    }

    /**
     * 后台命令（notify=true）结束后的回调：触发 Agent 新一轮，以一条后台任务完成通知（user 消息）
     * 作为本轮输入。
     *
     * 用 user 消息而非 assistant(tool_call) + tool_result 消息对：后者会与原 terminal 工具调用的
     * tool 结果在落库顺序上错位（后台回调异步触发，可能抢先于原 terminal 结果落库），导致 messages
     * 违反 OpenAI「assistant(tool_calls) → tool 结果紧跟」的配对约束，上游返回 400。user 消息无需与
     * 任何 tool_call 配对，天然不破坏顺序。通知文本带围栏说明，防止 AI 误判为用户的新指令或批准；
     * AI 据此用 terminal(read) 取回完整输出。
     *
     * 不自行 persist 通知、用 isAutoTrigger=false 走 enqueueAgentRequest 正常流程：由
     * executeAgentRequestStream 统一 persist 这条 user 消息，workflow 的 InitRequest 追加的同一条
     * UserMessage 即是它，避免重复落库或出现空占位消息。
     */
    private fun handleBackgroundCommandFinished(event: TerminalSessionManager.TabFinishedEvent) {
        // 按事件携带的来源会话路由，而非用户当前所在会话：后台命令可能在用户已切到别的会话后才结束。
        val sessionId = event.sourceSessionId ?: return
        viewModelScope.launch {
            val cmdDisplay = event.command?.takeIf { it.isNotBlank() }?.let { " \"$it\"" } ?: ""
            val status = if (event.exitCode == 0) "completed" else "failed"
            val notification = buildString {
                appendLine(BACKGROUND_NOTIFICATION_PREFIX)
                appendLine("这是一条后台任务完成事件，不是来自用户的消息。")
                appendLine("不要将其视为用户的确认、同意或对任何待处理问题的回答。")
                appendLine()
                appendLine("<task-notification>")
                appendLine("  <task-id>${event.tabId}</task-id>")
                appendLine("  <command>${event.command ?: ""}</command>")
                appendLine("  <exit-code>${event.exitCode}</exit-code>")
                appendLine("  <status>$status</status>")
                appendLine("  <summary>后台命令$cmdDisplay 已结束（退出码 ${event.exitCode}）</summary>")
                appendLine("</task-notification>")
                appendLine()
                append("可用 terminal(action=\"read\", tab_id=\"${event.tabId}\") 查看完整输出。")
            }
            enqueueAgentRequest(
                request = notification,
                projectRoot = _currentWorkspace.value,
                targetSessionId = sessionId
            )
        }
    }

    fun executeAgentRequest(
        request: String,
        modelRequest: String = request,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = "",
        inputImages: List<AgentImage> = emptyList(),
        inputAttachments: List<AgentAttachment> = emptyList(),
        targetSessionId: String? = null
    ): Job = viewModelScope.launch {
        val sessionId = targetSessionId ?: ensureSession()
        sessionJobs[sessionId] = coroutineContext[Job]!!
        setAgentState(sessionId, AgentUIState.Loading)

        try {
            val history = messagePersistenceUseCase.buildHistory(sessionId, SessionUseCase.PENDING_TOOL_MARKER)
            val isFirst = history.isEmpty()

            messagePersistenceUseCase.persist(sessionId, MessageRole.USER, request, attachments = inputAttachments)
            if (isFirst) sessionUseCase.updateTitle(sessionId, sessionUseCase.deriveTitle(request))
            sessionUseCase.touch(sessionId, messagePersistenceUseCase.nextTimestamp())

            val sessionEntity = sessionUseCase.getSessionById(sessionId)
            val mode = sessionEntity?.toDomain()?.mode ?: com.aicode.feature.agent.domain.model.AgentMode.BUILD

            val context = AgentContext(
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                language = currentFile?.let { detectLanguage(it) },
                history = history,
                inputImages = inputImages,
                sessionId = sessionId,
                mode = mode
            )

            val result = agentWorkflow.execute(
                userRequest = modelRequest,
                context = context,
                tools = toolRegistry.getAvailableTools(mode)
            )

            messagePersistenceUseCase.persist(sessionId, MessageRole.ASSISTANT, result.result)
            sessionUseCase.touch(sessionId, messagePersistenceUseCase.nextTimestamp())
            setChanges(sessionId, result.changes)

            setAgentState(sessionId, AgentUIState.Result(result.status))

            if (result.status != WorkflowStatus.SUCCESS && result.errors.isNotEmpty()) {
                setAgentState(sessionId, AgentUIState.Error(result.errors.joinToString("\n")))
            }

        } catch (e: CancellationException) {
            setAgentState(sessionId, AgentUIState.Idle)
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "executeAgentRequest 失败: request=$request", e)
            setAgentState(sessionId, AgentUIState.Error(e.toUserMessage()))
        } finally {
            if (sessionJobs[sessionId] == coroutineContext[Job]) {
                sessionJobs.remove(sessionId)
            }
        }
    }

    fun enqueueAgentRequest(
        request: String,
        modelRequest: String = request,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = "",
        inputImages: List<AgentImage> = emptyList(),
        inputAttachments: List<AgentAttachment> = emptyList(),
        isAutoTrigger: Boolean = false,
        targetSessionId: String? = null
    ) {
        val sid = targetSessionId ?: _currentSessionId.value
        val isCurrentRunning = sid != null && sessionJobs[sid]?.isActive == true
        if (isCurrentRunning) {
            val req = QueuedRequest(
                id = UUID.randomUUID().toString(),
                request = request,
                modelRequest = modelRequest,
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                inputImages = inputImages,
                inputAttachments = inputAttachments,
                isAutoTrigger = isAutoTrigger
            )
            val currentList = _queuedRequests.value[sid] ?: emptyList()
            _queuedRequests.value = _queuedRequests.value + (sid to (currentList + req))
        } else {
            executeAgentRequestStream(
                request = request,
                modelRequest = modelRequest,
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                inputImages = inputImages,
                inputAttachments = inputAttachments,
                targetSessionId = sid,
                isAutoTrigger = isAutoTrigger
            )
        }
    }

    private fun processNextInQueue(sessionId: String) {
        val queue = _queuedRequests.value[sessionId] ?: return
        val next = queue.firstOrNull() ?: return
        _queuedRequests.value = _queuedRequests.value + (sessionId to queue.drop(1))
        executeAgentRequestStream(
            request = next.request,
            modelRequest = next.modelRequest,
            currentFile = next.currentFile,
            selectedCode = next.selectedCode,
            projectRoot = next.projectRoot,
            inputImages = next.inputImages,
            inputAttachments = next.inputAttachments,
            targetSessionId = sessionId,
            isAutoTrigger = next.isAutoTrigger
        )
    }

    fun executeAgentRequestStream(
        request: String,
        modelRequest: String = request,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = "",
        inputImages: List<AgentImage> = emptyList(),
        inputAttachments: List<AgentAttachment> = emptyList(),
        targetSessionId: String? = null,
        isAutoTrigger: Boolean = false
    ): Job = viewModelScope.launch {
        val sessionId = targetSessionId ?: ensureSession()
        sessionJobs[sessionId] = coroutineContext[Job]!!
        setAgentState(sessionId, AgentUIState.Streaming)

        try {
            var failed = false
            // 必须在插入本次用户消息之前读取历史：workflow 会自己 add(userRequest)，避免重复。
            val history = messagePersistenceUseCase.buildHistory(sessionId, SessionUseCase.PENDING_TOOL_MARKER)
            val isFirst = history.isEmpty()

            if (!isAutoTrigger) {
                messagePersistenceUseCase.persist(sessionId, MessageRole.USER, request, attachments = inputAttachments)
                if (isFirst) sessionUseCase.updateTitle(sessionId, sessionUseCase.deriveTitle(request))
            }
            sessionUseCase.touch(sessionId, messagePersistenceUseCase.nextTimestamp())

            val sessionEntity = sessionUseCase.getSessionById(sessionId)
            val mode = sessionEntity?.toDomain()?.mode ?: com.aicode.feature.agent.domain.model.AgentMode.BUILD

            val context = AgentContext(
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                language = currentFile?.let { detectLanguage(it) },
                history = history,
                inputImages = inputImages,
                sessionId = sessionId,
                mode = mode
            )

            agentWorkflow.executeEvents(
                userRequest = modelRequest,
                context = context,
                tools = toolRegistry.getAvailableTools(mode)
            ).collect { event ->
                when (event) {
                    is AgentEvent.AssistantDelta -> {
                        setRetryState(sessionId, null)
                        setStreamingText(sessionId, event.accumulated)
                    }
                    is AgentEvent.ReasoningDelta -> {
                        setRetryState(sessionId, null)
                        setStreamingReasoning(sessionId, event.accumulated)
                    }
                    is AgentEvent.Retrying -> {
                        setRetryState(sessionId, RetryState(event.attempt, event.maxRetries))
                    }
                    is AgentEvent.CompactionStarted -> {
                        setRetryState(sessionId, null)
                        setStreamingText(sessionId, null)
                        setStreamingReasoning(sessionId, null)
                        setCompacting(sessionId, true)
                    }
                    AgentEvent.CompactionFinished -> {
                        setCompacting(sessionId, false)
                    }
                    is AgentEvent.AssistantText -> {
                        val normalized = if (event.content.hasVisibleContent()) event.content else ""
                        val reasoning = event.reasoning.takeIf { it.hasVisibleContent() }
                        messagePersistenceUseCase.persist(
                            sessionId,
                            MessageRole.ASSISTANT,
                            normalized,
                            toolCalls = event.toolCalls,
                            reasoning = reasoning,
                            inputTokens = event.inputTokens,
                            outputTokens = event.outputTokens
                        )
                        if (event.inputTokens > 0 || event.outputTokens > 0) {
                            viewModelScope.launch {
                                runCatching {
                                    chatSessionDao.addTokenUsage(sessionId, event.inputTokens, event.outputTokens)
                                    if (event.inputTokens > 0) {
                                        chatSessionDao.updateLastInputTokens(sessionId, event.inputTokens)
                                    }
                                }
                            }
                        }
                        setStreamingReasoning(sessionId, null)
                        setStreamingText(sessionId, normalized.ifEmpty { null })
                    }
                    is AgentEvent.ToolCallStarted -> {
                        val msgId = "tool_${event.id}"
                        setStreamingText(sessionId, null)
                        toolArgsByMsgId[msgId] = event.argsPreview
                        messagePersistenceUseCase.persist(
                            sessionId,
                            MessageRole.TOOL,
                            "${SessionUseCase.PENDING_TOOL_MARKER} ${event.toolName} 执行中…",
                            id = msgId,
                            toolCallId = event.id,
                            toolName = event.toolName,
                            toolArgs = event.argsPreview,
                            isError = false
                        )
                        setRunningTool(sessionId, RunningToolOutput(msgId, "", event.toolName, event.argsPreview))
                    }
                    is AgentEvent.ToolCallProgress -> {
                        setRunningTool(sessionId, RunningToolOutput(
                            "tool_${event.id}",
                            event.accumulated,
                            event.toolName,
                            toolArgsByMsgId["tool_${event.id}"] ?: ""
                        ))
                    }
                    is AgentEvent.ToolCallFinished -> {
                        val msgId = "tool_${event.id}"
                        messagePersistenceUseCase.persist(
                            sessionId,
                            MessageRole.TOOL,
                            event.result,
                            id = msgId,
                            toolCallId = event.id,
                            toolName = event.toolName,
                            toolArgs = event.argsPreview ?: toolArgsByMsgId[msgId],
                            isError = event.isError
                        )
                        toolArgsByMsgId.remove(msgId)
                        if (_runningTools.value[sessionId]?.messageId == msgId) {
                            setRunningTool(sessionId, null)
                        }
                    }
                    is AgentEvent.Failed -> {
                        failed = true
                        setCompacting(sessionId, false)
                        setAgentState(sessionId, AgentUIState.Error(event.error))
                    }
                    AgentEvent.Completed -> {
                        setRetryState(sessionId, null)
                        setCompacting(sessionId, false)
                    }
                    is AgentEvent.ModeChanged -> {
                        // 模式切换事件：PlanApprovalManager 已在 workflow 层面挂起等待用户批准
                        // 这里只更新 streamingText 显示
                    }
                }
            }

            sessionUseCase.touch(sessionId, messagePersistenceUseCase.nextTimestamp())
            if (!failed) {
                setAgentState(sessionId, AgentUIState.Result(WorkflowStatus.SUCCESS))
            }
            setStreamingText(sessionId, null)

        } catch (e: CancellationException) {
            setAgentState(sessionId, AgentUIState.Idle)
            throw e
        } catch (e: Exception) {
             FileLogger.e(TAG, "executeAgentRequestStream 失败: request=$request", e)
             setAgentState(sessionId, AgentUIState.Error(e.toUserMessage()))
        } finally {
            if (sessionJobs[sessionId] == coroutineContext[Job]) {
                sessionJobs.remove(sessionId)
            }
            setRunningTool(sessionId, null)
            setStreamingText(sessionId, null)
            setStreamingReasoning(sessionId, null)
            setCompacting(sessionId, false)
            setRetryState(sessionId, null)

            // Process the next queued request if any
            val currentState = _agentStates.value[sessionId]
            if (currentState !is AgentUIState.Loading && currentState !is AgentUIState.Streaming) {
                processNextInQueue(sessionId)
            }
        }
    }

    fun resolveToolPermission(id: String, choice: PermissionChoice) {
        toolPermissionManager.resolve(id, choice)
    }

    fun resolveUserQuestion(id: String, answer: UserQuestionAnswer) {
        askUserQuestionManager.resolve(id, answer)
    }

    /**
     * 主动打断正在运行的 agent：取消协程（会一并取消挂起的网络请求与容器命令进程），
     * 并把「执行中」的工具占位行收尾为「已停止」，避免悬挂的 spinner 与孤儿记录。
     */
    /** 停止当前工作区所有正在运行的 AI 会话并关闭所有终端标签（切换工作区前调用）。 */
    fun stopAllAndCloseTerminal() {
        stopAllAgents()
        terminalSessionManager.tabs.value.map { it.id }.forEach { terminalSessionManager.closeTab(it) }
    }

    /** 停止当前工作区所有正在运行的 AI 会话（切换工作区前调用）。 */
    fun stopAllAgents() {
        val jobs = sessionJobs.values.filter { it.isActive }
        jobs.forEach { it.cancel() }
        sessionJobs.clear()
        _agentStates.value = _agentStates.value.mapValues { AgentUIState.Idle }
        _streamingTexts.value = emptyMap()
        _streamingReasonings.value = emptyMap()
        _runningTools.value = emptyMap()
        _retryStates.value = emptyMap()
    }

    fun stopAgent() {
        val sessionId = _currentSessionId.value ?: return
        val job = sessionJobs[sessionId] ?: return
        if (!job.isActive) return
        val running = _runningTools.value[sessionId]
        val streamingText = _streamingTexts.value[sessionId]
        val streamingReasoning = _streamingReasonings.value[sessionId]
        job.cancel()
        setAgentState(sessionId, AgentUIState.Idle)
        setRunningTool(sessionId, null)
        setStreamingText(sessionId, null)
        setStreamingReasoning(sessionId, null)
        setCompacting(sessionId, false)
        setRetryState(sessionId, null)
        viewModelScope.launch {
            if (running != null) {
                val partial = running.text.trimEnd()
                val content = if (partial.isNotEmpty()) "$partial\n\n$STOPPED_TOOL_TEXT" else STOPPED_TOOL_TEXT
                messagePersistenceUseCase.persist(
                    sessionId = sessionId,
                    role = MessageRole.TOOL,
                    content = content,
                    id = running.messageId,
                    toolCallId = running.messageId.removePrefix("tool_"),
                    toolName = running.toolName.ifBlank { null },
                    toolArgs = running.toolArgs.ifBlank { toolArgsByMsgId[running.messageId] },
                    isError = true
                )
                toolArgsByMsgId.remove(running.messageId)
            } else if (!streamingText.isNullOrEmpty() || !streamingReasoning.isNullOrEmpty()) {
                val partial = (streamingText ?: "").trimEnd()
                val content = if (partial.isNotEmpty()) "$partial\n\n$STOPPED_TOOL_TEXT" else STOPPED_TOOL_TEXT
                val reasoning = streamingReasoning?.takeIf { it.hasVisibleContent() }
                messagePersistenceUseCase.persist(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = content,
                    reasoning = reasoning
                )
            }
            setRunningTool(sessionId, null)
            setStreamingText(sessionId, null)
            setStreamingReasoning(sessionId, null)
            setCompacting(sessionId, false)
            setRetryState(sessionId, null)
        }
    }

    // region 会话管理

    /** 新建会话；若当前会话还是空的则直接复用，避免堆积空会话。 */
    fun newSession() = viewModelScope.launch {
        val curId = _currentSessionId.value
        if (curId != null && sessionUseCase.isSessionEmpty(curId)) {
            setAgentState(curId, AgentUIState.Idle)
            setChanges(curId, emptyList())
            return@launch
        }
        val s = sessionUseCase.newSessionEntity(_currentWorkspace.value)
        sessionUseCase.upsertSession(s)
        _currentSessionId.value = s.id
    }

    fun setCurrentSessionId(id: String) {
        if (_currentSessionId.value == id) return
        _currentSessionId.value = id
    }

    fun setSessionMode(mode: com.aicode.feature.agent.domain.model.AgentMode) {
        val sid = _currentSessionId.value ?: return
        viewModelScope.launch {
            sessionUseCase.updateMode(sid, mode.name)
        }
    }

    fun setSessionProviderModel(providerId: String, model: String) {
        val sid = _currentSessionId.value ?: return
        viewModelScope.launch {
            sessionUseCase.updateProviderModel(sid, providerId, model)
        }
    }

    /** 暴露给 UI：发送时完全匹配查找斜杠命令。 */
    fun findSlashCommand(input: String) = slashCommandRegistry.findExact(input)

    /** 暴露给 UI：输入框下拉菜单展示的命令列表。 */
    val slashCommands: List<SlashCommandHandler> get() = slashCommandRegistry.all

    /** /status —— 以 Markdown 表格作为 AI 气泡输出当前会话状态。 */
    override fun showSessionStatus() {
        val sid = _currentSessionId.value ?: return
        val session = sessions.value.find { it.id == sid } ?: return
        val msgCount = messagesState.value.messages.size
        val model = session.model ?: sessionProviderModelDisplay(sid)
        val table = buildString {
            appendLine("| 项目 | 值 |")
            appendLine("|---|---|")
            appendLine("| 会话 | ${escapeMd(session.title)} |")
            appendLine("| 模型 | ${escapeMd(model)} |")
            appendLine("| 模式 | ${session.mode.name} |")
            appendLine("| 工作区 | ${escapeMd(session.workspacePath)} |")
            appendLine("| 消息数 | $msgCount |")
            appendLine("| 输入 tokens | ${formatTokenCount(session.totalInputTokens)} |")
            appendLine("| 输出 tokens | ${formatTokenCount(session.totalOutputTokens)} |")
        }
        viewModelScope.launch {
            sessionUseCase.touch(sid, messagePersistenceUseCase.nextTimestamp())
            messagePersistenceUseCase.persist(sid, MessageRole.ASSISTANT, table.trimEnd(), isCompacted = true)
        }
    }

    /** /compress —— 手动触发当前会话的上下文压缩。 */
    override fun compactCurrentSession() {
        val sid = _currentSessionId.value ?: return
        if (isRunning) return
        sessionJobs[sid]?.let { if (it.isActive) return }
        val job = viewModelScope.launch {
            setCompacting(sid, true)
            try {
                val changed = agentWorkflow.compactSession(sid) { event ->
                    when (event) {
                        is AgentEvent.CompactionStarted -> setCompacting(sid, true)
                        AgentEvent.CompactionFinished -> setCompacting(sid, false)
                        else -> {}
                    }
                }
                val resultText = if (changed) "上下文已压缩" else "当前上下文无需压缩（消息过少）"
                messagePersistenceUseCase.persist(
                    sessionId = sid,
                    role = MessageRole.ASSISTANT,
                    content = resultText
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FileLogger.e(TAG, "手动压缩失败: session=$sid", e)
                messagePersistenceUseCase.persist(
                    sessionId = sid,
                    role = MessageRole.ASSISTANT,
                    content = "压缩失败: ${e.message}"
                )
            } finally {
                setCompacting(sid, false)
            }
        }
        sessionJobs[sid] = job
    }

    private fun sessionProviderModelDisplay(sid: String): String {
        val pair = currentSessionProviderModel.value ?: return "未选择"
        val (_, model) = pair
        return model?.takeIf { it.isNotBlank() } ?: "未选择"
    }

    private fun escapeMd(text: String): String = text.replace("|", "\\|").replace("\n", " ")


    /** 用户批准计划，唤醒 workflow 继续在 BUILD 模式执行。 */
    fun approvePlanAndBuild() {
        planApprovalManager.resolve(com.aicode.feature.agent.domain.tool.mode.PlanApprovalChoice.APPROVE)
    }

    /** 用户选择继续反馈，唤醒 workflow 回滚到 PLAN 模式。 */
    fun refinePlan() {
        planApprovalManager.resolve(com.aicode.feature.agent.domain.tool.mode.PlanApprovalChoice.REFINE)
    }

    fun selectSession(id: String) {
        if (_currentSessionId.value == id) return
        _currentSessionId.value = id
    }

    fun deleteSession(id: String) = viewModelScope.launch {
        sessionUseCase.deleteSession(id)

        sessionJobs[id]?.cancel()
        sessionJobs.remove(id)
        _agentStates.value = _agentStates.value - id
        _streamingTexts.value = _streamingTexts.value - id
        _streamingReasonings.value = _streamingReasonings.value - id
        _runningTools.value = _runningTools.value - id
        _retryStates.value = _retryStates.value - id
        _changesMap.value = _changesMap.value - id
        _queuedRequests.value = _queuedRequests.value - id

        if (_currentSessionId.value == id) {
            val remaining = sessionUseCase.getFirstSessionOfWorkspace(_currentWorkspace.value)
            if (remaining != null) {
                _currentSessionId.value = remaining.id
            } else {
                val s = sessionUseCase.newSessionEntity(_currentWorkspace.value)
                sessionUseCase.upsertSession(s)
                _currentSessionId.value = s.id
            }
        }
    }

    private suspend fun ensureSession(): String {
        _currentSessionId.value?.let { return it }
        val existing = sessionUseCase.getFirstSessionOfWorkspace(_currentWorkspace.value)
        val id = if (existing != null) existing.id else {
            val s = sessionUseCase.newSessionEntity(_currentWorkspace.value)
            sessionUseCase.upsertSession(s)
            s.id
        }
        _currentSessionId.value = id
        return id
    }

    // endregion

    fun applyChanges(changes: List<CodeChange>) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                for (change in changes) {
                    when (change.type) {
                        com.aicode.feature.agent.domain.model.ChangeType.CREATE -> {
                            val file = File(change.filePath)
                            file.parentFile?.mkdirs()
                            file.writeText(change.newCode)
                        }

                        com.aicode.feature.agent.domain.model.ChangeType.REPLACE -> {
                            val file = File(change.filePath)
                            val lines = file.readLines().toMutableList()
                            val start = (change.startLine - 1).coerceIn(0, lines.size)
                            val end = (change.endLine - 1).coerceIn(0, lines.size - 1)

                            if (start <= end && start < lines.size) {
                                repeat(end - start + 1) {
                                    if (start < lines.size) lines.removeAt(start)
                                }
                                change.newCode.lines().reversed().forEach { line ->
                                    lines.add(start, line)
                                }
                                file.writeText(lines.joinToString("\n"))
                            }
                        }

                        com.aicode.feature.agent.domain.model.ChangeType.INSERT -> {
                            val file = File(change.filePath)
                            val lines = file.readLines().toMutableList()
                            val insertLine = (change.startLine - 1).coerceIn(0, lines.size)
                            change.newCode.lines().reversed().forEach { line ->
                                lines.add(insertLine, line)
                            }
                            file.writeText(lines.joinToString("\n"))
                        }

                        com.aicode.feature.agent.domain.model.ChangeType.DELETE -> {
                            val file = File(change.filePath)
                            val lines = file.readLines().toMutableList()
                            val start = (change.startLine - 1).coerceIn(0, lines.size)
                            val end = (change.endLine - 1).coerceIn(0, lines.size - 1)

                            for (i in end downTo start) {
                                if (i < lines.size) lines.removeAt(i)
                            }
                            file.writeText(lines.joinToString("\n"))
                        }

                        else -> {}
                    }
                }
            }
            val sessionId = _currentSessionId.value
            if (sessionId != null) {
                setAgentState(sessionId, AgentUIState.Applied)
                setChanges(sessionId, emptyList())
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "applyChanges 失败", e)
            val sessionId = _currentSessionId.value
            if (sessionId != null) {
                setAgentState(sessionId, AgentUIState.Error("应用更改失败: ${e.message}"))
            }
        }
    }

    fun rejectChanges() {
        val sessionId = _currentSessionId.value ?: return
        setAgentState(sessionId, AgentUIState.Idle)
        setChanges(sessionId, emptyList())
    }

    private fun detectLanguage(filePath: String): String {
        return when (filePath.substringAfterLast(".").lowercase()) {
            "kt", "kotlin" -> "kotlin"
            "java" -> "java"
            "dart" -> "dart"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "tsx" -> "typescript"
            "jsx" -> "javascript"
            "go" -> "go"
            "rs" -> "rust"
            else -> "text"
        }
    }
}
