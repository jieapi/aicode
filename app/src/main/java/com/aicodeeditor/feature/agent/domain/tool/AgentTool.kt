package com.aicodeeditor.feature.agent.domain.tool

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
