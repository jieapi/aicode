package com.aicode.feature.agent.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aicode.feature.agent.data.local.dao.AgentMessageDao
import com.aicode.feature.agent.data.local.dao.ChatSessionDao
import com.aicode.feature.agent.data.local.dao.TodoItemDao
import com.aicode.feature.agent.data.local.entity.AgentMessageEntity
import com.aicode.feature.agent.data.local.entity.ChatSessionEntity
import com.aicode.feature.agent.data.local.entity.TodoItemEntity
import com.aicode.feature.credentials.data.local.dao.GitCredentialDao
import com.aicode.feature.credentials.data.local.entity.GitCredentialEntity
import com.aicode.feature.settings.data.local.dao.AIProviderDao
import com.aicode.feature.settings.data.local.entity.AIProviderEntity
import com.aicode.feature.workspace.data.local.dao.RemoteConnectionDao
import com.aicode.feature.workspace.data.local.entity.RemoteConnectionEntity
import com.aicode.feature.workspace.data.local.entity.RemoteMountEntity

@Database(
    entities = [AgentMessageEntity::class, ChatSessionEntity::class, AIProviderEntity::class, RemoteConnectionEntity::class, RemoteMountEntity::class, TodoItemEntity::class, GitCredentialEntity::class],
    version = 25,
    exportSchema = false
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun agentMessageDao(): AgentMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun aiProviderDao(): AIProviderDao
    abstract fun remoteConnectionDao(): RemoteConnectionDao
    abstract fun todoItemDao(): TodoItemDao
    abstract fun gitCredentialDao(): GitCredentialDao
}
