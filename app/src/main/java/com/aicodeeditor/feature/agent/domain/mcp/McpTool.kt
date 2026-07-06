package com.aicodeeditor.feature.agent.domain.mcp

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ToolCapability
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest

/**
 * 把一个 MCP server 工具适配成应用内的 [AgentTool]，注册进 ToolRegistry 后即可被 Agent 循环复用。
 *
 * 关键点：
 * - [toJsonSchema] 覆写为透传 server 的原始 inputSchema（转成普通 Kotlin 类型供 Gson 序列化），
 *   绕过受限的 ParameterType 枚举——MCP 工具可以有任意复杂的入参 schema。
 * - 对模型暴露的 [name] 做命名空间化（`mcp__server__tool`）并清洗成 function-calling 合法字符，
 *   避免多个 server 工具重名；真正调用时用原始 [remoteName] 走 tools/call。
 */
class McpTool(
    private val client: McpClient,
    private val descriptor: McpToolDescriptor
) : AgentTool() {

    private companion object {
        const val TAG = "McpTool"
        const val NAME_PREFIX = "mcp"
    }

    /** server 上的原始工具名，tools/call 必须用它。 */
    val remoteName: String = descriptor.name

    override val name: String = buildNamespacedName(client.serverName, descriptor.name)

    override val description: String =
        descriptor.description ?: "MCP 工具 ${descriptor.name}（来自 ${client.serverName}）"
    override val capabilities = setOf(ToolCapability.EXTERNAL_TOOL)

    // MCP 工具直接用原始 schema，不走 parameters 这条路；保留空 map 满足基类契约。
    override val parameters: Map<String, ToolParameter> = emptyMap()

    /** 透传 server 的 inputSchema；缺失时回退为空对象 schema。 */
    override fun toJsonSchema(): Map<String, Any> {
        val schema = descriptor.inputSchema
        if (schema == null || schema.isEmpty()) {
            return mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        }
        @Suppress("UNCHECKED_CAST")
        return (jsonElementToAny(schema) as? Map<String, Any>)
            ?: mapOf("type" to "object", "properties" to emptyMap<String, Any>())
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            FileLogger.d(TAG, "调用 MCP 工具 $name (remote=$remoteName) args=${args.keys}")
            val text = client.callTool(remoteName, JsonObject(args))
            if (text.startsWith("Error:")) {
                ToolResult.Error(text.removePrefix("Error:").trim())
            } else {
                ToolResult.Success(JsonPrimitive(text))
            }
        } catch (e: McpException) {
            FileLogger.e(TAG, "MCP 工具调用失败: $name", e)
            ToolResult.Error("MCP 工具执行失败: ${e.message}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "MCP 工具调用异常: $name", e)
            ToolResult.Error("MCP 工具执行异常: ${e.message}")
        }
    }

    /** 拼接命名空间名并清洗成 function-calling 合法字符（^[a-zA-Z0-9_-]{1,64}$）。 */
    private fun buildNamespacedName(server: String, tool: String): String {
        val raw = "${NAME_PREFIX}__${sanitize(server)}__${sanitize(tool)}"
        if (raw.length <= 64) return raw

        val hash = sha1(raw).take(8)
        val suffix = "__$hash"
        return raw.take(64 - suffix.length).trimEnd('_', '-') + suffix
    }

    private fun sanitize(s: String): String =
        s.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }.joinToString("")

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** kotlinx JsonElement → 普通 Kotlin 类型（Map/List/String/Number/Boolean/null），供 Gson 正确序列化。 */
    private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
        is JsonObject -> element.mapValues { (_, v) -> jsonElementToAny(v) }
        is JsonArray -> element.map { jsonElementToAny(it) }
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            element.longOrNull != null -> element.longOrNull
            element.doubleOrNull != null -> element.doubleOrNull
            else -> element.content
        }
    }
}
