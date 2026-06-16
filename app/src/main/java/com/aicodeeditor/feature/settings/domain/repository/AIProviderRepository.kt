package com.aicodeeditor.feature.settings.domain.repository

import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import kotlinx.coroutines.flow.Flow

interface AIProviderRepository {
    fun getAllProviders(): Flow<List<AIProviderConfig>>
    fun getActiveProvider(): Flow<AIProviderConfig?>
    suspend fun getActiveProviderSync(): AIProviderConfig?
    suspend fun getProviderById(id: String): AIProviderConfig?
    suspend fun saveProvider(provider: AIProviderConfig)
    suspend fun deleteProvider(id: String)
    suspend fun setActiveProvider(id: String)
    suspend fun setSelectedModel(id: String, model: String)
    suspend fun updateModels(id: String, models: List<String>)
    suspend fun initializeDefaultProvidersIfEmpty()
}
