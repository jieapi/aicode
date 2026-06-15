package com.aicodeeditor.feature.agent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicodeeditor.feature.agent.data.local.dao.AgentMessageDao
import com.aicodeeditor.feature.agent.data.local.entity.AgentMessageEntity
import com.aicodeeditor.feature.agent.data.CodeChangeTracker
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.CodeChange
import com.aicodeeditor.feature.agent.domain.model.WorkflowStatus
import com.aicodeeditor.feature.agent.domain.workflow.AgentWorkflow
import com.aicodeeditor.feature.agent.domain.tool.ToolRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
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
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER, ASSISTANT, TOOL
}

@HiltViewModel
class AIAgentViewModel @Inject constructor(
    private val agentWorkflow: AgentWorkflow,
    private val toolRegistry: ToolRegistry,
    private val codeChangeTracker: CodeChangeTracker,
    private val agentMessageDao: AgentMessageDao
) : ViewModel() {

    private val _agentState = MutableStateFlow<AgentUIState>(AgentUIState.Idle)
    val agentState: StateFlow<AgentUIState> = _agentState.asStateFlow()

    private val _messages = MutableStateFlow<List<AgentUIMessage>>(emptyList())
    val messages: StateFlow<List<AgentUIMessage>> = _messages.asStateFlow()

    private val _changes = MutableStateFlow<List<CodeChange>>(emptyList())
    val changes: StateFlow<List<CodeChange>> = _changes.asStateFlow()

    init {
        viewModelScope.launch {
            agentMessageDao.getAllMessages().collect { entities ->
                _messages.value = entities.map { it.toUIMessage() }
            }
        }
    }

    fun executeAgentRequest(
        request: String,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = ""
    ) = viewModelScope.launch {
        _agentState.value = AgentUIState.Loading

        try {
            val userMessage = AgentUIMessage(
                id = System.currentTimeMillis().toString(),
                role = MessageRole.USER,
                content = request
            )
            // Save to DB
            agentMessageDao.insert(AgentMessageEntity.fromUIMessage(userMessage))

            // The _messages state will be updated via the flow collection in init
            // currentMessages.add(userMessage)
            // _messages.value = currentMessages

            val context = AgentContext(
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                language = currentFile?.let { detectLanguage(it) }
                // In a real app, map UIMessages back to AgentMessages for context.history
            )

            val result = agentWorkflow.execute(
                userRequest = request,
                context = context,
                tools = toolRegistry.getAvailableTools()
            )

            // Update UI with Assistant's final message
            val assistantMessage = AgentUIMessage(
                id = System.currentTimeMillis().toString(),
                role = MessageRole.ASSISTANT,
                content = result.result
            )
            agentMessageDao.insert(AgentMessageEntity.fromUIMessage(assistantMessage))
            _changes.value = result.changes

            _agentState.value = AgentUIState.Result(result.status)

            if (result.status != WorkflowStatus.SUCCESS && result.errors.isNotEmpty()) {
                _agentState.value = AgentUIState.Error(result.errors.joinToString("\n"))
            }

        } catch (e: Exception) {
            _agentState.value = AgentUIState.Error(e.message ?: "未知错误")
        }
    }

    fun executeAgentRequestStream(
        request: String,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = ""
    ) = viewModelScope.launch {
        _agentState.value = AgentUIState.Streaming

        try {
            val userMessage = AgentUIMessage(
                id = System.currentTimeMillis().toString(),
                role = MessageRole.USER,
                content = request
            )
            agentMessageDao.insert(AgentMessageEntity.fromUIMessage(userMessage))

            val context = AgentContext(
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                language = currentFile?.let { detectLanguage(it) }
            )

            val assistantMessageId = System.currentTimeMillis().toString()
            val initialAssistantMessage = AgentUIMessage(
                id = assistantMessageId,
                role = MessageRole.ASSISTANT,
                content = "" // Initial empty content
            )
            agentMessageDao.insert(AgentMessageEntity.fromUIMessage(initialAssistantMessage))

            var currentContent = ""

            agentWorkflow.executeStream(
                userRequest = request,
                context = context,
                tools = toolRegistry.getAvailableTools()
            ).collect { chunk ->
                currentContent += chunk
                // Continuously update DB during stream (could be optimized)
                agentMessageDao.insert(
                    AgentMessageEntity.fromUIMessage(
                        initialAssistantMessage.copy(content = currentContent)
                    )
                )
            }

            _agentState.value = AgentUIState.Result(WorkflowStatus.SUCCESS)

        } catch (e: Exception) {
             _agentState.value = AgentUIState.Error(e.message ?: "Streaming error")
        }
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
            _agentState.value = AgentUIState.Error("应用更改失败: ${e.message}")
        }
    }

    fun rejectChanges() {
        _agentState.value = AgentUIState.Idle
        _changes.value = emptyList()
    }

    fun clearMessages() = viewModelScope.launch {
        agentMessageDao.deleteAllMessages()
        _agentState.value = AgentUIState.Idle
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
