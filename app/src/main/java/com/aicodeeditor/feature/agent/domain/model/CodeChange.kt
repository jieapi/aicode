package com.aicodeeditor.feature.agent.domain.model

import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import kotlinx.serialization.Serializable

enum class ChangeType {
    INSERT, REPLACE, DELETE, CREATE, MOVE
}

@Serializable
data class CodeChange(
    val filePath: String,
    val type: ChangeType,
    val startLine: Int,
    val endLine: Int,
    val oldCode: String,
    val newCode: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class WorkflowStatus {
    SUCCESS, PARTIAL_SUCCESS, FAILED, CANCELLED
}

data class WorkflowResult(
    val status: WorkflowStatus,
    val result: String,
    val changes: List<CodeChange> = emptyList(),
    val errors: List<String> = emptyList(),
    val messages: List<AgentMessage> = emptyList()
)
