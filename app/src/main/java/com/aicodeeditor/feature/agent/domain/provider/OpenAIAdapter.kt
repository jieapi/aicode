package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.core.util.AILogger
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIApi
import java.io.IOException
import com.aicodeeditor.feature.agent.data.remote.openai.ChatCompletionRequest
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIChatMessage
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.google.gson.JsonParser
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIToolCall
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIToolDefinition
import com.aicodeeditor.feature.agent.data.remote.openai.OpenAIFunctionDefinition

class OpenAIAdapter @Inject constructor(
    private val api: OpenAIApi
) : AIProvider {

    override var apiKey = ""
    override var baseUrl = "https://api.openai.com/"
    override var model = "gpt-4-turbo"
    override var logSessionId: String? = null

    override suspend fun complete(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): AIResponse {
        val openAIMessages = buildList {
            if (systemPrompt.isNotBlank()) {
                add(OpenAIChatMessage(role = "system", content = systemPrompt))
            }
            addAll(convertToOpenAIMessages(messages))
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

        val url = joinUrl(baseUrl, "v1/chat/completions")
        val request = ChatCompletionRequest(
            model = model,
            messages = openAIMessages,
            tools = toolDefs,
            stream = false
        )
        AILogger.logRequest(logSessionId, "OpenAI", model, "POST", url, request)

        val response = try {
            api.createChatCompletion(url = url, authorization = "Bearer $apiKey", request = request)
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
        val content = message?.content ?: ""
        val toolCalls = message?.tool_calls?.map { convertToToolCall(it) } ?: emptyList()

        return AIResponse(content = content, toolCalls = toolCalls, stopReason = finishReason)
    }

    override fun completeStream(
        systemPrompt: String,
        messages: List<AgentMessage>,
        tools: List<AgentTool>
    ): Flow<AIStreamChunk> = flow {
        val openAIMessages = buildList {
            if (systemPrompt.isNotBlank()) {
                add(OpenAIChatMessage(role = "system", content = systemPrompt))
            }
            addAll(convertToOpenAIMessages(messages))
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

        val url = joinUrl(baseUrl, "v1/chat/completions")
        val request = ChatCompletionRequest(
            model = model,
            messages = openAIMessages,
            tools = toolDefs,
            stream = true
        )
        AILogger.logRequest(logSessionId, "OpenAI", model, "POST", url, request)
        // 累积原始 SSE，整轮结束（或失败）后整体落盘，避免高频写盘。
        val rawSse = StringBuilder()

        // 首字节前失败可安全重试；一旦开始吐字（onProduced 已调用）再失败则上抛，避免重复文本。
        try {
            streamWithStaircaseRetry { onProduced ->
            val textBuilder = StringBuilder()
            // tool_call index -> 累积中的工具调用（保序）。
            val toolAccs = LinkedHashMap<Int, OpenAIToolAcc>()
            var finishReason: String? = null

            val body = api.streamChatCompletion(
                url = url,
                authorization = "Bearer $apiKey",
                request = request
            )

            body.use { rb ->
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
                        val choice = obj.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject ?: continue
                        val delta = choice.getAsJsonObject("delta") ?: continue

                        choice.get("finish_reason")?.takeIf { !it.isJsonNull }?.asString?.let {
                            finishReason = it
                        }

                        // 文字增量
                        delta.get("content")?.takeIf { !it.isJsonNull }?.asString?.let { c ->
                            if (c.isNotEmpty()) {
                                textBuilder.append(c)
                                onProduced()
                                emit(AIStreamChunk.TextDelta(c))
                            }
                        }
                        // 思考过程增量（reasoning_content）：仅 UI 实时展示，不计入正文、不触发 onProduced
                        // （思考不落库，重试时重新流出即可，无重复文本风险）。
                        delta.get("reasoning_content")?.takeIf { !it.isJsonNull }?.asString?.let { r ->
                            if (r.isNotEmpty()) emit(AIStreamChunk.ReasoningDelta(r))
                        }
                        // 工具调用增量：按 index 聚合 id/name/arguments 片段。
                        delta.getAsJsonArray("tool_calls")?.forEach { el ->
                            val tc = el.asJsonObject
                            val idx = tc.get("index")?.asInt ?: 0
                            val acc = toolAccs.getOrPut(idx) { OpenAIToolAcc() }
                            tc.get("id")?.takeIf { !it.isJsonNull }?.asString?.let { acc.id = it }
                            tc.getAsJsonObject("function")?.let { fn ->
                                fn.get("name")?.takeIf { !it.isJsonNull }?.asString?.let { acc.name = it }
                                fn.get("arguments")?.takeIf { !it.isJsonNull }?.asString?.let { acc.args.append(it) }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 该行 SSE 解析失败，跳过；不影响已累积文本与后续行。
                    }
                }
            }

            val toolCalls = toolAccs.values
                .filter { it.id.isNotEmpty() || it.name.isNotEmpty() }
                .map { acc -> ToolCall(id = acc.id, name = acc.name, arguments = parseArgs(acc.args.toString())) }
            // 读完整轮才视为「已产出」，确保仅返回工具调用（无文字）的轮次失败时也能重试。
            onProduced()
            emit(AIStreamChunk.Final(AIResponse(content = textBuilder.toString(), toolCalls = toolCalls, stopReason = finishReason)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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

    private fun convertToOpenAIMessages(messages: List<AgentMessage>): MutableList<OpenAIChatMessage> {
        return messages.map { message ->
            when (message) {
                is AgentMessage.UserMessage -> OpenAIChatMessage(role = "user", content = message.content)
                is AgentMessage.AssistantMessage -> {
                    val toolCalls = if (message.toolCalls.isNotEmpty()) {
                        message.toolCalls.map { convertToOpenAIToolCall(it) }
                    } else null
                    OpenAIChatMessage(role = "assistant", content = message.content, tool_calls = toolCalls)
                }
                is AgentMessage.ToolResultMessage -> OpenAIChatMessage(
                    role = "tool",
                    content = message.result,
                    tool_call_id = message.id
                )
            }
        }.toMutableList()
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
            function = com.aicodeeditor.feature.agent.data.remote.openai.OpenAIFunctionCall(
                name = toolCall.name,
                arguments = JsonObject(toolCall.arguments).toString()
            )
        )
    }
}
