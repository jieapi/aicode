package com.aicodeeditor.feature.agent.domain.mcp

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.ContainerInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

/** 历史位置：API 28 起配置曾存于此 DataStore，现已迁移到 [McpConfigRepository.configFile]。 */
private val Context.mcpDataStore by preferencesDataStore(name = "mcp_prefs")

/**
 * 持久化 MCP 配置。沿用 Claude Desktop 的 `mcpServers` JSON 格式。
 *
 * 存储位置：[ContainerInstaller.aicodeDir]`/mcp.json`，即容器内 `/root/.aicode/mcp.json`——与 skill 同处
 * 统一的 AI 配置目录，AI 和终端都能直接查看/编辑。物理上独立于 rootfs，容器升级重装不丢。
 *
 * 旧版本曾把整段 JSON 存在 `mcp_prefs` DataStore；首次加载时若文件尚不存在，会把 DataStore 里的旧配置
 * 一次性迁移到文件并清空旧键（见 [load]）。
 *
 * 解析容错：单个 server 条目非法只跳过该条并记日志，不影响其它条目；整段 JSON 非法返回空列表。
 * 识别两种形态：含 `url` 的远程 HTTP；含 `command` 的本地 stdio（command/args/env）。
 */
@Singleton
class McpConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val containerInstaller: ContainerInstaller
) {
    private companion object {
        const val TAG = "McpConfigRepository"
        const val CONFIG_FILE = "mcp.json"
        /** 旧 DataStore 里整段 JSON 的键，仅用于一次性迁移。 */
        val LEGACY_KEY = stringPreferencesKey("mcp_servers_json")
        const val DEFAULT_JSON = """{
  "mcpServers": {
  }
}"""
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
        val PRETTY_JSON = Json { prettyPrint = true }
    }

    /** 配置文件：`<aicode>/mcp.json`。 */
    private val configFile: File
        get() = File(containerInstaller.aicodeDir, CONFIG_FILE)

    /** 当前 JSON 文本的内存镜像；null = 尚未从磁盘加载。加载/写入串行化由 [mutex] 保证。 */
    private val rawState = MutableStateFlow<String?>(null)
    private val mutex = Mutex()

    /** 首次访问时从磁盘加载（含 DataStore→文件 迁移）；后续直接复用内存镜像。 */
    private suspend fun ensureLoaded() {
        if (rawState.value != null) return
        mutex.withLock {
            if (rawState.value != null) return
            rawState.value = load()
        }
    }

    /** 读取文件；文件不存在则尝试从旧 DataStore 迁移，否则用默认空配置。 */
    private suspend fun load(): String = withContext(Dispatchers.IO) {
        val file = configFile
        if (file.isFile) {
            return@withContext runCatching { file.readText() }.getOrElse {
                FileLogger.w(TAG, "读取 $CONFIG_FILE 失败，回退默认配置: ${it.message}")
                DEFAULT_JSON
            }
        }
        // 文件缺失：尝试迁移旧 DataStore 配置。
        val legacy = runCatching {
            context.mcpDataStore.data.first()[LEGACY_KEY]
        }.getOrNull()
        val initial = legacy?.takeIf { it.isNotBlank() } ?: DEFAULT_JSON
        writeFile(initial)
        if (legacy != null) {
            FileLogger.i(TAG, "已迁移旧 MCP 配置到 $CONFIG_FILE")
            runCatching { context.mcpDataStore.edit { it.remove(LEGACY_KEY) } }
        }
        initial
    }

    /** 落盘到 [configFile]（确保父目录存在）。 */
    private fun writeFile(json: String) {
        val file = configFile
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    /** 原始 JSON 文本流（供设置页编辑框直接绑定）。首次收集触发加载/迁移。 */
    val rawJsonFlow: Flow<String> = flow {
        ensureLoaded()
        emitAll(rawState.filterNotNull())
    }

    /** 解析后的 server 配置流。 */
    val serversFlow: Flow<List<McpServerConfig>> = rawJsonFlow.map { parse(it) }

    /** 一次性读取当前配置（启动时连接用）。 */
    suspend fun getServers(): List<McpServerConfig> {
        ensureLoaded()
        return parse(rawState.value ?: DEFAULT_JSON)
    }

    /** 写入新的 JSON 文本。调用方负责触发重连。 */
    suspend fun setRawJson(json: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) { writeFile(json) }
            rawState.value = json
        }
    }

    /** 把结构化的 server 列表序列化回 `mcpServers` JSON 并持久化（供可视化编辑器使用）。 */
    suspend fun setServers(servers: List<McpServerConfig>) {
        setRawJson(serialize(servers))
    }

    /** 把 server 列表序列化为 Claude Desktop 格式的 `mcpServers` JSON 文本（与 [parse] 互为逆操作）。 */
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
                }
            }
        }
        val root = buildJsonObject { put("mcpServers", serversObj) }
        return PRETTY_JSON.encodeToString(JsonObject.serializer(), root)
    }

    /** 解析 `mcpServers` 对象为配置列表；任何异常都降级为已解析的部分或空列表。 */
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

            when {
                !command.isNullOrBlank() -> {
                    val args = (obj["args"] as? JsonArray)?.mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull
                    } ?: emptyList()
                    val env = (obj["env"] as? JsonObject)?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                    }?.toMap() ?: emptyMap()
                    McpServerConfig(name = name, command = command, args = args, env = env, enabled = enabled)
                }
                !url.isNullOrBlank() -> {
                    val headers = (obj["headers"] as? JsonObject)?.mapNotNull { (k, v) ->
                        (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
                    }?.toMap() ?: emptyMap()
                    McpServerConfig(name = name, url = url, headers = headers, enabled = enabled)
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
