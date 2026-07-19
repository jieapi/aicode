package com.aicode.feature.agent.domain.provider

import com.aicode.feature.agent.data.remote.anthropic.AnthropicApi
import com.aicode.feature.agent.data.remote.anthropic.AnthropicMessageRequest
import com.aicode.feature.agent.data.remote.anthropic.AnthropicMessage
import com.aicode.feature.agent.data.remote.anthropic.AnthropicContentBlock
import com.aicode.feature.agent.data.remote.anthropic.AnthropicToolDefinition
import com.aicode.core.util.AILogger
import com.aicode.feature.agent.domain.model.AgentImage
import com.aicode.feature.agent.domain.model.AgentMessage
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ToolCall
import com.aicode.feature.settings.domain.model.ProviderType
import com.aicode.feature.settings.domain.model.defaultProviderApiPath
import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class AnthropicAdapter @Inject constructor(
    private val api: AnthropicApi
) : AIProvider {

    override var apiKey = ""
    override var baseUrl = "https://api.anthropic.com/"
    override var useFullUrl = false
    override var useResponseApi = false
    override var model = "claude-3-5-sonnet-20241022"
    override var logSessionId: String? = null

    override suspend fun complete(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): AIResponse {
        val anthropicMessages = convertToAnthropicMessages(messages)

        val toolDefs = tools.takeIf { it.isNotEmpty() }?.map { tool ->
            AnthropicToolDefinition(
                name = tool.name,
                description = tool.description,
                input_schema = tool.toJsonSchema()
            )
        }

        val url = if (useFullUrl) baseUrl else joinUrl(baseUrl, defaultProviderApiPath(ProviderType.ANTHROPIC))
        val request = AnthropicMessageRequest(
            model = model,
            messages = anthropicMessages,
            system = systemPrompt.ifBlank { null },
            tools = toolDefs,
            stream = false
        )
        AILogger.logRequest(logSessionId, "Anthropic", model, "POST", url, request)

        val response = try {
            retryStaircase {
                api.createMessage(url = url, apiKey = apiKey, request = request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val enriched = e.enrichWithHttpErrorBody()
            AILogger.logError(logSessionId, "Anthropic", enriched)
            throw enriched
        }
        AILogger.logResponse(logSessionId, "Anthropic", response)

        var contentText = ""
        val toolCalls = mutableListOf<ToolCall>()

        for (block in response.content) {
            when (block.type) {
                "text" -> contentText += block.text ?: ""
                "tool_use" -> {
                    val arguments = block.input?.let { mapToJson(it) } ?: JsonObject(emptyMap())
                    toolCalls.add(
                        ToolCall(
                            id = block.id ?: "",
                            name = block.name ?: "",
                            arguments = arguments
                        )
                    )
                }
            }
        }

        return AIResponse(content = contentText, toolCalls = toolCalls, stopReason = response.stop_reason, inputTokens = response.usage.input_tokens, outputTokens = response.usage.output_tokens)
    }

    override fun completeStream(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): Flow<AIStreamChunk> = flow {
        val anthropicMessages = convertToAnthropicMessages(messages)
        val toolDefs = tools.takeIf { it.isNotEmpty() }?.map { tool ->
            AnthropicToolDefinition(
                name = tool.name,
                description = tool.description,
                input_schema = tool.toJsonSchema()
            )
        }

        val url = if (useFullUrl) baseUrl else joinUrl(baseUrl, defaultProviderApiPath(ProviderType.ANTHROPIC))
        val request = AnthropicMessageRequest(
            model = model,
            messages = anthropicMessages,
            system = systemPrompt.ifBlank { null },
            tools = toolDefs,
            stream = true
        )
        AILogger.logRequest(logSessionId, "Anthropic", model, "POST", url, request)
        // 累积原始 SSE，整轮结束（或失败）后整体落盘，避免高频写盘。
        val rawSse = StringBuilder()

        // 首字节前失败可安全重试；一旦开始吐字（onProduced 已调用）再失败则上抛，避免重复文本。
        try {
            streamWithStaircaseRetry(
                attemptOnce = { onProduced ->
            val textBuilder = StringBuilder()
            // content block index -> 累积中的 tool_use（仅 tool_use 块建条目，保序）。
            val toolBlocks = LinkedHashMap<Int, ToolBlockAcc>()
            var stopReason: String? = null
            var streamInputTokens = 0
            var streamOutputTokens = 0

            val body = api.streamMessage(url = url, apiKey = apiKey, request = request)

            body.use { rb ->
                // 首字节超时 watchdog：60s 内未收到首个内容块则关闭流，触发可重试的 IOException。
                val firstByteReceived = java.util.concurrent.atomic.AtomicBoolean(false)
                val watchdog = launchFirstByteWatchdog({ rb.close() }) { firstByteReceived.get() }
                val closeHandle = coroutineContext[Job]?.invokeOnCompletion {
                    runCatching { rb.close() }
                }
                try {
                    val reader = rb.charStream().buffered()
                    // 收到服务端 message_stop 事件即 break 正常结束；readLine() 返回 null 则视为
                    // 流被异常截断（网络中断/TCP 重置/readTimeout），必须抛异常让重试/日志接管——
                    // 否则原本会用截断数据「正常完成」，表现为 AI 突然中断且无任何错误日志。
                    // （收到 message_stop 即 break，故走到 readLine()==null 时必然未收到过结束标记。）
                    while (true) {
                        coroutineContext.ensureActive()
                        val line = reader.readLine()
                            ?: throw IOException("SSE 流被中断：未收到 message_stop 结束标记（疑似网络断开）")
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty()) continue
                        rawSse.append(line).append('\n')
                        val obj = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                        // 单行 SSE 解析：不同上游/模型的字段类型偶有出入，Gson 的 getAsJsonObject/getAsJsonArray
                        // 在类型不符时会直接抛 ClassCastException，asString/asInt 对非原始值抛 UnsupportedOperationException。
                        // 单行异常不应中断整条流——宽松解析，出错仅跳过该行；必须放行 CancellationException。
                        try {
                            when (obj.get("type")?.asString) {
                                "error" -> {
                                    val errObj = obj.getAsJsonObject("error")
                                    val code = errObj?.get("type")?.takeIf { !it.isJsonNull }?.asString
                                    val msg = errObj?.get("message")?.takeIf { !it.isJsonNull }?.asString ?: "未知错误"
                                    throw StreamApiException(code, msg)
                                }
                                "message_start" -> {
                                    val usage = obj.get("message")?.takeIf { it.isJsonObject }?.asJsonObject
                                        ?.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject
                                    streamInputTokens = usage?.get("input_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                                }
                                "content_block_start" -> {
                                    val index = obj.get("index")?.asInt ?: continue
                                    val block = obj.getAsJsonObject("content_block")
                                    if (block?.get("type")?.asString == "tool_use") {
                                        toolBlocks[index] = ToolBlockAcc(
                                            id = block.get("id")?.asString ?: "",
                                            name = block.get("name")?.asString ?: ""
                                        )
                                    }
                                }
                                "content_block_delta" -> {
                                    val delta = obj.getAsJsonObject("delta") ?: continue
                                    when (delta.get("type")?.asString) {
                                        "text_delta" -> {
                                            val t = delta.get("text")?.asString ?: ""
                                            if (t.isNotEmpty()) {
                                                textBuilder.append(t)
                                                if (firstByteReceived.compareAndSet(false, true)) watchdog.cancel()
                                                onProduced()
                                                emit(AIStreamChunk.TextDelta(t))
                                            }
                                        }
                                        "thinking_delta" -> {
                                            val t = delta.get("thinking")?.asString ?: ""
                                            if (t.isNotEmpty()) {
                                                // 思考内容不落库、可重试重流出，但收到即说明连接已活，取消首字节超时。
                                                if (firstByteReceived.compareAndSet(false, true)) watchdog.cancel()
                                                emit(AIStreamChunk.ReasoningDelta(t))
                                            }
                                        }
                                        "input_json_delta" -> {
                                            val index = obj.get("index")?.asInt
                                            val partial = delta.get("partial_json")?.asString ?: ""
                                            if (index != null) toolBlocks[index]?.args?.append(partial)
                                        }
                                    }
                                }
                                "message_stop" -> break
                                "message_delta" -> {
                                    val delta = obj.get("delta")?.takeIf { it.isJsonObject }?.asJsonObject
                                    delta?.get("stop_reason")?.takeIf { !it.isJsonNull }?.asString?.let {
                                        stopReason = it
                                    }
                                    val usage = obj.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject
                                    usage?.get("output_tokens")?.takeIf { !it.isJsonNull }?.asInt?.let {
                                        streamOutputTokens = it
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            coroutineContext.ensureActive()
                            // 该行 SSE 解析失败，跳过；不影响已累积文本与后续行。
                        }
                    }
                } finally {
                    watchdog.cancel()
                    closeHandle?.dispose()
                }
            }

            val toolCalls = toolBlocks.values.map { acc ->
                ToolCall(id = acc.id, name = acc.name, arguments = parseArgs(acc.args.toString()))
            }
            onProduced()
            emit(AIStreamChunk.Final(AIResponse(content = textBuilder.toString(), toolCalls = toolCalls, stopReason = stopReason, inputTokens = streamInputTokens, outputTokens = streamOutputTokens)))
                },
                onRetry = { attempt, max -> emit(AIStreamChunk.Retrying(attempt, max)) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            val enriched = e.enrichWithHttpErrorBody()
            AILogger.logError(logSessionId, "Anthropic", enriched)
            throw enriched
        } finally {
            // 无论成功/失败/取消，把已收到的原始 SSE 落盘（重试时会从上次中断处续写）。
            AILogger.logResponseStream(logSessionId, "Anthropic", rawSse.toString())
        }
    }.flowOn(Dispatchers.IO)

    /** 流式过程中按 content block index 累积的 tool_use 状态。 */
    private class ToolBlockAcc(val id: String, val name: String) {
        val args = StringBuilder()
    }

    /** 把累积的工具入参 JSON 字符串解析为 JsonObject；为空或非法时回退为空对象。 */
    private fun parseArgs(raw: String): JsonObject {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(trimmed).jsonObject }.getOrElse { JsonObject(emptyMap()) }
    }

    private fun convertToAnthropicMessages(messages: List<AgentMessage>): MutableList<AnthropicMessage> {
        val result = mutableListOf<AnthropicMessage>()
        // 防御性跟踪：上一个 assistant 消息是否包含 tool_use
        var lastAssistantHadToolUse = false

        for (message in messages) {
            when (message) {
                is AgentMessage.UserMessage -> {
                    result.add(AnthropicMessage(role = "user", content = message.toAnthropicUserContent()))
                    lastAssistantHadToolUse = false
                }
                is AgentMessage.AssistantMessage -> {
                    val contentBlocks = mutableListOf<AnthropicContentBlock>()
                    if (message.content.isNotEmpty()) {
                        contentBlocks.add(AnthropicContentBlock(type = "text", text = message.content))
                    }

                    for (toolCall in message.toolCalls) {
                         @Suppress("UNCHECKED_CAST")
                         val inputMap = jsonElementToMap(JsonObject(toolCall.arguments)) as Map<String, Any>

                         contentBlocks.add(
                            AnthropicContentBlock(
                                type = "tool_use",
                                id = toolCall.id,
                                name = toolCall.name,
                                input = inputMap
                            )
                        )
                    }

                    lastAssistantHadToolUse = message.toolCalls.isNotEmpty()

                    if (contentBlocks.isNotEmpty()) {
                        result.add(AnthropicMessage(role = "assistant", content = contentBlocks))
                    }
                }
                is AgentMessage.ToolResultMessage -> {
                    // 防御性清理：跳过没有配对 tool_use 的孤立 tool_result
                    if (!lastAssistantHadToolUse) continue
                    result.add(
                        AnthropicMessage(
                            role = "user",
                            content = listOf(
                                AnthropicContentBlock(
                                    type = "tool_result",
                                    tool_use_id = message.id,
                                    content = message.result
                                )
                            )
                        )
                    )
                }
            }
        }

        return result
    }

    /** Convert a Map<String, Any> (from Gson) to a JsonObject */
    private fun mapToJson(map: Map<String, Any>): JsonObject {
        val mutable = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for ((k, v) in map) {
            mutable[k] = when (v) {
                is String -> JsonPrimitive(v)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    mapToJson(v as Map<String, Any>)
                }
                else -> JsonPrimitive(v.toString())
            }
        }
        return JsonObject(mutable)
    }

    /** Convert a JsonObject back to Map<String, Any> for Anthropic API */
    private fun jsonElementToMap(element: kotlinx.serialization.json.JsonElement): Any {
        return when (element) {
            is JsonObject -> element.mapValues { (_, v) -> jsonElementToMap(v) }
            is kotlinx.serialization.json.JsonArray -> element.map { jsonElementToMap(it) }
            is JsonPrimitive -> element.contentOrNull ?: ""
        }
    }

    private fun AgentMessage.UserMessage.toAnthropicUserContent(): Any {
        if (images.isEmpty()) return content

        val blocks = mutableListOf<AnthropicContentBlock>()
        if (content.isNotBlank()) {
            blocks.add(AnthropicContentBlock(type = "text", text = content))
        }
        images.forEach { image ->
            blocks.add(image.toAnthropicImageBlock())
        }
        return blocks
    }

    private fun AgentImage.toAnthropicImageBlock(): AnthropicContentBlock {
        return AnthropicContentBlock(
            type = "image",
            source = mapOf(
                "type" to "base64",
                "media_type" to mimeType,
                "data" to base64Data
            )
        )
    }
}
