package com.aicodeeditor.feature.agent.domain.mcp

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.LinuxContainerEngine
import com.aicodeeditor.feature.agent.domain.tool.ToolRegistry
import com.aicodeeditor.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

data class McpServerStatus(
    val name: String,
    val state: State,
    val toolCount: Int = 0,
    val error: String? = null
) {
    enum class State { CONNECTING, CONNECTED, FAILED, DISABLED }
}

// reloadMutex 串行化重连，避免设置页连点导致并发注册/反注册竞态。
@Singleton
class McpManager @Inject constructor(
    private val configRepository: McpConfigRepository,
    private val toolRegistry: ToolRegistry,
    private val okHttpClient: OkHttpClient,
    private val containerEngine: LinuxContainerEngine,
    private val workspaceRepository: WorkspaceRepository
) {
    private companion object {
        const val TAG = "McpManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reloadMutex = Mutex()

    private val activeClients = mutableMapOf<String, McpClient>()
    private val registeredToolNames = mutableSetOf<String>()

    private val _statuses = MutableStateFlow<List<McpServerStatus>>(emptyList())
    val statuses: StateFlow<List<McpServerStatus>> = _statuses.asStateFlow()

    fun start() {
        scope.launch { reload() }
    }

    suspend fun reload() = reloadMutex.withLock {
        val servers = configRepository.getServers()
        FileLogger.i(TAG, "重新加载 MCP 配置，共 ${servers.size} 个 server")

        teardown()

        if (servers.isEmpty()) {
            _statuses.value = emptyList()
            return@withLock
        }

        // 先把所有 server 置为「连接中/禁用」，UI 立即有反馈。
        _statuses.value = servers.map { cfg ->
            McpServerStatus(
                name = cfg.name,
                state = if (cfg.enabled) McpServerStatus.State.CONNECTING else McpServerStatus.State.DISABLED
            )
        }

        // 并行连接所有启用的 server；各自独立失败。
        val results = withContext(Dispatchers.IO) {
            servers.filter { it.enabled }.map { cfg ->
                async { connectOne(cfg) }
            }.awaitAll()
        }

        // 合并禁用项与连接结果，保持原始顺序。
        val byName = results.associateBy { it.name }
        _statuses.value = servers.map { cfg ->
            byName[cfg.name] ?: McpServerStatus(cfg.name, McpServerStatus.State.DISABLED)
        }
    }

    private suspend fun connectOne(cfg: McpServerConfig): McpServerStatus {
        return try {
            val transport = if (cfg.isStdio) {
                // 本地 stdio server 需要容器就绪（首次会解压 rootfs）。
                containerEngine.ensureInstalled()
                StdioTransport(
                    serverName = cfg.name,
                    engine = containerEngine,
                    program = cfg.command!!,
                    programArgs = cfg.args,
                    projectPath = workspaceRepository.currentPath(),
                    extraEnv = cfg.env
                )
            } else {
                StreamableHttpTransport(
                    endpoint = cfg.url.orEmpty(),
                    client = okHttpClient,
                    extraHeaders = cfg.headers
                )
            }
            val client = McpClient(serverName = cfg.name, transport = transport)
            client.connect()

            val tools = client.tools.map { McpTool(client, it) }
            val enabledTools = tools.filter { it.remoteName !in cfg.disabledTools }
            synchronized(activeClients) {
                activeClients[cfg.name] = client
                enabledTools.forEach { tool ->
                    toolRegistry.register(tool.name, tool)
                    registeredToolNames.add(tool.name)
                }
            }
            FileLogger.i(TAG, "[${cfg.name}] 连接成功，注册 ${enabledTools.size}/${tools.size} 个工具")
            McpServerStatus(cfg.name, McpServerStatus.State.CONNECTED, toolCount = enabledTools.size)
        } catch (e: Exception) {
            FileLogger.e(TAG, "[${cfg.name}] 连接失败", e)
            McpServerStatus(cfg.name, McpServerStatus.State.FAILED, error = e.message)
        }
    }

    fun getServerTools(serverName: String): List<McpToolDescriptor> {
        return synchronized(activeClients) {
            activeClients[serverName]?.tools ?: emptyList()
        }
    }

    private fun teardown() {
        synchronized(activeClients) {
            registeredToolNames.forEach { toolRegistry.unregister(it) }
            registeredToolNames.clear()
            activeClients.values.forEach { runCatching { it.close() } }
            activeClients.clear()
        }
    }
}
