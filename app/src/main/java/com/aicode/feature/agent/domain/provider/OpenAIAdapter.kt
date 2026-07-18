package com.aicode.feature.agent.domain.provider

import com.aicode.core.util.AILogger
import com.aicode.feature.agent.data.remote.openai.OpenAIApi
import com.aicode.feature.settings.domain.model.ProviderType
import com.aicode.feature.settings.domain.model.defaultProviderApiPath
import java.io.IOException
import com.aicode.feature.agent.data.remote.openai.ChatCompletionRequest
import com.aicode.feature.agent.data.remote.openai.OpenAIChatMessage
import com.aicode.feature.agent.domain.model.AgentImage
import com.aicode.feature.agent.domain.model.AgentMessage
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ToolCall
import com.google.gson.JsonParser
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.aicode.feature.agent.data.remote.openai.OpenAIToolCall
import com.aicode.feature.agent.data.remote.openai.OpenAIToolDefinition
import com.aicode.feature.agent.data.remote.openai.OpenAIFunctionDefinition
import com.aicode.feature.agent.data.remote.openai.StreamOptions

class OpenAIAdapter @Inject constructor(
    private val api: OpenAIApi
) : AIProvider {

    override var apiKey = ""
    override var baseUrl = "https://api.openai.com/"
    override var useFullUrl = false
    override var useResponseApi = false
    override var model = "gpt-4-turbo"
    override var logSessionId: String? = null

    override suspend fun complete(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): AIResponse {
        val openAIMessages = buildList {
            if (systemPrompt.isNotBlank()) {
                val role = if (model.startsWith("o1") || model.startsWith("o3")) "developer" else "system"
                add(OpenAIChatMessage(role = role, content = systemPrompt))
            }
            addAll(convertToOpenAIMessages(messages, useResponseApi))
        }

        val toolDefs = tools.takeIf { it.isNotEmpty() }?.map { tool ->
            OpenAIToolDefinition(
                function = OpenAIFunctionDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.toJsonSchema()
                )
            )
        }

        val url = if (useFullUrl) baseUrl else joinUrl(baseUrl, defaultProviderApiPath(ProviderType.OPENAI))
        if (useResponseApi) {
            val request = mapOf(
                "model" to model,
                "input" to openAIMessages,
                "tools" to toolDefs
            )
            AILogger.logRequest(logSessionId, "OpenAI", model, "POST", url, request)

            val response = try {
                retryStaircase {
                    api.createResponses(url = url, authorization = "Bearer $apiKey", request = request)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val enriched = e.enrichWithHttpErrorBody()
                AILogger.logError(logSessionId, "OpenAI", enriched)
                throw enriched
            }
            AILogger.logResponse(logSessionId, "OpenAI", response)

            val outputs = response.getAsJsonArray("output")
            var content = ""
            val toolCalls = mutableListOf<ToolCall>()
            var finishReason: String? = null

            outputs?.forEach { out ->
                val msg = out.asJsonObject
                if (msg.get("role")?.asString == "assistant") {
                    msg.getAsJsonArray("content")?.forEach { partEl ->
                        val part = partEl.asJsonObject
                        when (part.get("type")?.asString) {
                            "output_text" -> content += part.get("text")?.asString ?: ""
                            "tool_call" -> {
                                val id = part.get("id")?.asString ?: ""
                                val name = part.get("name")?.asString ?: ""
                                val args = part.get("arguments")?.asString ?: ""
                                toolCalls.add(ToolCall(id, name, parseArgs(args)))
                            }
                        }
                    }
                }
            }
            // status of output items is completed
            finishReason = "stop" // simplify for Responses API
            val usage = response.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject
            val inputTokens = usage?.get("input_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val outputTokens = usage?.get("output_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            return AIResponse(content = content, toolCalls = toolCalls, stopReason = finishReason, inputTokens = inputTokens, outputTokens = outputTokens)
        }

        val request = ChatCompletionRequest(
            model = model,
            messages = openAIMessages,
            tools = toolDefs,
            tool_choice = if (toolDefs != null) "auto" else null,
            stream = false
        )
        AILogger.logRequest(logSessionId, "OpenAI", model, "POST", url, request)

        val response = try {
            retryStaircase {
                api.createChatCompletion(url = url, authorization = "Bearer $apiKey", request = request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val enriched = e.enrichWithHttpErrorBody()
            AILogger.logError(logSessionId, "OpenAI", enriched)
            throw enriched
        }
        AILogger.logResponse(logSessionId, "OpenAI", response)

        val message = response.choices.firstOrNull()?.message
        val finishReason = response.choices.firstOrNull()?.finish_reason
        val content = message?.content.asTextContent()
        val toolCalls = message?.tool_calls?.map { convertToToolCall(it) } ?: emptyList()
        val reasoning = message?.reasoning_content?.takeIf { it.isNotEmpty() }
        val usage = response.usage

        return AIResponse(content = content, toolCalls = toolCalls, stopReason = finishReason, reasoning = reasoning, inputTokens = usage?.prompt_tokens ?: 0, outputTokens = usage?.completion_tokens ?: 0)
    }

    override fun completeStream(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): Flow<AIStreamChunk> = flow {
        val openAIMessages = buildList {
            if (systemPrompt.isNotBlank()) {
                val role = if (model.startsWith("o1") || model.startsWith("o3")) "developer" else "system"
                add(OpenAIChatMessage(role = role, content = systemPrompt))
            }
            addAll(convertToOpenAIMessages(messages, useResponseApi))
        }
        val toolDefs = tools.takeIf { it.isNotEmpty() }?.map { tool ->
            OpenAIToolDefinition(
                function = OpenAIFunctionDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.toJsonSchema()
                )
            )
        }

        val url = if (useFullUrl) baseUrl else joinUrl(baseUrl, defaultProviderApiPath(ProviderType.OPENAI))
        
        if (useResponseApi) {
            val request = mapOf(
                "model" to model,
                "input" to openAIMessages,
                "tools" to toolDefs,
                "stream" to true
            )
            AILogger.logRequest(logSessionId, "OpenAI", model, "POST", url, request)
            val rawSse = StringBuilder()
            try {
                streamWithStaircaseRetry(attemptOnce = { onProduced ->
                    val textBuilder = StringBuilder()
                    val toolAccs = LinkedHashMap<Int, OpenAIToolAcc>()
                    var finishReason: String? = null
                    var streamInputTokens = 0
                    var streamOutputTokens = 0

                    val body = api.streamResponses(
                        url = url,
                        authorization = "Bearer $apiKey",
                        request = request
                    )

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
                                    ?: throw IOException("SSE 流被中断：未收到 [DONE] 结束标记（疑似网络断开）")
                                if (!line.startsWith("data:")) continue
                                val data = line.removePrefix("data:").trim()
                                if (data.isEmpty()) continue
                                rawSse.append(line).append('\n')
                                if (data == "[DONE]") break
                                val obj = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                                try {
                                    val eventType = obj.get("type")?.asString
                                    if (eventType == "response.output_text.delta") {
                                        val delta = obj.get("delta")?.asString ?: ""
                                        if (delta.isNotEmpty()) {
                                            textBuilder.append(delta)
                                            if (firstByteReceived.compareAndSet(false, true)) watchdog.cancel()
                                            onProduced()
                                            emit(AIStreamChunk.TextDelta(delta))
                                        }
                                    } else if (eventType == "response.completed") {
                                        val outputs = obj.getAsJsonObject("response")?.getAsJsonArray("output")
                                        outputs?.forEach { out ->
                                            val msg = out.asJsonObject
                                            if (msg.get("role")?.asString == "assistant") {
                                                msg.getAsJsonArray("content")?.forEach { partEl ->
                                                    val part = partEl.asJsonObject
                                                    if (part.get("type")?.asString == "tool_call") {
                                                        val id = part.get("id")?.asString ?: ""
                                                        val name = part.get("name")?.asString ?: ""
                                                        val args = part.get("arguments")?.asString ?: ""
                                                        val idx = toolAccs.size
                                                        val acc = toolAccs.getOrPut(idx) { OpenAIToolAcc() }
                                                        acc.id = id
                                                        acc.name = name
                                                        acc.args.append(args)
                                                    }
                                                }
                                            }
                                        }
                                        finishReason = "stop"
                                        val usageObj = obj.get("response")?.takeIf { it.isJsonObject }?.asJsonObject
                                            ?.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject
                                        streamInputTokens = usageObj?.get("input_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                                        streamOutputTokens = usageObj?.get("output_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    coroutineContext.ensureActive()
                                }
                            }
                        } finally {
                            watchdog.cancel()
                            closeHandle?.dispose()
                        }
                    }

                    val toolCalls = toolAccs.values
                        .filter { it.id.isNotEmpty() || it.name.isNotEmpty() }
                        .map { acc -> ToolCall(id = acc.id, name = acc.name, arguments = parseArgs(acc.args.toString())) }
                    onProduced()
                    emit(AIStreamChunk.Final(AIResponse(content = textBuilder.toString(), toolCalls = toolCalls, stopReason = finishReason, inputTokens = streamInputTokens, outputTokens = streamOutputTokens)))
                },
                onRetry = { attempt, max -> emit(AIStreamChunk.Retrying(attempt, max)) }
            )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                val enriched = e.enrichWithHttpErrorBody()
                AILogger.logError(logSessionId, "OpenAI", enriched)
                throw enriched
            }
            return@flow
        }

        val request = ChatCompletionRequest(
            model = model,
            messages = openAIMessages,
            tools = toolDefs,
            tool_choice = if (toolDefs != null) "auto" else null,
            stream = true,
            stream_options = StreamOptions(include_usage = true)
        )
        AILogger.logRequest(logSessionId, "OpenAI", model, "POST", url, request)
        // 累积原始 SSE，整轮结束（或失败）后整体落盘，避免高频写盘。
        val rawSse = StringBuilder()

        // 首字节前失败可安全重试；一旦开始吐字（onProduced 已调用）再失败则上抛，避免重复文本。
        try {
            streamWithStaircaseRetry(
                attemptOnce = { onProduced ->
            val textBuilder = StringBuilder()
            // tool_call index -> 累积中的工具调用（保序）。
            val toolAccs = LinkedHashMap<Int, OpenAIToolAcc>()
            var finishReason: String? = null
            var streamInputTokens = 0
            var streamOutputTokens = 0

            val body = api.streamChatCompletion(
                url = url,
                authorization = "Bearer $apiKey",
                request = request
            )

            body.use { rb ->
                // 首字节超时 watchdog：60s 内未收到首个内容块则关闭流，触发可重试的 IOException。
                val firstByteReceived = java.util.concurrent.atomic.AtomicBoolean(false)
                val watchdog = launchFirstByteWatchdog({ rb.close() }) { firstByteReceived.get() }
                val closeHandle = coroutineContext[Job]?.invokeOnCompletion {
                    runCatching { rb.close() }
                }
                try {
                    val reader = rb.charStream().buffered()
                    // 收到服务端 [DONE] 标记即 break 正常结束；readLine() 返回 null 则视为
                    // 流被异常截断（网络中断/TCP 重置/readTimeout），必须抛异常让重试/日志接管——
                    // 否则原本会用截断数据「正常完成」，表现为 AI 突然中断且无任何错误日志。
                    // （收到 [DONE] 即 break，故走到 readLine()==null 时必然未收到过结束标记。）
                    while (true) {
                        coroutineContext.ensureActive()
                        val line = reader.readLine()
                            ?: throw IOException("SSE 流被中断：未收到 [DONE] 结束标记（疑似网络断开）")
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty()) continue
                        rawSse.append(line).append('\n')
                        if (data == "[DONE]") break
                        val obj = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                        // 单行 SSE 解析：不同上游/模型的字段类型偶有出入（如把对象写成数组、把字符串写成对象），
                        // Gson 的 getAsJsonObject/getAsJsonArray 在类型不符时会直接抛 ClassCastException，
                        // asString/asInt 对非原始值会抛 UnsupportedOperationException。
                        // 单行异常不应中断整条流——这里宽松解析，出错仅跳过该行；已累积的文本与后续行不受影响。
                        // 必须放行 CancellationException，否则会吞掉协程取消信号。
                        try {
                            obj.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject?.let { u ->
                                streamInputTokens = u.get("prompt_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: streamInputTokens
                                streamOutputTokens = u.get("completion_tokens")?.takeIf { !it.isJsonNull }?.asInt ?: streamOutputTokens
                            }
                            val choice = obj.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject ?: continue
                            val delta = choice.getAsJsonObject("delta") ?: continue

                            choice.get("finish_reason")?.takeIf { !it.isJsonNull }?.asString?.let {
                                finishReason = it
                            }

                            // 文字增量
                            delta.get("content")?.takeIf { !it.isJsonNull }?.asString?.let { c ->
                                if (c.isNotEmpty()) {
                                    textBuilder.append(c)
                                    if (firstByteReceived.compareAndSet(false, true)) watchdog.cancel()
                                    onProduced()
                                    emit(AIStreamChunk.TextDelta(c))
                                }
                            }
                            // 思考过程增量（reasoning_content）：仅 UI 实时展示，不计入正文、不触发 onProduced
                            // （思考不落库，重试时重新流出即可，无重复文本风险），但收到即说明连接已活，取消首字节超时。
                            delta.get("reasoning_content")?.takeIf { !it.isJsonNull }?.asString?.let { r ->
                                if (r.isNotEmpty()) {
                                    if (firstByteReceived.compareAndSet(false, true)) watchdog.cancel()
                                    emit(AIStreamChunk.ReasoningDelta(r))
                                }
                            }
                            // 工具调用增量：按 index 聚合 id/name/arguments 片段。
                            // 有些模型（如 DeepSeek）在后续增量 chunk 中只传 arguments 片段，
                            // id 和 name 为空字符串 ""，不应覆盖已收到的有效值——否则首次 chunk
                            // 收到的完整 id/name 会被后续空值清空，导致 ToolCall 丢失。
                            delta.getAsJsonArray("tool_calls")?.forEach { el ->
                                val tc = el.asJsonObject
                                val idx = tc.get("index")?.asInt ?: 0
                                val acc = toolAccs.getOrPut(idx) { OpenAIToolAcc() }
                                // 仅在 id/name 非空时更新，避免增量 chunk 的空值覆盖首 chunk 的有效值
                                tc.get("id")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }?.let { acc.id = it }
                                tc.getAsJsonObject("function")?.let { fn ->
                                    fn.get("name")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }?.let { acc.name = it }
                                    fn.get("arguments")?.takeIf { !it.isJsonNull }?.asString?.let { acc.args.append(it) }
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

            val toolCalls = toolAccs.values
                .filter { it.id.isNotEmpty() || it.name.isNotEmpty() }
                .map { acc -> ToolCall(id = acc.id, name = acc.name, arguments = parseArgs(acc.args.toString())) }
            onProduced()
            emit(AIStreamChunk.Final(AIResponse(content = textBuilder.toString(), toolCalls = toolCalls, stopReason = finishReason, inputTokens = streamInputTokens, outputTokens = streamOutputTokens)))
            },
            onRetry = { attempt, max -> emit(AIStreamChunk.Retrying(attempt, max)) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            val enriched = e.enrichWithHttpErrorBody()
            AILogger.logError(logSessionId, "OpenAI", enriched)
            throw enriched
        } finally {
            // 无论成功/失败/取消，把已收到的原始 SSE 落盘（重试时会从上次中断处续写）。
            AILogger.logResponseStream(logSessionId, "OpenAI", rawSse.toString())
        }
    }.flowOn(Dispatchers.IO)

    /** 流式过程中按 index 累积的工具调用状态。 */
    private class OpenAIToolAcc {
        var id = ""
        var name = ""
        val args = StringBuilder()
    }

    /** 把累积的工具入参 JSON 字符串解析为 JsonObject；为空或非法时回退为空对象。 */
    private fun parseArgs(raw: String): JsonObject {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(trimmed).jsonObject }.getOrElse { JsonObject(emptyMap()) }
    }

    private fun convertToOpenAIMessages(
        messages: List<AgentMessage>,
        useResponsesContentParts: Boolean
    ): MutableList<OpenAIChatMessage> {
        val raw = messages.map { message ->
            when (message) {
                is AgentMessage.UserMessage -> OpenAIChatMessage(
                    role = "user",
                    content = message.toOpenAIUserContent(useResponsesContentParts)
                )
                is AgentMessage.AssistantMessage -> {
                    val toolCalls = if (message.toolCalls.isNotEmpty()) {
                        message.toolCalls.map { convertToOpenAIToolCall(it) }
                    } else null
                    OpenAIChatMessage(
                        role = "assistant",
                        content = message.content,
                        tool_calls = toolCalls,
                        reasoning_content = message.reasoning.ifEmpty { null }
                    )
                }
                is AgentMessage.ToolResultMessage -> OpenAIChatMessage(
                    role = "tool",
                    content = message.result,
                    tool_call_id = message.id
                )
            }
        }

        // 防御性清理：确保每个 role: "tool" 消息的前驱 assistant 消息包含对应的 tool_calls。
        // 上下文压缩可能导致 assistant(toolCalls) + tool 的配对断裂，产生孤立 tool 消息，
        // OpenAI API 对此会报 400: "Messages with role 'tool' must be a response to a preceding
        // message with 'tool_calls'"。
        val cleaned = mutableListOf<OpenAIChatMessage>()
        var lastAssistantHadToolCalls = false
        for (msg in raw) {
            if (msg.role == "tool" && !lastAssistantHadToolCalls) {
                // 孤立 tool 消息，跳过
                continue
            }
            if (msg.role == "assistant") {
                lastAssistantHadToolCalls = msg.tool_calls?.isNotEmpty() == true
            } else if (msg.role != "tool") {
                lastAssistantHadToolCalls = false
            }
            cleaned.add(msg)
        }
        return cleaned
    }

    private fun AgentMessage.UserMessage.toOpenAIUserContent(useResponsesContentParts: Boolean): Any {
        if (images.isEmpty()) return content

        val parts = mutableListOf<Map<String, Any>>()
        if (content.isNotBlank()) {
            parts.add(
                if (useResponsesContentParts) {
                    mapOf("type" to "input_text", "text" to content)
                } else {
                    mapOf("type" to "text", "text" to content)
                }
            )
        }
        images.forEach { image ->
            parts.add(image.toOpenAIImagePart(useResponsesContentParts))
        }
        return parts
    }

    private fun AgentImage.toOpenAIImagePart(useResponsesContentParts: Boolean): Map<String, Any> {
        val imageUrl = "data:$mimeType;base64,$base64Data"
        return if (useResponsesContentParts) {
            mapOf(
                "type" to "input_image",
                "image_url" to imageUrl,
                "detail" to "auto"
            )
        } else {
            mapOf(
                "type" to "image_url",
                "image_url" to mapOf(
                    "url" to imageUrl,
                    "detail" to "auto"
                )
            )
        }
    }

    private fun Any?.asTextContent(): String = when (this) {
        null -> ""
        is String -> this
        else -> toString()
    }

    private fun convertToToolCall(openAIToolCall: OpenAIToolCall): ToolCall {
        val argumentsJson = runCatching {
            Json.parseToJsonElement(openAIToolCall.function.arguments).jsonObject
        }.getOrElse { JsonObject(emptyMap()) }
        return ToolCall(
            id = openAIToolCall.id,
            name = openAIToolCall.function.name,
            arguments = argumentsJson
        )
    }

    private fun convertToOpenAIToolCall(toolCall: ToolCall): OpenAIToolCall {
        return OpenAIToolCall(
            id = toolCall.id,
            type = "function",
            function = com.aicode.feature.agent.data.remote.openai.OpenAIFunctionCall(
                name = toolCall.name,
                arguments = JsonObject(toolCall.arguments).toString()
            )
        )
    }
}
