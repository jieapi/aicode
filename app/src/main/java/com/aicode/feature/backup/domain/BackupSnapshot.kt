package com.aicode.feature.backup.domain

import com.aicode.feature.agent.domain.mcp.McpServerConfig
import com.aicode.feature.agent.domain.permission.PermissionRule
import com.aicode.feature.settings.data.repository.SyncSettingsSnapshot
import kotlinx.serialization.Serializable

/**
 * 备份快照：一次导出/导入的完整数据集。各 DTO 与 Room Entity 同构但解耦，避免序列化框架绑定到 Room。
 *
 * [schemaVersion] 记录导出时的 AgentDatabase 版本，导入时据此判断兼容性：
 * 备份版本 > 当前 App 版本则拒绝（字段可能缺失）；< 当前则允许（新字段取默认值）。
 */
@Serializable
data class BackupSnapshot(
    val schemaVersion: Int,
    val appVersion: String = "",
    val createdAt: Long,
    val providers: List<ProviderDto> = emptyList(),
    val gitCredentials: List<GitCredentialDto> = emptyList(),
    val remoteConnections: List<RemoteConnectionDto> = emptyList(),
    val remoteMounts: List<RemoteMountDto> = emptyList(),
    val chatSessions: List<ChatSessionDto> = emptyList(),
    val agentMessages: List<AgentMessageDto> = emptyList(),
    val todoItems: List<TodoItemDto> = emptyList(),
    val mcpServers: List<McpServerConfig> = emptyList(),
    val globalPermissionRules: List<PermissionRule> = emptyList(),
    val themeMode: String? = null,
    val keepaliveEnabled: Boolean = false,
    val logLevel: String? = null,
    val visionProviderId: String = "",
    val visionModel: String = "",
    val syncSettings: SyncSettingsSnapshot? = null
)

@Serializable
data class ProviderDto(
    val id: String,
    val name: String,
    val type: String,
    val apiKey: String,
    val baseUrl: String,
    val defaultModel: String,
    val isActive: Boolean,
    val models: String = "",
    val selectedModel: String = "",
    val isEnabled: Boolean = true,
    val useFullUrl: Boolean = false,
    val useResponseApi: Boolean = false
)

@Serializable
data class GitCredentialDto(
    val id: String,
    val host: String,
    val username: String,
    val token: String,
    val label: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class RemoteConnectionDto(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String = "password",
    val authData: String,
    val passphrase: String? = null
)

@Serializable
data class RemoteMountDto(
    val id: String,
    val connectionId: String,
    val remotePath: String,
    val localMountPath: String,
    val isActive: Boolean = false,
    val autoConnect: Boolean = true
)

@Serializable
data class ChatSessionDto(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val workspacePath: String = "",
    val mode: String = "BUILD",
    val providerId: String? = null,
    val model: String? = null
)

@Serializable
data class AgentMessageDto(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val isError: Boolean = false,
    val reasoning: String? = null,
    val attachmentsJson: String? = null,
    val isCompacted: Boolean = false,
    val isContextSummary: Boolean = false,
    val isCompactionMarker: Boolean = false
)

@Serializable
data class TodoItemDto(
    val id: String,
    val sessionId: String,
    val subject: String,
    val description: String = "",
    val status: String = "PENDING",
    val priority: Int = 0,
    val order: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
