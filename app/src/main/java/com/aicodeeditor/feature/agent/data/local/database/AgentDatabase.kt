package com.aicodeeditor.feature.agent.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aicodeeditor.feature.agent.data.local.dao.AgentMessageDao
import com.aicodeeditor.feature.agent.data.local.entity.AgentMessageEntity
import com.aicodeeditor.feature.settings.data.local.dao.AIProviderDao
import com.aicodeeditor.feature.settings.data.local.entity.AIProviderEntity

@Database(
    entities = [AgentMessageEntity::class, AIProviderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun agentMessageDao(): AgentMessageDao
    abstract fun aiProviderDao(): AIProviderDao
}
