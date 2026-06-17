package com.aicodeeditor.feature.agent.domain.provider

import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicApi
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicMessageRequest
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicMessage
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicContentBlock
import com.aicodeeditor.feature.agent.data.remote.anthropic.AnthropicToolDefinition
import com.aicodeeditor.core.util.AILogger
import com.aicodeeditor.feature.agent.domain.model.AgentMessage
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCall
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
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
    override var model = "claude-3-opus-20240229"
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

        val url = joinUrl(baseUrl, "v1/messages")
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
            AILogger.logError(logSessionId, "Anthropic", e)
            throw e
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

        return AIResponse(content = contentText, toolCalls = toolCalls)
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

        val url = joinUrl(baseUrl, "v1/messages")
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
            streamWithStaircaseRetry { onProduced ->
            val textBuilder = StringBuilder()
            // content block index -> 累积中的 tool_use（仅 tool_use 块建条目，保序）。
            val toolBlocks = LinkedHashMap<Int, ToolBlockAcc>()

            val body = api.streamMessage(url = url, apiKey = apiKey, request = request)

            body.use { rb ->
                val reader = rb.charStream().buffered()
                while (true) {
                    coroutineContext.ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    rawSse.append(line).append('\n')
                    val obj = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                    when (obj.get("type")?.asString) {
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
                                        onProduced()
                                        emit(AIStreamChunk.TextDelta(t))
                                    }
                                }
                                "thinking_delta" -> {
                                    val t = delta.get("thinking")?.asString ?: ""
                                    if (t.isNotEmpty()) emit(AIStreamChunk.ReasoningDelta(t))
                                }
                                "input_json_delta" -> {
                                    val index = obj.get("index")?.asInt
                                    val partial = delta.get("partial_json")?.asString ?: ""
                                    if (index != null) toolBlocks[index]?.args?.append(partial)
                                }
                            }
                        }
                        "message_stop" -> break
                    }
                }
            }

            val toolCalls = toolBlocks.values.map { acc ->
                ToolCall(id = acc.id, name = acc.name, arguments = parseArgs(acc.args.toString()))
            }
            // 读完整轮才视为「已产出」，确保仅返回工具调用（无文字）的轮次失败时也能重试。
            onProduced()
            emit(AIStreamChunk.Final(AIResponse(content = textBuilder.toString(), toolCalls = toolCalls)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AILogger.logError(logSessionId, "Anthropic", e)
            throw e
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

        for (message in messages) {
            when (message) {
                is AgentMessage.UserMessage -> {
                    result.add(AnthropicMessage(role = "user", content = message.content))
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

                    if (contentBlocks.isNotEmpty()) {
                        result.add(AnthropicMessage(role = "assistant", content = contentBlocks))
                    }
                }
                is AgentMessage.ToolResultMessage -> {
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
            else -> element.toString()
        }
    }
}
