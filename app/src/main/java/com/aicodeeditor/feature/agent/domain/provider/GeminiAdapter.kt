package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.core.util.AILogger
import com.aicodeeditor.feature.agent.data.remote.gemini.GeminiApi
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.IOException

class GeminiAdapter @Inject constructor(
    private val api: GeminiApi
) : AIProvider {

    override var apiKey = ""
    override var baseUrl = "https://generativelanguage.googleapis.com/"
    override var apiPath = "v1beta/models"
    override var useResponseApi = false
    override var model = "gemini-1.5-flash"
    override var logSessionId: String? = null

    override suspend fun complete(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): AIResponse {
        val geminiContents = convertToGeminiContents(messages)
        val toolDefs = tools.takeIf { it.isNotEmpty() }?.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.toJsonSchema()
            )
        }?.let { listOf(mapOf("functionDeclarations" to it)) }

        val request = mutableMapOf<String, Any>(
            "contents" to geminiContents
        )
        if (systemPrompt.isNotBlank()) {
            request["systemInstruction"] = mapOf(
                "role" to "system",
                "parts" to listOf(mapOf("text" to systemPrompt))
            )
        }
        if (toolDefs != null) {
            request["tools"] = toolDefs
        }

        val path = if (apiPath.endsWith("/")) "$apiPath$model:generateContent" else "$apiPath/$model:generateContent"
        val url = joinUrl(baseUrl, path)
        AILogger.logRequest(logSessionId, "Gemini", model, "POST", url, request)

        val response = try {
            retryStaircase {
                api.generateContent(url = url, apiKey = apiKey, request = request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val enriched = e.enrichWithHttpErrorBody()
            AILogger.logError(logSessionId, "Gemini", enriched)
            throw enriched
        }
        AILogger.logResponse(logSessionId, "Gemini", response)

        var contentText = ""
        val toolCalls = mutableListOf<ToolCall>()
        var finishReason: String? = null

        val candidates = response.getAsJsonArray("candidates")
        candidates?.firstOrNull()?.asJsonObject?.let { candidate ->
            finishReason = candidate.get("finishReason")?.asString
            val content = candidate.getAsJsonObject("content")
            content?.getAsJsonArray("parts")?.forEach { partEl ->
                val part = partEl.asJsonObject
                if (part.has("text")) {
                    contentText += part.get("text").asString
                }
                if (part.has("functionCall")) {
                    val fnCall = part.getAsJsonObject("functionCall")
                    val name = fnCall.get("name")?.asString ?: ""
                    val argsStr = fnCall.getAsJsonObject("args")?.toString() ?: "{}"
                    val argsJson = parseArgs(argsStr)
                    toolCalls.add(ToolCall(id = name, name = name, arguments = argsJson))
                }
            }
        }

        return AIResponse(content = contentText, toolCalls = toolCalls, stopReason = finishReason)
    }

    override fun completeStream(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): Flow<AIStreamChunk> = flow {
        val geminiContents = convertToGeminiContents(messages)
        val toolDefs = tools.takeIf { it.isNotEmpty() }?.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "parameters" to tool.toJsonSchema()
            )
        }?.let { listOf(mapOf("functionDeclarations" to it)) }

        val request = mutableMapOf<String, Any>(
            "contents" to geminiContents
        )
        if (systemPrompt.isNotBlank()) {
            request["systemInstruction"] = mapOf(
                "role" to "system",
                "parts" to listOf(mapOf("text" to systemPrompt))
            )
        }
        if (toolDefs != null) {
            request["tools"] = toolDefs
        }

        val path = if (apiPath.endsWith("/")) "$apiPath$model:streamGenerateContent?alt=sse" else "$apiPath/$model:streamGenerateContent?alt=sse"
        val url = joinUrl(baseUrl, path)
        
        AILogger.logRequest(logSessionId, "Gemini", model, "POST", url, request)
        val rawSse = StringBuilder()

        try {
            streamWithStaircaseRetry(
                attemptOnce = { onProduced ->
                val textBuilder = StringBuilder()
                val toolCalls = mutableListOf<ToolCall>()
                var currentFinishReason: String? = null

                val body = api.streamGenerateContent(url = url, apiKey = apiKey, request = request)

                body.use { rb ->
                    // 首字节超时 watchdog：60s 内未收到首个内容块则关闭流，触发可重试的 IOException。
                    val firstByteReceived = java.util.concurrent.atomic.AtomicBoolean(false)
                    val watchdog = launchFirstByteWatchdog({ rb.close() }) { firstByteReceived.get() }
                    val closeHandle = coroutineContext[Job]?.invokeOnCompletion {
                        runCatching { rb.close() }
                    }
                    try {
                        val reader = rb.charStream().buffered()
                        while (true) {
                            coroutineContext.ensureActive()
                            val line = reader.readLine()
                                ?: throw IOException("SSE 流被中断（疑似网络断开）")
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data.isEmpty()) continue
                            rawSse.append(line).append('\n')
                            val obj = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                            
                            try {
                                val chunkCandidates = obj.getAsJsonArray("candidates")
                                chunkCandidates?.firstOrNull()?.asJsonObject?.let { candidate ->
                                    val reason = candidate.get("finishReason")?.takeIf { !it.isJsonNull }?.asString
                                    if (reason != null && reason != "null") currentFinishReason = reason

                                    val content = candidate.getAsJsonObject("content")
                                    content?.getAsJsonArray("parts")?.forEach { partEl ->
                                        val part = partEl.asJsonObject
                                        if (part.has("text")) {
                                            val text = part.get("text")?.asString ?: ""
                                            if (text.isNotEmpty()) {
                                                textBuilder.append(text)
                                                if (firstByteReceived.compareAndSet(false, true)) watchdog.cancel()
                                                onProduced()
                                                emit(AIStreamChunk.TextDelta(text))
                                            }
                                        }
                                        if (part.has("functionCall")) {
                                            val fnCall = part.getAsJsonObject("functionCall")
                                            val name = fnCall.get("name")?.asString ?: ""
                                            val argsStr = fnCall.getAsJsonObject("args")?.toString() ?: "{}"
                                            val argsJson = parseArgs(argsStr)
                                            toolCalls.add(ToolCall(id = name, name = name, arguments = argsJson))
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                coroutineContext.ensureActive()
                                // ignore
                            }
                        }
                    } finally {
                        watchdog.cancel()
                        closeHandle?.dispose()
                    }
                }

                onProduced()
                emit(AIStreamChunk.Final(AIResponse(content = textBuilder.toString(), toolCalls = toolCalls, stopReason = currentFinishReason)))
                },
                onRetry = { attempt, max -> emit(AIStreamChunk.Retrying(attempt, max)) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            val enriched = e.enrichWithHttpErrorBody()
            AILogger.logError(logSessionId, "Gemini", enriched)
            throw enriched
        } finally {
            AILogger.logResponseStream(logSessionId, "Gemini", rawSse.toString())
        }
    }.flowOn(Dispatchers.IO)

    private fun parseArgs(raw: String): kotlinx.serialization.json.JsonObject {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return kotlinx.serialization.json.JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(trimmed).jsonObject }.getOrElse { kotlinx.serialization.json.JsonObject(emptyMap()) }
    }

    private fun convertToGeminiContents(messages: List<AgentMessage>): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        for (message in messages) {
            when (message) {
                is AgentMessage.UserMessage -> {
                    result.add(
                        mapOf(
                            "role" to "user",
                            "parts" to listOf(mapOf("text" to message.content))
                        )
                    )
                }
                is AgentMessage.AssistantMessage -> {
                    val parts = mutableListOf<Map<String, Any>>()
                    if (message.content.isNotEmpty()) {
                        parts.add(mapOf("text" to message.content))
                    }
                    for (toolCall in message.toolCalls) {
                        parts.add(
                            mapOf(
                                "functionCall" to mapOf(
                                    "name" to toolCall.name,
                                    "args" to toolCall.arguments
                                )
                            )
                        )
                    }
                    if (parts.isNotEmpty()) {
                        result.add(
                            mapOf(
                                "role" to "model",
                                "parts" to parts
                            )
                        )
                    }
                }
                is AgentMessage.ToolResultMessage -> {
                    result.add(
                        mapOf(
                            "role" to "user",
                            "parts" to listOf(
                                mapOf(
                                    "functionResponse" to mapOf(
                                        "name" to message.id, // For Gemini, we typically use the name as ID
                                        "response" to mapOf(
                                            "result" to message.result
                                        )
                                    )
                                )
                            )
                        )
                    )
                }
            }
        }
        return result
    }
}
