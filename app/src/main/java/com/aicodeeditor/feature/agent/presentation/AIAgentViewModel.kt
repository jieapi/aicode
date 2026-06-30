package com.aicodeeditor.feature.agent.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.core.util.toUserMessage
import com.aicodeeditor.feature.agent.data.local.dao.AgentMessageDao
import com.aicodeeditor.feature.agent.data.local.dao.ChatSessionDao
import com.aicodeeditor.feature.agent.data.local.entity.AgentMessageEntity
import com.aicodeeditor.feature.agent.data.local.entity.ChatSessionEntity
import com.aicodeeditor.feature.agent.data.CodeChangeTracker
import com.aicodeeditor.feature.agent.domain.container.ContainerInitState
import com.aicodeeditor.feature.agent.domain.container.LinuxContainerEngine
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.ChatSession
import com.aicodeeditor.feature.agent.domain.model.CodeChange
import com.aicodeeditor.feature.agent.domain.model.WorkflowStatus
import com.aicodeeditor.feature.agent.domain.permission.PermissionChoice
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.aicodeeditor.feature.agent.domain.workflow.AgentWorkflow
import com.aicodeeditor.feature.agent.domain.workflow.AgentEvent
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionManager
import com.aicodeeditor.feature.agent.domain.tool.ToolRegistry
import com.aicodeeditor.feature.agent.domain.tool.question.AskUserQuestionManager
import com.aicodeeditor.feature.agent.domain.tool.question.UserQuestionAnswer
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed class AgentUIState {
    object Idle : AgentUIState()
    object Loading : AgentUIState()
    object Streaming : AgentUIState()
    data class Result(val status: WorkflowStatus) : AgentUIState()
    object Applied : AgentUIState()
    data class Error(val message: String) : AgentUIState()
}

/**
 * 网络请求重试状态（仅用于 UI 实时展示「正在重试 (N/M)...」提示）。
 * 与 [AgentUIState] 解耦：重试是 Streaming 的子状态，不改变顶层 agent 状态机。
 */
@Immutable
data class RetryState(val attempt: Int, val maxRetries: Int)

@Immutable
data class AgentUIMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    // 仅 TOOL 消息：渲染用，不参与上下文回放。
    val toolName: String? = null,
    // 仅 TOOL 消息：本次调用传入的参数（JSON 文本），渲染「执行的指令」用。
    val toolArgs: String? = null,
    val isError: Boolean = false,
    // 仅 ASSISTANT 消息：本轮模型的思考过程，渲染为可折叠「思考过程」气泡；无则为 null。
    val reasoning: String? = null,
    // 预解析后的 Markdown 块（普通文本或代码块），供 UI 层极速渲染。
    val parsedBlocks: List<MdBlock> = emptyList()
)

/** Markdown 渲染块：纯文本段或围栏代码块。 */
sealed interface MdBlock {
    @Immutable data class Text(val text: String) : MdBlock
    @Immutable data class Code(val lang: String, val code: String) : MdBlock
}

/**
 * 按行扫描，把文本切成「文本段」与「围栏代码块」。以 ``` 开头的行进入代码模式，
 * 直到下一条 ``` 行或文本结束（流式途中未闭合也按代码块收尾）。围栏行本身被丢弃，
 * 其后的标识符作为语言标签。相邻文本被合并为一段，纯空白段丢弃。
 */
fun parseMarkdownBlocks(src: String): List<MdBlock> {
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
            if (i < lines.size) i++
            blocks.add(MdBlock.Code(lang, code.toString()))
        } else {
            if (textBuf.isNotEmpty()) textBuf.append("\n")
            textBuf.append(line)
            i++
        }
    }
    flushText()
    if (blocks.isEmpty()) blocks.add(MdBlock.Text(src))
    return blocks
}

enum class MessageRole {
    USER, ASSISTANT, TOOL
}

/**
 * 一次会话消息查询的结果快照。
 * - [sessionId]：这批消息所属会话；null 表示当前还没有解析出会话（冷启动中）。
 * - [messages]：已过滤、可直接渲染的消息列表。
 * - [loaded]：是否已经从数据库读到该会话的数据，用以区分「加载中」与「空会话」。
 */
