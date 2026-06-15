package com.aicodeeditor.feature.settings.domain.model

data class AIProviderConfig(
    val id: String,
    val name: String,
    val type: ProviderType,
    val apiKey: String,
    val baseUrl: String,
    val defaultModel: String,
    val isActive: Boolean
)

enum class ProviderType {
    OPENAI, ANTHROPIC, CUSTOM
}
