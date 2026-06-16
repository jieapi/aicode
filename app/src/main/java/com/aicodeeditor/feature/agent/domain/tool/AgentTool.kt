package com.aicodeeditor.feature.agent.domain.tool

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class ToolResult {
    @Serializable
    data class Success(val data: JsonElement) : ToolResult()

    @Serializable
    data class Error(val message: String, val code: String = "UNKNOWN") : ToolResult()

    @Serializable
    data class Partial(val data: JsonElement, val message: String) : ToolResult()
}

data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    val enum: List<String>? = null
)

enum class ParameterType {
    STRING, INTEGER, BOOLEAN, ARRAY, OBJECT
}

abstract class AgentTool {
    abstract val name: String
    abstract val description: String
    abstract val parameters: Map<String, ToolParameter>

    abstract suspend fun execute(args: Map<String, JsonElement>): ToolResult

    fun toToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = description,
            parameters = parameters.values.map {
                ParameterDefinition(
                    name = it.name,
                    type = it.type.name.lowercase(),
                    description = it.description,
                    required = it.required
                )
            }
        )
    }

    /**
     * 生成符合 JSON Schema 的参数描述，用于真正传给大模型的 function-calling 接口
     * （OpenAI 的 function.parameters / Anthropic 的 input_schema）。
     */
    fun toJsonSchema(): Map<String, Any> {
        val properties = LinkedHashMap<String, Any>()
        val required = mutableListOf<String>()
        parameters.forEach { (key, param) ->
            val prop = LinkedHashMap<String, Any>()
            prop["type"] = param.type.name.lowercase()
            prop["description"] = param.description
            param.enum?.let { prop["enum"] = it }
            properties[key] = prop
            if (param.required) required.add(key)
        }
        return mapOf(
            "type" to "object",
            "properties" to properties,
            "required" to required
        )
    }
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ParameterDefinition>
)

data class ParameterDefinition(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, JsonElement>
)

/**
 * 可流式执行的工具：在最终结果产生之前，逐步 emit 过程输出（如命令的逐行 stdout），
 * 让 UI 能实时显示「执行过程」而非只有最终结果。
 *
 * 实现类应同时实现 [AgentTool.execute] 作为非流式兜底；工作流优先走 [executeStream]。
 */
interface StreamingAgentTool {
    fun executeStream(args: Map<String, JsonElement>): Flow<ToolStreamEvent>
}

/** 流式工具执行过程中产生的事件。 */
sealed class ToolStreamEvent {
    /** 一段新的过程输出（通常是一行）。 */
    data class Progress(val chunk: String) : ToolStreamEvent()
    /** 执行结束，附最终聚合结果（喂回模型用）。 */
    data class Completed(val result: ToolResult) : ToolStreamEvent()
}
