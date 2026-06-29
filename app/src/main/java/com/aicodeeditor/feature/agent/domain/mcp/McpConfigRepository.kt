package com.aicodeeditor.feature.agent.domain.mcp

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.ContainerInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpConfigRepository @Inject constructor(
    private val containerInstaller: ContainerInstaller
) {
    private companion object {
        const val TAG = "McpConfigRepository"
        const val CONFIG_FILE = "mcp.json"
        const val DEFAULT_JSON = """{"mcpServers":{}}"""
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
        val PRETTY_JSON = Json { prettyPrint = true }
    }

    private val configFile: File
        get() = File(containerInstaller.aicodeDir, CONFIG_FILE)

    private val rawState = MutableStateFlow<String?>(null)
    private val mutex = Mutex()

    private suspend fun ensureLoaded() {
        if (rawState.value != null) return
        mutex.withLock {
            if (rawState.value != null) return
            rawState.value = load()
        }
    }

    private suspend fun load(): String = withContext(Dispatchers.IO) {
        val file = configFile
        if (file.isFile) {
            return@withContext runCatching { file.readText() }.getOrElse {
                FileLogger.w(TAG, "读取 $CONFIG_FILE 失败，回退默认配置: ${it.message}")
                DEFAULT_JSON
            }
        }
        writeFile(DEFAULT_JSON)
        DEFAULT_JSON
    }

    private fun writeFile(json: String) {
        val file = configFile
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    val rawJsonFlow: Flow<String> = flow {
        ensureLoaded()
        emitAll(rawState.filterNotNull())
    }

    val serversFlow: Flow<List<McpServerConfig>> = rawJsonFlow.map { parse(it) }

    suspend fun getServers(): List<McpServerConfig> {
        ensureLoaded()
        return parse(rawState.value ?: DEFAULT_JSON)
    }

    suspend fun setRawJson(json: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) { writeFile(json) }
            rawState.value = json
        }
    }

    suspend fun setServers(servers: List<McpServerConfig>) {
        setRawJson(serialize(servers))
    }

    fun serialize(servers: List<McpServerConfig>): String {
        val serversObj = buildJsonObject {
            servers.forEach { server ->
                putJsonObject(server.name) {
                    if (server.isStdio) {
                        put("command", server.command)
                        if (server.args.isNotEmpty()) {
                            putJsonArray("args") { server.args.forEach { add(it) } }
                        }
                        if (server.env.isNotEmpty()) {
                            putJsonObject("env") {
                                server.env.forEach { (key, value) -> put(key, value) }
                            }
                        }
                    } else {
                        put("url", server.url ?: "")
                        if (server.headers.isNotEmpty()) {
                            putJsonObject("headers") {
                                server.headers.forEach { (key, value) -> put(key, value) }
                            }
                        }
                    }
                    put("enabled", server.enabled)
                    if (server.disabledTools.isNotEmpty()) {
                        putJsonArray("disabledTools") { server.disabledTools.forEach { add(it) } }
                    }
                    if (server.requireApprovalTools.isNotEmpty()) {
                        putJsonArray("requireApprovalTools") { server.requireApprovalTools.forEach { add(it) } }
                    }
                }
            }
        }
        val root = buildJsonObject { put("mcpServers", serversObj) }
        return PRETTY_JSON.encodeToString(JsonObject.serializer(), root)
    }

    fun parse(raw: String): List<McpServerConfig> {
        val root = runCatching { JSON.parseToJsonElement(raw).jsonObject }.getOrElse {
            FileLogger.w(TAG, "MCP 配置 JSON 解析失败: ${it.message}")
            return emptyList()
        }
        val servers = (root["mcpServers"] as? JsonObject) ?: return emptyList()

        return servers.mapNotNull { (name, element) ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true

            val command = (obj["command"] as? JsonPrimitive)?.contentOrNull
            val url = (obj["url"] as? JsonPrimitive)?.contentOrNull

            val disabledTools = (obj["disabledTools"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }?.toSet() ?: emptySet()
            val requireApprovalTools = (obj["requireApprovalTools"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }?.toSet() ?: emptySet()

            when {
                !command.isNullOrBlank() -> {
                    val args = (obj["args"] as? JsonArray)?.mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull
                    } ?: emptyList()
                    val env = (obj["env"] as? JsonObject)?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                    }?.toMap() ?: emptyMap()
                    McpServerConfig(
                        name = name,
                        command = command,
                        args = args,
                        env = env,
                        enabled = enabled,
                        disabledTools = disabledTools,
                        requireApprovalTools = requireApprovalTools
                    )
                }
                !url.isNullOrBlank() -> {
                    val headers = (obj["headers"] as? JsonObject)?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                    }?.toMap() ?: emptyMap()
                    McpServerConfig(
                        name = name,
                        url = url,
                        headers = headers,
                        enabled = enabled,
                        disabledTools = disabledTools,
                        requireApprovalTools = requireApprovalTools
                    )
                }
                else -> {
                    // 既无 url 也无 command，无法识别，跳过。
                    FileLogger.i(TAG, "跳过无法识别的 MCP server（缺 url/command）: $name")
                    null
                }
            }
        }
    }
}
