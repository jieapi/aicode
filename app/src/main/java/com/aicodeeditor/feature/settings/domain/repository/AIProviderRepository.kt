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
    /**
     * 若当前没有任何激活服务商、但库中存在服务商，则自动激活首个。
     * 防止用户添加/编辑服务商后无激活项，导致主页模型切换胶囊（依赖 activeProvider）消失。
     */
    suspend fun ensureActiveProvider()
}