@Immutable
data class ChatMessagesState(
    val sessionId: String?,
    val messages: List<AgentUIMessage>,
    val loaded: Boolean,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false
)

/**
 * 「不出墨」的码点：虽非空白、也不属格式(Cf)/控制(Cc)，却渲染为零宽或纯空白。
 * 关键是 Hangul filler 一族——它们的 Unicode 类别是 Lo(其他字母)，所以**任何按类别判定的
 * 方案都抓不到**，必须显式列举。这是上一版「只过滤 Cf/Cc」修复失效的真正原因：部分模型在
 * 纯工具调用轮次吐出 U+3164 等填充字符，每调一次工具就漏出一个空气泡。
 */
private val BLANK_GLYPH_CODE_POINTS: Set<Char> = setOf(
    0x115F, 0x1160, 0x3164, 0xFFA0, // Hangul filler（Lo，零宽，按类别抓不到）
    0x2800,                         // Braille pattern blank（So，纯空点）
    0x034F,                         // 组合用字位连接符 CGJ
    0x17B4, 0x17B5,                 // Khmer 固有元音（零宽）
    0x2060, 0xFEFF,                 // 词连接符 / BOM（Cf，冗余兜底）
    0x200B, 0x200C, 0x200D,         // 零宽空格 / ZWNJ / ZWJ（Cf，冗余兜底）
).mapTo(HashSet()) { it.toChar() }

/**
 * 文本是否含「可见(出墨)」内容。判定 = 至少有一个字符既非空白、又不属不可见类别(Cf/Cc/代理)、
 * 也不在 [BLANK_GLYPH_CODE_POINTS] 黑名单内。比 [CharSequence.isBlank] 严格得多：
 * 后者只把 whitespace 当空，会让零宽/填充字符漏出空白气泡。
 * 这是「是否渲染助手气泡」的唯一关门，持久化归一化与各渲染/过滤层共用它，改此一处即全链路生效。
 * 注意：保留代理对(emoji 等)与普通可见字符；零宽连接符 ZWJ 只在「整条文本是否为空」上当空，
 * 不会从展示文本里被剔除，故 emoji 连字序列不受影响。
 */
fun CharSequence.hasVisibleContent(): Boolean = any { ch ->
    !ch.isWhitespace() &&
        ch.category != CharCategory.FORMAT &&
        ch.category != CharCategory.CONTROL &&
        ch.category != CharCategory.SURROGATE &&
        ch !in BLANK_GLYPH_CODE_POINTS
}

/**
 * 运行中工具的实时累积输出。仅存内存、不落库：用于在该工具消息气泡里实时叠加显示
 * 命令逐行 stdout；命令结束后清空，最终完整结果走正常 persist 落库。
 */
@Immutable
data class RunningToolOutput(val messageId: String, val text: String, val toolName: String = "", val toolArgs: String = "")

