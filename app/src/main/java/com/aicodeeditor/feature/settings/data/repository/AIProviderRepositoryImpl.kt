package com.aicodeeditor.feature.settings.data.repository

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.settings.data.local.dao.AIProviderDao
import com.aicodeeditor.feature.settings.data.local.entity.AIProviderEntity
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIProviderRepositoryImpl @Inject constructor(
    private val aiProviderDao: AIProviderDao
) : AIProviderRepository {

    private companion object {
        const val TAG = "AIProviderRepo"
    }

    override fun getAllProviders(): Flow<List<AIProviderConfig>> {
        return aiProviderDao.getAllProviders().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getActiveProvider(): Flow<AIProviderConfig?> {
        return aiProviderDao.getActiveProvider().map { it?.toDomainModel() }
    }

    override suspend fun getActiveProviderSync(): AIProviderConfig? {
        return aiProviderDao.getActiveProviderSync()?.toDomainModel()
    }

    override suspend fun getProviderById(id: String): AIProviderConfig? {
        return aiProviderDao.getProviderById(id)?.toDomainModel()
    }

    override suspend fun saveProvider(provider: AIProviderConfig) {
        FileLogger.i(TAG, "保存提供商 id=${provider.id} name=${provider.name} active=${provider.isActive}")
        val entity = provider.toEntity()
        if (provider.isActive) {
            aiProviderDao.deactivateAllProviders()
        }
        aiProviderDao.insertProvider(entity)
    }

    override suspend fun deleteProvider(id: String) {
        FileLogger.i(TAG, "删除提供商 id=$id")
        aiProviderDao.deleteProvider(id)
    }

    override suspend fun setActiveProvider(id: String) {
        FileLogger.i(TAG, "切换启用提供商 id=$id")
        aiProviderDao.deactivateAllProviders()
        aiProviderDao.activateProvider(id)
    }

    override suspend fun setSelectedModel(id: String, model: String) {
        FileLogger.i(TAG, "切换模型 provider=$id model=$model")
        aiProviderDao.setSelectedModel(id, model)
    }

    override suspend fun updateModels(id: String, models: List<String>) {
        FileLogger.d(TAG, "更新模型列表 provider=$id 共 ${models.size} 个")
        aiProviderDao.setModels(id, models.joinToString("\n"))
    }

    override suspend fun setProviderEnabled(id: String, isEnabled: Boolean) {
        FileLogger.i(TAG, "设置提供商状态 provider=$id isEnabled=$isEnabled")
        aiProviderDao.setProviderEnabled(id, isEnabled)
    }

    override suspend fun ensureActiveProvider() {
        // 已有激活项则无需处理。
        if (aiProviderDao.getActiveProviderSync() != null) return
        val first = aiProviderDao.getAllProviders().first().firstOrNull() ?: return
        FileLogger.i(TAG, "无激活提供商，自动激活首个: ${first.id} (${first.name})")
        aiProviderDao.deactivateAllProviders()
        aiProviderDao.activateProvider(first.id)
    }

    private fun AIProviderEntity.toDomainModel(): AIProviderConfig {
        val modelList = models.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        return AIProviderConfig(
            id = id,
            name = name,
            type = try { ProviderType.valueOf(type) } catch (e: Exception) { ProviderType.OPENAI },
            apiKey = apiKey,
            baseUrl = baseUrl,
            defaultModel = defaultModel,
            isActive = isActive,
            models = modelList,
            selectedModel = selectedModel.ifBlank { defaultModel },
            isEnabled = isEnabled,
            apiPath = apiPath,
            useResponseApi = useResponseApi
        )
    }

    private fun AIProviderConfig.toEntity(): AIProviderEntity {
        return AIProviderEntity(
            id = id,
            name = name,
            type = type.name,
            apiKey = apiKey,
            baseUrl = baseUrl,
            apiPath = apiPath,
            defaultModel = defaultModel,
            isActive = isActive,
            models = models.joinToString("\n"),
            selectedModel = selectedModel,
            isEnabled = isEnabled,
            useResponseApi = useResponseApi
        )
    }
}
