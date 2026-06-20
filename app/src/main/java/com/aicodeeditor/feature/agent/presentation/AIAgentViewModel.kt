package com.aicodeeditor.feature.agent.presentation

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
    val reasoning: String? = null
)

enum class MessageRole {
    USER, ASSISTANT, TOOL
}

/**
 * 一次会话消息查询的结果快照。
 * - [sessionId]：这批消息所属会话；null 表示当前还没有解析出会话（冷启动中）。
 * - [messages]：已过滤、可直接渲染的消息列表。
 * - [loaded]：是否已经从数据库读到该会话的数据，用以区分「加载中」与「空会话」。
 */
data class ChatMessagesState(
    val sessionId: String?,
    val messages: List<AgentUIMessage>,
    val loaded: Boolean
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
data class RunningToolOutput(val messageId: String, val text: String, val toolName: String = "", val toolArgs: String = "")

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

    private val _agentState = MutableStateFlow<AgentUIState>(AgentUIState.Idle)
    val agentState: StateFlow<AgentUIState> = _agentState.asStateFlow()

    /** 容器初始化实时进度（解压/部署/装包），AI 页底部气泡展示。 */
    val containerInit: StateFlow<ContainerInitState> = containerEngine.initProgress

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val sessions: StateFlow<List<ChatSession>> = chatSessionDao.getAllSessions()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 当前会话的消息状态：会话切换时自动切换到对应历史，并携带所属会话 id 与 loaded 标志，
     * 使 UI 能区分「切换/冷启动加载中」与「空会话」——避免先闪 Welcome 或上一个会话的消息再突然刷新。
     * 过滤掉「纯工具调用」的空助手行（content 为空、仅用于回放配对，不应显示为气泡）。
     */
    val messagesState: StateFlow<ChatMessagesState> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(ChatMessagesState(null, emptyList(), loaded = false))
            else agentMessageDao.getMessagesBySession(id).map { list ->
                ChatMessagesState(
                    sessionId = id,
                    messages = list.asSequence()
                        // 丢弃既无可见文字、又无思考过程的纯回放配对助手行（仅携带 toolCalls）；
                        // 但若带思考过程则保留，以便历史里仍能展开查看本轮思考。
                        .filterNot {
                            it.role == MessageRole.ASSISTANT.name &&
                                !it.content.hasVisibleContent() &&
                                it.reasoning.isNullOrEmpty()
                        }
                        .map { it.toUIMessage() }
                        .toList(),
                    loaded = true
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatMessagesState(null, emptyList(), loaded = false))

    private val _changes = MutableStateFlow<List<CodeChange>>(emptyList())
    val changes: StateFlow<List<CodeChange>> = _changes.asStateFlow()

    // 运行中工具的实时输出（内存态，不落库）。null 表示当前没有流式工具在跑。
    private val _runningTool = MutableStateFlow<RunningToolOutput?>(null)
    val runningTool: StateFlow<RunningToolOutput?> = _runningTool.asStateFlow()

    // 模型当前正在流式吐出的文字（内存态，不落库）。null 表示当前没有在流式输出文字；
    // 本轮文字结束后随最终消息落库而清空，由持久化气泡接管显示。
    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    // 模型当前正在流式吐出的思考过程（内存态，不落库）。null 表示当前没有思考输出；
    // 本轮拿到正式回复或工具调用后清空，不进入持久化历史。
    private val _streamingReasoning = MutableStateFlow<String?>(null)
    val streamingReasoning: StateFlow<String?> = _streamingReasoning.asStateFlow()

    val pendingToolPermission = toolPermissionManager.pendingRequest

    val pendingUserQuestion = askUserQuestionManager.pendingQuestion

    private val json = Json { ignoreUnknownKeys = true }

    // 工具调用传入参数（argsPreview）按落库消息 id 暂存：ToolCallStarted 落库后，
    // ToolCallFinished / 用户停止会用同 id REPLACE 整行，需在此把参数带到后续落库。
    private val toolArgsByMsgId = mutableMapOf<String, String>()

    /** 当前运行中的 agent 协程；用于「停止/打断」。空闲时为 null 或已完成。 */
    private var agentJob: Job? = null

    /** 是否有正在运行、可被打断的 agent 任务。 */
    val isRunning: Boolean get() = agentJob?.isActive == true

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

            val existing = chatSessionDao.getAllSessionsOnce()
            _currentSessionId.value = if (existing.isNotEmpty()) {
                existing.first().id // ORDER BY updatedAt DESC：最近一条
            } else {
                val s = newSessionEntity()
                chatSessionDao.upsert(s)
                s.id
            }
        }
    }

    fun executeAgentRequest(
        request: String,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = ""
    ): Job = viewModelScope.launch {
        _agentState.value = AgentUIState.Loading

        try {
            val sessionId = ensureSession()
            val history = buildHistory(sessionId)
            val isFirst = history.isEmpty()

            persist(sessionId, MessageRole.USER, request)
            if (isFirst) chatSessionDao.updateTitle(sessionId, deriveTitle(request))
            chatSessionDao.touch(sessionId, nextTimestamp())

            val context = AgentContext(
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                language = currentFile?.let { detectLanguage(it) },
                history = history,
                sessionId = sessionId
            )

            val result = agentWorkflow.execute(
                userRequest = request,
                context = context,
                tools = toolRegistry.getAvailableTools()
            )

            persist(sessionId, MessageRole.ASSISTANT, result.result)
            chatSessionDao.touch(sessionId, nextTimestamp())
            _changes.value = result.changes

            _agentState.value = AgentUIState.Result(result.status)

            if (result.status != WorkflowStatus.SUCCESS && result.errors.isNotEmpty()) {
                _agentState.value = AgentUIState.Error(result.errors.joinToString("\n"))
            }

        } catch (e: CancellationException) {
            _agentState.value = AgentUIState.Idle
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "executeAgentRequest 失败: request=$request", e)
            _agentState.value = AgentUIState.Error(e.toUserMessage())
        }
    }.also { agentJob = it }

    fun executeAgentRequestStream(
        request: String,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = ""
    ): Job = viewModelScope.launch {
        _agentState.value = AgentUIState.Streaming

        try {
            val sessionId = ensureSession()
            // 必须在插入本次用户消息之前读取历史：workflow 会自己 add(userRequest)，避免重复。
            val history = buildHistory(sessionId)
            val isFirst = history.isEmpty()

            persist(sessionId, MessageRole.USER, request)
            if (isFirst) chatSessionDao.updateTitle(sessionId, deriveTitle(request))
            chatSessionDao.touch(sessionId, nextTimestamp())

            val context = AgentContext(
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                language = currentFile?.let { detectLanguage(it) },
                history = history,
                sessionId = sessionId
            )

            agentWorkflow.executeEvents(
                userRequest = request,
                context = context,
                tools = toolRegistry.getAvailableTools()
            ).collect { event ->
                when (event) {
                    is AgentEvent.AssistantDelta -> {
                        // 仅更新内存态实时文字，不落库；UI 在底部渲染一个跟随增长的助手气泡。
                        _streamingText.value = event.accumulated
                    }
                    is AgentEvent.ReasoningDelta -> {
                        // 仅更新内存态实时思考，不落库；UI 在底部渲染一个可折叠的「思考」气泡。
                        _streamingReasoning.value = event.accumulated
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
                        // 本轮文字已落库，清空实时态，交给持久化气泡显示。
                        _streamingText.value = null
                        // 本轮思考已结束（拿到正式回复/工具调用），清空思考气泡。
                        _streamingReasoning.value = null
                    }
                    is AgentEvent.ToolCallStarted -> {
                        val msgId = "tool_${event.id}"
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
                        _runningTool.value = RunningToolOutput(msgId, "", event.toolName, event.argsPreview)
                    }
                    is AgentEvent.ToolCallProgress -> {
                        // 仅更新内存态实时输出，不落库；UI 叠加渲染到对应占位行。
                        _runningTool.value = RunningToolOutput(
                            "tool_${event.id}",
                            event.accumulated,
                            event.toolName,
                            toolArgsByMsgId["tool_${event.id}"] ?: ""
                        )
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
                        if (_runningTool.value?.messageId == msgId) {
                            _runningTool.value = null
                        }
                    }
                    AgentEvent.Completed -> { /* 循环结束，下方统一置为成功 */ }
                }
            }

            chatSessionDao.touch(sessionId, nextTimestamp())
            _agentState.value = AgentUIState.Result(WorkflowStatus.SUCCESS)

        } catch (e: CancellationException) {
            // 用户主动停止：不是错误，复位为空闲后重新抛出以遵守结构化并发。
            _agentState.value = AgentUIState.Idle
            throw e
        } catch (e: Exception) {
             FileLogger.e(TAG, "executeAgentRequestStream 失败: request=$request", e)
             _agentState.value = AgentUIState.Error(e.toUserMessage())
        } finally {
            // 无论成功/失败/取消，都清空实时输出态，避免残留 spinner 与悬挂的流式文字。
            _runningTool.value = null
            _streamingText.value = null
            _streamingReasoning.value = null
        }
    }.also { agentJob = it }

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
        val job = agentJob ?: return
        if (!job.isActive) return
        // 取消前先抓住运行中的工具与会话，避免与协程 finally 清空 _runningTool 竞争。
        val running = _runningTool.value
        val sessionId = _currentSessionId.value
        job.cancel()
        viewModelScope.launch {
            if (running != null && sessionId != null) {
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
            }
            _runningTool.value = null
            _agentState.value = AgentUIState.Idle
        }
    }

    // region 会话管理

    /** 新建会话；若当前会话还是空的则直接复用，避免堆积空会话。 */
    fun newSession() = viewModelScope.launch {
        val curId = _currentSessionId.value
        if (curId != null && agentMessageDao.getMessagesBySessionOnce(curId).isEmpty()) {
            _agentState.value = AgentUIState.Idle
            _changes.value = emptyList()
            return@launch
        }
        val s = newSessionEntity()
        chatSessionDao.upsert(s)
        _currentSessionId.value = s.id
        _agentState.value = AgentUIState.Idle
        _changes.value = emptyList()
    }

    fun selectSession(id: String) {
        if (_currentSessionId.value == id) return
        _currentSessionId.value = id
        _agentState.value = AgentUIState.Idle
        _changes.value = emptyList()
    }

    fun deleteSession(id: String) = viewModelScope.launch {
        agentMessageDao.deleteBySession(id)
        chatSessionDao.delete(id)
        if (_currentSessionId.value == id) {
            val remaining = chatSessionDao.getAllSessionsOnce()
            _currentSessionId.value = if (remaining.isNotEmpty()) {
                remaining.first().id
            } else {
                val s = newSessionEntity()
                chatSessionDao.upsert(s)
                s.id
            }
            _agentState.value = AgentUIState.Idle
            _changes.value = emptyList()
        }
    }

    private suspend fun ensureSession(): String {
        _currentSessionId.value?.let { return it }
        val existing = chatSessionDao.getAllSessionsOnce()
        val id = if (existing.isNotEmpty()) existing.first().id else {
            val s = newSessionEntity()
            chatSessionDao.upsert(s)
            s.id
        }
        _currentSessionId.value = id
        return id
    }

    private fun newSessionEntity(): ChatSessionEntity {
        val now = nextTimestamp()
        return ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = "新对话",
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
            _agentState.value = AgentUIState.Applied
            _changes.value = emptyList()
        } catch (e: Exception) {
            FileLogger.e(TAG, "applyChanges 失败", e)
            _agentState.value = AgentUIState.Error("应用更改失败: ${e.message}")
        }
    }

    fun rejectChanges() {
        _agentState.value = AgentUIState.Idle
        _changes.value = emptyList()
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
