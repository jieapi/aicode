package com.aicodeeditor.feature.settings.data.remote

import android.content.Context
import com.aicodeeditor.feature.settings.domain.model.ModelMetadata
import com.aicodeeditor.feature.settings.domain.model.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelMetadataService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: Cache? = null

    @Volatile
    private var refreshAttemptedThisProcess = false

    suspend fun resolve(type: ProviderType, modelId: String): ModelMetadata = withContext(Dispatchers.IO) {
        val catalog = loadCatalog().getOrNull().orEmpty()
        findMetadata(catalog, type, modelId) ?: infer(type, modelId)
    }

    suspend fun resolveAll(type: ProviderType, modelIds: List<String>): Map<String, ModelMetadata> =
        withContext(Dispatchers.IO) {
            val catalog = loadCatalog().getOrNull().orEmpty()
            modelIds.associateWith { modelId ->
                findMetadata(catalog, type, modelId) ?: infer(type, modelId)
            }
        }

    suspend fun refreshAll(type: ProviderType, modelIds: List<String>): Result<Map<String, ModelMetadata>> =
        withContext(Dispatchers.IO) {
            refreshCatalogOncePerProcess().map { catalog ->
                modelIds.associateWith { modelId ->
                    findMetadata(catalog, type, modelId) ?: infer(type, modelId)
                }
            }
        }

    fun infer(type: ProviderType, modelId: String): ModelMetadata {
        val model = modelId.lowercase()
        val context = when {
            "gemini-1.5" in model || "gemini-2." in model || "gemini-3" in model -> 1_000_000
            "claude" in model -> 200_000
            "gpt-4.1" in model || "gpt-4o" in model || "gpt-5" in model ||
                model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4") -> 128_000
            "deepseek" in model -> 64_000
            "qwen" in model && ("1m" in model || "1000k" in model) -> 1_000_000
            "qwen" in model && ("235b" in model || "coder" in model) -> 128_000
            "kimi" in model || "moonshot" in model -> 128_000
            "llama" in model && "128" in model -> 128_000
            "llama" in model -> 32_000
            else -> 128_000
        }
        return ModelMetadata(
            id = modelId,
            providerId = type.name.lowercase(),
            displayName = modelId,
            contextTokens = context,
            outputTokens = 20_000,
            supportsTools = type != ProviderType.GEMINI || "gemini" in model,
            supportsVision = "vision" in model || "gpt-4o" in model || "gemini" in model || "claude" in model,
            supportsReasoning = "thinking" in model || "reason" in model || model.startsWith("o") || "deepseek" in model,
            source = ModelMetadata.Source.INFERRED
        )
    }

    private fun findMetadata(
        catalog: Map<String, Map<String, ModelMetadata>>,
        type: ProviderType,
        modelId: String
    ): ModelMetadata? {
        val normalized = modelId.removePrefix("models/")
        val preferredProviders = when (type) {
            ProviderType.OPENAI -> listOf(
                "openai", "openrouter", "deepseek", "groq", "xai", "mistral",
                "togetherai", "alibaba", "moonshot", "github-copilot"
            )
            ProviderType.ANTHROPIC -> listOf("anthropic", "google-vertex-anthropic")
            ProviderType.GEMINI -> listOf("google", "google-vertex")
        }

        for (provider in preferredProviders) {
            catalog[provider]?.get(normalized)?.let { return it }
        }
        return catalog.values.firstNotNullOfOrNull { models -> models[normalized] }
    }

    private fun loadCatalog(): Result<Map<String, Map<String, ModelMetadata>>> {
        cached?.let {
            return Result.success(it.catalog)
        }

        loadCatalogFromDisk()?.let {
            cached = it
            return Result.success(it.catalog)
        }

        return fetchCatalogFromNetwork()
    }

    private fun refreshCatalogOncePerProcess(): Result<Map<String, Map<String, ModelMetadata>>> {
        if (refreshAttemptedThisProcess) {
            return Result.success(cached?.catalog ?: loadCatalogFromDisk()?.catalog.orEmpty())
        }
        refreshAttemptedThisProcess = true
        return fetchCatalogFromNetwork()
    }

    private fun fetchCatalogFromNetwork(): Result<Map<String, Map<String, ModelMetadata>>> =
        runCatching {
            val request = Request.Builder()
                .url(MODELS_DEV_URL)
                .header("User-Agent", "aicode")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(200)}")
                writeCatalogCache(body)
                parseCatalog(json.parseToJsonElement(body))
            }.also {
                cached = Cache(System.currentTimeMillis(), it)
            }
        }

    private fun loadCatalogFromDisk(): Cache? {
        val file = cacheFile()
        if (!file.isFile) return null
        return runCatching {
            val body = file.readText(Charsets.UTF_8)
            val loadedAtMs = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
            Cache(loadedAtMs, parseCatalog(json.parseToJsonElement(body)))
        }.getOrNull()
    }

    private fun writeCatalogCache(body: String) {
        runCatching {
            val file = cacheFile()
            file.parentFile?.mkdirs()
            file.writeText(body, Charsets.UTF_8)
        }
    }

    private fun cacheFile(): File =
        File(File(context.filesDir, "aicode"), CACHE_FILE_NAME)

    private fun parseCatalog(root: JsonElement): Map<String, Map<String, ModelMetadata>> {
        return root.jsonObject.mapValues { (providerId, providerEl) ->
            val models = providerEl.jsonObject["models"]?.jsonObject.orEmpty()
            models.mapValues { (_, modelEl) ->
                val model = modelEl.jsonObject
                val limit = model["limit"]?.jsonObject
                val modalities = model["modalities"]?.jsonObject
                val inputModalities = modalities?.get("input")?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.content }
                    .orEmpty()
                ModelMetadata(
                    id = model["id"]?.jsonPrimitive?.content ?: "",
                    providerId = providerId,
                    displayName = model["name"]?.jsonPrimitive?.content ?: model["id"]?.jsonPrimitive?.content.orEmpty(),
                    contextTokens = limit?.get("context")?.jsonPrimitive?.intOrNull ?: 0,
                    inputTokens = limit?.get("input")?.jsonPrimitive?.intOrNull,
                    outputTokens = limit?.get("output")?.jsonPrimitive?.intOrNull,
                    supportsTools = model["tool_call"]?.jsonPrimitive?.booleanOrNull == true,
                    supportsVision = "image" in inputModalities || "video" in inputModalities || "pdf" in inputModalities,
                    supportsReasoning = model["reasoning"]?.jsonPrimitive?.booleanOrNull == true,
                    source = ModelMetadata.Source.MODELS_DEV
                )
            }
        }
    }

    private data class Cache(
        val loadedAtMs: Long,
        val catalog: Map<String, Map<String, ModelMetadata>>
    )

    private companion object {
        const val MODELS_DEV_URL = "https://models.dev/api.json"
        const val CACHE_FILE_NAME = "models-dev-api.json"
    }
}
