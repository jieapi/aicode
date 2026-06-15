package com.aicodeeditor.feature.settings.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_providers")
data class AIProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // "openai", "anthropic"
    val apiKey: String, // TODO: Consider encryption for real production
    val baseUrl: String,
    val defaultModel: String,
    val isActive: Boolean
)
