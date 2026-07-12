package com.aicode.feature.agent.domain.mcp

import kotlinx.serialization.json.JsonObject

/**
 * MCP 传输层抽象：把一条 JSON-RPC 请求送达 server 并取回单条响应。
 *
 * 故意做成可插拔接口：当前只实现远程 [StreamableHttpTransport]；
 * 之后接 stdio（在 proot 里跑 npx server）时再加一个实现，[McpClient] 无需改动。
 */
interface McpTransport {
    /**
     * 发送一条需要应答的请求，阻塞直到拿到配对的 [JsonRpcResponse]。
     * 传输/协议错误以 [McpException] 抛出。
     */
    suspend fun request(method: String, params: JsonObject? = null): JsonRpcResponse

    /** 发送一条不需要应答的通知（fire-and-forget）。 */
    suspend fun notify(method: String, params: JsonObject? = null)

    /** 释放底层资源（连接、会话等）。可重复调用。 */
    fun close()
}
