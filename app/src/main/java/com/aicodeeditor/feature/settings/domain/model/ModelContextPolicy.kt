package com.aicodeeditor.feature.settings.domain.model

object ModelContextPolicy {
    const val DEFAULT_CONTEXT_TOKENS = 128_000
    const val DEFAULT_OUTPUT_RESERVE_TOKENS = 20_000
    const val MIN_PRESERVE_RECENT_TOKENS = 2_000
    const val MAX_PRESERVE_RECENT_TOKENS = 8_000
    const val CHARS_PER_TOKEN = 4

    fun usableInputTokens(metadata: ModelMetadata): Int {
        val context = metadata.contextTokens.takeIf { it > 0 } ?: DEFAULT_CONTEXT_TOKENS
        val output = metadata.outputTokens?.takeIf { it > 0 } ?: DEFAULT_OUTPUT_RESERVE_TOKENS
        val reserved = minOf(DEFAULT_OUTPUT_RESERVE_TOKENS, output)
        return metadata.inputTokens
            ?.let { (it - reserved).coerceAtLeast(MIN_PRESERVE_RECENT_TOKENS) }
            ?: (context - reserved).coerceAtLeast(MIN_PRESERVE_RECENT_TOKENS)
    }

    fun preserveRecentTokens(usableTokens: Int): Int =
        (usableTokens / 4).coerceIn(MIN_PRESERVE_RECENT_TOKENS, MAX_PRESERVE_RECENT_TOKENS)

    fun estimateTokens(chars: Int): Int =
        (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
}