data class QueuedRequest(
    val id: String,
    val request: String,
    val currentFile: String?,
    val selectedCode: String?,
    val projectRoot: String
)

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
    private val containerEngine: LinuxContainerEngine
) : ViewModel() {

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

    val sessions: StateFlow<List<com.aicodeeditor.feature.agent.domain.model.ChatSession>> = _currentWorkspace
        .flatMapLatest { path ->
            chatSessionDao.getAllSessionsByWorkspace(path)
                .map { list -> list.map { it.toDomain() } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentSessionMode: StateFlow<com.aicodeeditor.feature.agent.domain.model.AgentMode> = kotlinx.coroutines.flow.combine(
        _currentSessionId, sessions
    ) { id, list ->
        list.find { it.id == id }?.mode ?: com.aicodeeditor.feature.agent.domain.model.AgentMode.BUILD
    }.stateIn(viewModelScope, SharingStarted.Eagerly, com.aicodeeditor.feature.agent.domain.model.AgentMode.BUILD)

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
                // 在后台线程执行预解析
                withContext(Dispatchers.Default) {
                    ChatMessagesState(
                        sessionId = id,
                        messages = list.asSequence()
                            .filterNot {
                                it.role == MessageRole.ASSISTANT.name &&
                                    !it.content.hasVisibleContent() &&
                                    it.reasoning.isNullOrEmpty()
                            }
                            .map { entity -> 
                                val uiMessage = entity.toUIMessage()
                                uiMessage.copy(parsedBlocks = parseMarkdownBlocks(uiMessage.content))
                            }
                            .toList(),
                        loaded = true,
                        hasMore = list.size >= limit,
                        isLoadingMore = false
                    )
                }
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

    private val json = Json { ignoreUnknownKeys = true }

    // 工具调用传入参数（argsPreview）按落库消息 id 暂存：ToolCallStarted 落库后，
    // ToolCallFinished / 用户停止会用同 id REPLACE 整行，需在此把参数带到后续落库。
    private val toolArgsByMsgId = mutableMapOf<String, String>()

    /** 是否有正在运行、可被打断的 agent 任务。 */
    val isRunning: Boolean get() {
        val sid = _currentSessionId.value ?: return false
        return sessionJobs[sid]?.isActive == true
    }

    fun hasRunningSessionsInCurrentWorkspace(): Boolean {
        // sessions.value already contains the current workspace's sessions
        return sessions.value.any { sessionJobs[it.id]?.isActive == true }
    }

    // 单调递增时间戳：保证同毫秒内多次落库的顺序稳定（assistant 永远在其 tool 结果之前）。
    private var lastTimestamp = 0L
    @Synchronized
    private fun nextTimestamp(): Long {
        val now = System.currentTimeMillis()
        val ts = if (now > lastTimestamp) now else lastTimestamp + 1
        lastTimestamp = ts
        return ts
    }

    private companion object {
        const val TAG = "AIAgentViewModel"
        const val TITLE_MAX = 20
        // 工具占位行前缀：标记「执行中、结果未回」的孤儿，UI 与回放据此识别并排除。
        const val PENDING_TOOL_MARKER = "⏳"
        // 进程被杀后残留「执行中」工具行的收尾文案（冷启动时回填）。
        const val INTERRUPTED_TOOL_TEXT = "⏹ 执行被中断（应用已关闭）"
        // 用户主动停止时的收尾标记（追加在已输出内容之后，而非整体替换）。
        const val STOPPED_TOOL_TEXT = "⏹ 已被用户停止"
    }

    init {
        viewModelScope.launch {
            // 冷启动收尾：上次进程被杀时若有工具正在执行，其 ⏳ 占位行会永久显示「执行中」。
            // 这些工具不可能还在跑，统一回填为「已中断」。放在设置会话之前完成，使首帧不再闪转圈。
            runCatching {
                val n = agentMessageDao.markPendingToolsInterrupted(
                    toolRole = MessageRole.TOOL.name,
                    pendingPrefix = "$PENDING_TOOL_MARKER%",
                    interruptedContent = INTERRUPTED_TOOL_TEXT
                )
                if (n > 0) FileLogger.i(TAG, "冷启动收尾 $n 条残留「执行中」工具行为已中断")
            }.onFailure { FileLogger.e(TAG, "回收残留执行中工具行失败", it) }

            _currentWorkspace.collectLatest { path ->
                val existing = chatSessionDao.getAllSessionsByWorkspaceOnce(path)
                _currentSessionId.value = if (existing.isNotEmpty()) {
                    existing.first().id // ORDER BY updatedAt DESC：最近一条
                } else {
                    val s = newSessionEntity(path)
                    chatSessionDao.upsert(s)
                    s.id
                }
            }
        }
    }

    fun executeAgentRequest(
        request: String,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = "",
        targetSessionId: String? = null
    ): Job = viewModelScope.launch {
        val sessionId = targetSessionId ?: ensureSession()
        sessionJobs[sessionId] = coroutineContext[Job]!!
        setAgentState(sessionId, AgentUIState.Loading)

        try {
            val history = buildHistory(sessionId)
            val isFirst = history.isEmpty()

            persist(sessionId, MessageRole.USER, request)
            if (isFirst) chatSessionDao.updateTitle(sessionId, deriveTitle(request))
            chatSessionDao.touch(sessionId, nextTimestamp())

            val sessionEntity = chatSessionDao.getById(sessionId)
            val mode = sessionEntity?.toDomain()?.mode ?: com.aicodeeditor.feature.agent.domain.model.AgentMode.BUILD

            val context = AgentContext(
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                language = currentFile?.let { detectLanguage(it) },
                history = history,
                sessionId = sessionId,
                mode = mode
            )

            val result = agentWorkflow.execute(
                userRequest = request,
                context = context,
                tools = toolRegistry.getAvailableTools(mode)
            )

            persist(sessionId, MessageRole.ASSISTANT, result.result)
            chatSessionDao.touch(sessionId, nextTimestamp())
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
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = ""
    ) {
        val sid = _currentSessionId.value
        val isCurrentRunning = sid != null && sessionJobs[sid]?.isActive == true
        if (isCurrentRunning) {
            val req = QueuedRequest(
                id = UUID.randomUUID().toString(),
                request = request,
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot
            )
            val currentList = _queuedRequests.value[sid] ?: emptyList()
            _queuedRequests.value = _queuedRequests.value + (sid to (currentList + req))
        } else {
            executeAgentRequestStream(request, currentFile, selectedCode, projectRoot, sid)
        }
    }

    private fun processNextInQueue(sessionId: String) {
        val queue = _queuedRequests.value[sessionId] ?: return
        val next = queue.firstOrNull() ?: return
        _queuedRequests.value = _queuedRequests.value + (sessionId to queue.drop(1))
        executeAgentRequestStream(
            request = next.request,
            currentFile = next.currentFile,
            selectedCode = next.selectedCode,
            projectRoot = next.projectRoot,
            targetSessionId = sessionId
        )
    }

    fun executeAgentRequestStream(
        request: String,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = "",
        targetSessionId: String? = null
    ): Job = viewModelScope.launch {
        val sessionId = targetSessionId ?: ensureSession()
        sessionJobs[sessionId] = coroutineContext[Job]!!
        setAgentState(sessionId, AgentUIState.Streaming)

        try {
            // 必须在插入本次用户消息之前读取历史：workflow 会自己 add(userRequest)，避免重复。
            val history = buildHistory(sessionId)
            val isFirst = history.isEmpty()

            persist(sessionId, MessageRole.USER, request)
            if (isFirst) chatSessionDao.updateTitle(sessionId, deriveTitle(request))
            chatSessionDao.touch(sessionId, nextTimestamp())

            val sessionEntity = chatSessionDao.getById(sessionId)
            val mode = sessionEntity?.toDomain()?.mode ?: com.aicodeeditor.feature.agent.domain.model.AgentMode.BUILD

            val context = AgentContext(
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                language = currentFile?.let { detectLanguage(it) },
                history = history,
                sessionId = sessionId,
                mode = mode
            )

            agentWorkflow.executeEvents(
                userRequest = request,
                context = context,
                tools = toolRegistry.getAvailableTools(mode)
            ).collect { event ->
                when (event) {
                    is AgentEvent.AssistantDelta -> {
                        // 仅更新内存态实时文字，不落库；UI 在底部渲染一个跟随增长的助手气泡。
                        // 流已恢复吐字，清除「正在重试」提示。
                        setRetryState(sessionId, null)
                        setStreamingText(sessionId, event.accumulated)
                    }
                    is AgentEvent.ReasoningDelta -> {
                        // 仅更新内存态实时思考，不落库；UI 在底部渲染一个可折叠的「思考」气泡。
                        // 流已恢复吐字，清除「正在重试」提示。
                        setRetryState(sessionId, null)
                        setStreamingReasoning(sessionId, event.accumulated)
                    }
                    is AgentEvent.Retrying -> {
                        // 网络请求正在重试（首字节前失败触发自动重试），UI 展示「正在重试 (N/M)...」。
                        setRetryState(sessionId, RetryState(event.attempt, event.maxRetries))
                    }
                    is AgentEvent.AssistantText -> {
                        // 即便 content 为空（纯工具调用），也落库以携带结构化 toolCalls，保证回放配对完整。
                        // 归一化：无可见内容（仅空白/零宽空格/BOM 等）的文字一律存为空串，
                        // 避免不可见字符在 DB 里残留、回放与 UI 渲染出空白气泡。
                        val normalized = if (event.content.hasVisibleContent()) event.content else ""
                        // 思考过程随本轮助手消息一并落库（仅供历史展示，不回放进上下文）；空白归一为 null。
                        val reasoning = event.reasoning.takeIf { it.hasVisibleContent() }
                        persist(
                            sessionId,
                            MessageRole.ASSISTANT,
                            normalized,
                            toolCalls = event.toolCalls,
                            reasoning = reasoning
                        )
                        // 本轮思考已结束（拿到正式回复/工具调用），清空思考气泡。
                        setStreamingReasoning(sessionId, null)
                        // 保留满文本的流式气泡撑到回合真正结束——避免在 isBusy=true 期间清流式
                        // 导致 __thinking__ 出现继而消失的闪烁序列。回合结束处统一清空。
                        setStreamingText(sessionId, normalized.ifEmpty { null })
                    }
                    is AgentEvent.ToolCallStarted -> {
                        val msgId = "tool_${event.id}"
                        // 即将执行工具，清空之前的流式文本气泡（它会被落库消息取代）。
                        setStreamingText(sessionId, null)
                        // 记下本次调用参数，供 Finished/停止时用同 id REPLACE 落库复用。
                        toolArgsByMsgId[msgId] = event.argsPreview
                        // 占位行：执行中提示；ToolCallFinished 用相同 id REPLACE 为最终结果。
                        persist(
                            sessionId,
                            MessageRole.TOOL,
                            "⏳ ${event.toolName} 执行中…",
                            id = msgId,
                            toolCallId = event.id,
                            toolName = event.toolName,
                            toolArgs = event.argsPreview,
                            isError = false
                        )
                        // 标记该占位行进入「实时输出」模式（内存态）。
                        setRunningTool(sessionId, RunningToolOutput(msgId, "", event.toolName, event.argsPreview))
                    }
                    is AgentEvent.ToolCallProgress -> {
                        // 仅更新内存态实时输出，不落库；UI 叠加渲染到对应占位行。
                        setRunningTool(sessionId, RunningToolOutput(
                            "tool_${event.id}",
                            event.accumulated,
                            event.toolName,
                            toolArgsByMsgId["tool_${event.id}"] ?: ""
                        ))
                    }
                    is AgentEvent.ToolCallFinished -> {
                        val msgId = "tool_${event.id}"
                        // content 存原始完整结果（回放喂回模型）；截断/图标交给 UI。
                        persist(
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
                        // 最终结果已落库，清空实时输出态。
                        if (_runningTools.value[sessionId]?.messageId == msgId) {
                            setRunningTool(sessionId, null)
                        }
                    }
                    AgentEvent.Completed -> {
                        // 循环结束，下方统一置为成功；同时清掉残留的重试提示。
                        setRetryState(sessionId, null)
                    }
                }
            }

            chatSessionDao.touch(sessionId, nextTimestamp())
            // 先关 busy（让 isBusy=false），再清流式气泡——同一 snapshot 内完成，
            // 避免 isBusy=true 期间流式气泡已清而触发 __thinking__ 短暂出现的闪烁。
            setAgentState(sessionId, AgentUIState.Result(WorkflowStatus.SUCCESS))
            setStreamingText(sessionId, null)

        } catch (e: CancellationException) {
            // 用户主动停止：不是错误，复位为空闲后重新抛出以遵守结构化并发。
            setAgentState(sessionId, AgentUIState.Idle)
            throw e
        } catch (e: Exception) {
             FileLogger.e(TAG, "executeAgentRequestStream 失败: request=$request", e)
             setAgentState(sessionId, AgentUIState.Error(e.toUserMessage()))
        } finally {
            if (sessionJobs[sessionId] == coroutineContext[Job]) {
                sessionJobs.remove(sessionId)
            }
            // 无论成功/失败/取消，都清空实时输出态，避免残留 spinner 与悬挂的流式文字。
            setRunningTool(sessionId, null)
            setStreamingText(sessionId, null)
            setStreamingReasoning(sessionId, null)
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
    fun stopAgent() {
        val sessionId = _currentSessionId.value ?: return
        val job = sessionJobs[sessionId] ?: return
        if (!job.isActive) return
        // 取消前先抓住运行中的工具和实时流式文本，避免与协程 finally 清空状态竞争。
        val running = _runningTools.value[sessionId]
        val streamingText = _streamingTexts.value[sessionId]
        val streamingReasoning = _streamingReasonings.value[sessionId]
        job.cancel()
        viewModelScope.launch {
            if (running != null) {
                // 保留已实时输出的内容，仅在末尾追加「已停止」标记，而不是整体替换。
                val partial = running.text.trimEnd()
                val content = if (partial.isNotEmpty()) "$partial\n\n$STOPPED_TOOL_TEXT" else STOPPED_TOOL_TEXT
                persist(
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
                persist(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = content,
                    reasoning = reasoning
                )
            }
            setRunningTool(sessionId, null)
            setStreamingText(sessionId, null)
            setStreamingReasoning(sessionId, null)
            setRetryState(sessionId, null)
        }
    }

    // region 会话管理

    /** 新建会话；若当前会话还是空的则直接复用，避免堆积空会话。 */
    fun newSession() = viewModelScope.launch {
        val curId = _currentSessionId.value
        if (curId != null && agentMessageDao.getMessagesBySessionOnce(curId).isEmpty()) {
            setAgentState(curId, AgentUIState.Idle)
            setChanges(curId, emptyList())
            return@launch
        }
        val s = newSessionEntity()
        chatSessionDao.upsert(s)
        _currentSessionId.value = s.id
        // Inherits default state (Idle)
    }

    fun setCurrentSessionId(id: String) {
        if (_currentSessionId.value == id) return
        _currentSessionId.value = id
        // 切换时如果旧会话正在加载，我们不自动取消，它会在后台继续完成并落库。
        // UI 会响应 id 变化，渲染新会话的列表。
    }

    fun setSessionMode(mode: com.aicodeeditor.feature.agent.domain.model.AgentMode) {
        val sid = _currentSessionId.value ?: return
        viewModelScope.launch {
            val s = chatSessionDao.getById(sid) ?: return@launch
            chatSessionDao.upsert(s.copy(mode = mode.name))
        }
    }

    fun selectSession(id: String) {
        if (_currentSessionId.value == id) return
        _currentSessionId.value = id
        // Do not clear the state, allow it to display its actual background state.
    }

    fun deleteSession(id: String) = viewModelScope.launch {
        agentMessageDao.deleteBySession(id)
        chatSessionDao.delete(id)

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
            val remaining = chatSessionDao.getAllSessionsByWorkspaceOnce(_currentWorkspace.value)
            if (remaining.isNotEmpty()) {
                _currentSessionId.value = remaining.first().id
            } else {
                val s = newSessionEntity()
                chatSessionDao.upsert(s)
                _currentSessionId.value = s.id
            }
        }
    }

    private suspend fun ensureSession(): String {
        _currentSessionId.value?.let { return it }
        val existing = chatSessionDao.getAllSessionsByWorkspaceOnce(_currentWorkspace.value)
        val id = if (existing.isNotEmpty()) existing.first().id else {
            val s = newSessionEntity()
            chatSessionDao.upsert(s)
            s.id
        }
        _currentSessionId.value = id
        return id
    }

    private fun newSessionEntity(workspacePath: String = _currentWorkspace.value): ChatSessionEntity {
        val now = nextTimestamp()
        return ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = "新会话",
            workspacePath = workspacePath,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun deriveTitle(request: String): String {
        val clean = request.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= TITLE_MAX) clean.ifBlank { "新对话" }
        else clean.take(TITLE_MAX) + "…"
    }

    // endregion

    /**
     * 从持久化的消息重建合法的上下文历史。
     * 关键：只保留「assistant 的 tool_call」与「tool 结果」能配对成功的部分，
     * 丢弃任何一方缺失的悬挂项，避免回放出现孤儿 tool_use / tool_result 违反 API 约束。
     */
    private suspend fun buildHistory(sessionId: String): List<AgentMessage> {
        val entities = agentMessageDao.getMessagesBySessionOnce(sessionId)

        // 第一遍：求 assistant 声明的 toolCallId 与 tool 结果 toolCallId 的交集。
        val declaredIds = mutableSetOf<String>()
        val resultIds = mutableSetOf<String>()
        for (e in entities) {
            when (MessageRole.valueOf(e.role)) {
                MessageRole.ASSISTANT -> e.toolCallsJson?.let {
                    runCatching { json.decodeFromString<List<ToolCall>>(it) }
                        .getOrNull()?.forEach { tc -> declaredIds.add(tc.id) }
                }
                MessageRole.TOOL -> {
                    // 只有真正完成的结果才计入配对；"⏳ 执行中"占位行（完成事件未回来的孤儿）不算。
                    if (!e.content.startsWith(PENDING_TOOL_MARKER)) {
                        e.toolCallId?.let { resultIds.add(it) }
                    }
                }
                else -> {}
            }
        }
        val validIds = declaredIds intersect resultIds

        // 第二遍：构建消息，过滤掉无法配对的工具调用 / 工具结果。
        val result = mutableListOf<AgentMessage>()
        for (e in entities) {
            when (MessageRole.valueOf(e.role)) {
                MessageRole.USER -> result.add(
                    AgentMessage.UserMessage(id = e.id, content = e.content)
                )
                MessageRole.ASSISTANT -> {
                    val toolCalls = e.toolCallsJson?.let {
                        runCatching { json.decodeFromString<List<ToolCall>>(it) }.getOrNull()
                    }?.filter { it.id in validIds } ?: emptyList()
                    if (e.content.isNotBlank() || toolCalls.isNotEmpty()) {
                        result.add(
                            AgentMessage.AssistantMessage(
                                id = e.id,
                                content = e.content,
                                toolCalls = toolCalls
                            )
                        )
                    }
                }
                MessageRole.TOOL -> {
                    val tcId = e.toolCallId
                    if (tcId != null && tcId in validIds) {
                        result.add(
                            AgentMessage.ToolResultMessage(
                                id = tcId,
                                toolName = e.toolName ?: "unknown",
                                result = e.content
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private suspend fun persist(
        sessionId: String,
        role: MessageRole,
        content: String,
        id: String = UUID.randomUUID().toString(),
        toolCalls: List<ToolCall> = emptyList(),
        toolCallId: String? = null,
        toolName: String? = null,
        toolArgs: String? = null,
        isError: Boolean = false,
        reasoning: String? = null
    ) {
        agentMessageDao.insert(
            AgentMessageEntity(
                id = id,
                sessionId = sessionId,
                role = role.name,
                content = content,
                timestamp = nextTimestamp(),
                toolCallsJson = if (toolCalls.isNotEmpty()) json.encodeToString(toolCalls) else null,
                toolCallId = toolCallId,
                toolName = toolName,
                toolArgs = toolArgs,
                isError = isError,
                reasoning = reasoning
            )
        )
    }

    fun applyChanges(changes: List<CodeChange>) = viewModelScope.launch {
        try {
            // 文件读写是阻塞 IO，必须切到 Dispatchers.IO，避免 viewModelScope（默认 Main）写盘时卡主线程导致 ANR。
            withContext(Dispatchers.IO) {
                for (change in changes) {
                    when (change.type) {
                        com.aicodeeditor.feature.agent.domain.model.ChangeType.CREATE -> {
                            val file = File(change.filePath)
                            file.parentFile?.mkdirs()
                            file.writeText(change.newCode)
                        }

                        com.aicodeeditor.feature.agent.domain.model.ChangeType.REPLACE -> {
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

                        com.aicodeeditor.feature.agent.domain.model.ChangeType.INSERT -> {
                            val file = File(change.filePath)
                            val lines = file.readLines().toMutableList()
                            val insertLine = (change.startLine - 1).coerceIn(0, lines.size)
                            change.newCode.lines().reversed().forEach { line ->
                                lines.add(insertLine, line)
                            }
                            file.writeText(lines.joinToString("\n"))
                        }

                        com.aicodeeditor.feature.agent.domain.model.ChangeType.DELETE -> {
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
