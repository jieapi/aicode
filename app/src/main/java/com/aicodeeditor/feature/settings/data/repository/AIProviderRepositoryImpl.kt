package com.aicodeeditor.feature.settings.data.repository

import com.aicodeeditor.feature.settings.data.local.dao.AIProviderDao
import com.aicodeeditor.feature.settings.data.local.entity.AIProviderEntity
import com.aicodeeditor.feature.settings.domain.model.AIProviderConfig
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import com.aicodeeditor.feature.settings.domain.repository.AIProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIProviderRepositoryImpl @Inject constructor(
    private val aiProviderDao: AIProviderDao
) : AIProviderRepository {

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
        val entity = provider.toEntity()
        if (provider.isActive) {
            aiProviderDao.deactivateAllProviders()
        }
        aiProviderDao.insertProvider(entity)
    }

    override suspend fun deleteProvider(id: String) {
        aiProviderDao.deleteProvider(id)
    }

    override suspend fun setActiveProvider(id: String) {
        aiProviderDao.deactivateAllProviders()
        aiProviderDao.activateProvider(id)
    }

    override suspend fun setSelectedModel(id: String, model: String) {
        aiProviderDao.setSelectedModel(id, model)
    }

    override suspend fun updateModels(id: String, models: List<String>) {
        aiProviderDao.setModels(id, models.joinToString("\n"))
    }

    override suspend fun initializeDefaultProvidersIfEmpty() {
        val currentActive = aiProviderDao.getActiveProviderSync()
        if (currentActive == null) {
            // Check if any providers exist
            val allProvidersFlow = aiProviderDao.getAllProviders()
            // We only insert defaults if there's no active provider to ensure the user has something to start with

            val defaultOpenAI = AIProviderEntity(
                id = "default_openai",
                name = "OpenAI",
                type = ProviderType.OPENAI.name,
                apiKey = "", // User needs to configure this
                baseUrl = "https://api.openai.com/",
                defaultModel = "gpt-4o",
                isActive = true,
                models = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo").joinToString("\n"),
                selectedModel = "gpt-4o"
            )

            val defaultAnthropic = AIProviderEntity(
                id = "default_anthropic",
                name = "Anthropic",
                type = ProviderType.ANTHROPIC.name,
                apiKey = "",
                baseUrl = "https://api.anthropic.com/",
                defaultModel = "claude-3-5-sonnet-20241022",
                isActive = false,
                models = listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229").joinToString("\n"),
                selectedModel = "claude-3-5-sonnet-20241022"
            )

            aiProviderDao.insertProvider(defaultOpenAI)
            aiProviderDao.insertProvider(defaultAnthropic)
        }
    }

    private fun AIProviderEntity.toDomainModel(): AIProviderConfig {
        val modelList = models.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        return AIProviderConfig(
            id = id,
            name = name,
            type = try { ProviderType.valueOf(type) } catch (e: Exception) { ProviderType.CUSTOM },
            apiKey = apiKey,
            baseUrl = baseUrl,
            defaultModel = defaultModel,
            isActive = isActive,
            models = modelList,
            selectedModel = selectedModel.ifBlank { defaultModel }
        )
    }

    private fun AIProviderConfig.toEntity(): AIProviderEntity {
        return AIProviderEntity(
            id = id,
            name = name,
            type = type.name,
            apiKey = apiKey,
            baseUrl = baseUrl,
            defaultModel = defaultModel,
            isActive = isActive,
            models = models.joinToString("\n"),
            selectedModel = selectedModel.ifBlank { defaultModel }
        )
    }
}
