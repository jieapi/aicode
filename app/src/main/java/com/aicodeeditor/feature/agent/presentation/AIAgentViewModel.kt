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
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.model.ChatSession
import com.aicodeeditor.feature.agent.domain.model.CodeChange
import com.aicodeeditor.feature.agent.domain.model.WorkflowStatus
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.aicodeeditor.feature.agent.domain.workflow.AgentWorkflow
import com.aicodeeditor.feature.agent.domain.workflow.AgentEvent
import com.aicodeeditor.feature.agent.domain.tool.ToolRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    val isError: Boolean = false
)

enum class MessageRole {
    USER, ASSISTANT, TOOL
}

/**
 * 运行中工具的实时累积输出。仅存内存、不落库：用于在该工具消息气泡里实时叠加显示
 * 命令逐行 stdout；命令结束后清空，最终完整结果走正常 persist 落库。
 */
data class RunningToolOutput(val messageId: String, val text: String, val toolName: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AIAgentViewModel @Inject constructor(
    private val agentWorkflow: AgentWorkflow,
    private val toolRegistry: ToolRegistry,
    private val codeChangeTracker: CodeChangeTracker,
    private val agentMessageDao: AgentMessageDao,
    private val chatSessionDao: ChatSessionDao,
    private val aiProviderRepository: AIProviderRepository
) : ViewModel() {

    private val _agentState = MutableStateFlow<AgentUIState>(AgentUIState.Idle)
    val agentState: StateFlow<AgentUIState> = _agentState.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val sessions: StateFlow<List<ChatSession>> = chatSessionDao.getAllSessions()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 当前会话的消息：会话切换时自动切换到对应历史。
    // 过滤掉「纯工具调用」的空助手行（content 为空、仅用于回放配对，不应显示为气泡）。
    val messages: StateFlow<List<AgentUIMessage>> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else agentMessageDao.getMessagesBySession(id).map { list ->
                list.asSequence()
                    .filterNot { it.role == MessageRole.ASSISTANT.name && it.content.isBlank() }
                    .map { it.toUIMessage() }
                    .toList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _changes = MutableStateFlow<List<CodeChange>>(emptyList())
    val changes: StateFlow<List<CodeChange>> = _changes.asStateFlow()

    // 运行中工具的实时输出（内存态，不落库）。null 表示当前没有流式工具在跑。
    private val _runningTool = MutableStateFlow<RunningToolOutput?>(null)
    val runningTool: StateFlow<RunningToolOutput?> = _runningTool.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

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
    }

    init {
        viewModelScope.launch {
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
                history = history
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
                history = history
            )

            agentWorkflow.executeEvents(
                userRequest = request,
                context = context,
                tools = toolRegistry.getAvailableTools()
            ).collect { event ->
                when (event) {
                    is AgentEvent.AssistantText -> {
                        // 即便 content 为空（纯工具调用），也落库以携带结构化 toolCalls，保证回放配对完整。
                        persist(
                            sessionId,
                            MessageRole.ASSISTANT,
                            event.content,
                            toolCalls = event.toolCalls
                        )
                    }
                    is AgentEvent.ToolCallStarted -> {
                        // 占位行：执行中提示；ToolCallFinished 用相同 id REPLACE 为最终结果。
                        persist(
                            sessionId,
                            MessageRole.TOOL,
                            "⏳ ${event.toolName} 执行中…",
                            id = "tool_${event.id}",
                            toolCallId = event.id,
                            toolName = event.toolName,
                            isError = false
                        )
                        // 标记该占位行进入「实时输出」模式（内存态）。
                        _runningTool.value = RunningToolOutput("tool_${event.id}", "", event.toolName)
                    }
                    is AgentEvent.ToolCallProgress -> {
                        // 仅更新内存态实时输出，不落库；UI 叠加渲染到对应占位行。
                        _runningTool.value = RunningToolOutput("tool_${event.id}", event.accumulated, event.toolName)
                    }
                    is AgentEvent.ToolCallFinished -> {
                        // content 存原始完整结果（回放喂回模型）；截断/图标交给 UI。
                        persist(
                            sessionId,
                            MessageRole.TOOL,
                            event.result,
                            id = "tool_${event.id}",
                            toolCallId = event.id,
                            toolName = event.toolName,
                            isError = event.isError
                        )
                        // 最终结果已落库，清空实时输出态。
                        if (_runningTool.value?.messageId == "tool_${event.id}") {
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
            // 无论成功/失败/取消，都清空实时输出态，避免残留 spinner。
            _runningTool.value = null
        }
    }.also { agentJob = it }

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
                persist(
                    sessionId = sessionId,
                    role = MessageRole.TOOL,
                    content = "⏹ 已被用户停止",
                    id = running.messageId,
                    toolCallId = running.messageId.removePrefix("tool_"),
                    toolName = running.toolName.ifBlank { null },
                    isError = true
                )
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
        isError: Boolean = false
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
                isError = isError
            )
        )
    }

    fun applyChanges(changes: List<CodeChange>) = viewModelScope.launch {
        try {
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
