package com.aicode.feature.agent.domain.mcp

// 远程 HTTP（含 url）或本地 stdio（含 command）两种形态，由 isStdio 推断。
data class McpServerConfig(
    val name: String,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val disabledTools: Set<String> = emptySet(),
    val requireApprovalTools: Set<String> = emptySet()
) {
    val isStdio: Boolean get() = !command.isNullOrBlank()
}
