package com.aicodeeditor.feature.agent.domain.model

enum class TodoStatus { PENDING, IN_PROGRESS, COMPLETED }

data class TodoItem(
    val id: String,
    val sessionId: String,
    val subject: String,
    val description: String = "",
    val status: TodoStatus = TodoStatus.PENDING,
    val priority: Int = 0,
    val order: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
