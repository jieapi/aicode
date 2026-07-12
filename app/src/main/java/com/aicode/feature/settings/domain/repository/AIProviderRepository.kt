package com.aicode.feature.settings.domain.repository

import com.aicode.feature.settings.domain.model.AIProviderConfig
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
    suspend fun setProviderEnabled(id: String, isEnabled: Boolean)
    /**
     * 若当前没有任何激活提供商、但库中存在提供商，则自动激活首个。
     * 防止用户添加/编辑提供商后无激活项，导致主页模型切换胶囊（依赖 activeProvider）消失。
     */
    suspend fun ensureActiveProvider()
}
