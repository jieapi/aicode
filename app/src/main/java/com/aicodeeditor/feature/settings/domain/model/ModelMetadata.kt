package com.aicodeeditor.feature.settings.domain.model

data class ModelMetadata(
    val id: String,
    val providerId: String? = null,
    val displayName: String = id,
    val contextTokens: Int,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val supportsTools: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val source: Source = Source.INFERRED
) {
    enum class Source {
        MODELS_DEV,
        INFERRED
    }
}

