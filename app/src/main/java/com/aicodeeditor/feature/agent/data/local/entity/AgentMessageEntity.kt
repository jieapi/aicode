package com.aicodeeditor.feature.agent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aicodeeditor.feature.agent.presentation.MessageRole
import com.aicodeeditor.feature.agent.presentation.AgentUIMessage

@Entity(tableName = "agent_messages")
data class AgentMessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val content: String,
    val timestamp: Long
) {
    fun toUIMessage(): AgentUIMessage {
        return AgentUIMessage(
            id = id,
            role = MessageRole.valueOf(role),
            content = content,
            timestamp = timestamp
        )
    }

    companion object {
        fun fromUIMessage(message: AgentUIMessage): AgentMessageEntity {
            return AgentMessageEntity(
                id = message.id,
                role = message.role.name,
                content = message.content,
                timestamp = message.timestamp
            )
        }
    }
}
