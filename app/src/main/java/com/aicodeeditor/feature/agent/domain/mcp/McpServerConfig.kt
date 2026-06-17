package com.aicodeeditor.feature.agent.domain.mcp

/**
 * 单个 MCP server 的配置。对应 Claude Desktop `mcpServers` JSON 中的一项，支持两种形态：
 *
 * - **远程 HTTP**：含 [url]，通过 [StreamableHttpTransport] 连接；[headers] 用于静态鉴权
 *   （如 `{"Authorization": "Bearer xxx"}`）。
 * - **本地 stdio**：含 [command]，在 proot 容器内作为长驻子进程启动（如 `npx -y some-server`），
 *   通过 [StdioTransport] 用 stdin/stdout 收发 JSON-RPC；[args] 为命令参数，[env] 为额外环境变量。
 *   需要容器内已具备相应运行时（如 `apk add nodejs npm`）。
 *
 * 形态由字段推断：[isStdio] 为 true（有 command）走 stdio，否则按 HTTP 处理。
 */
data class McpServerConfig(
    val name: String,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
) {
    /** 含 command 即视为本地 stdio 形态；否则按远程 HTTP 处理。 */
    val isStdio: Boolean get() = !command.isNullOrBlank()
}
