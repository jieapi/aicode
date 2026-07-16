package com.aicode.feature.backup.data

import android.content.Context
import com.aicode.feature.agent.data.local.dao.AgentMessageDao
import com.aicode.feature.agent.data.local.dao.ChatSessionDao
import com.aicode.feature.agent.data.local.dao.TodoItemDao
import com.aicode.feature.agent.data.local.entity.AgentMessageEntity
import com.aicode.feature.agent.data.local.entity.ChatSessionEntity
import com.aicode.feature.agent.data.local.entity.TodoItemEntity
import com.aicode.feature.agent.domain.mcp.McpConfigRepository
import com.aicode.feature.agent.domain.mcp.McpManager
import com.aicode.feature.agent.domain.permission.PermissionRulesRepository
import com.aicode.feature.backup.domain.AgentMessageDto
import com.aicode.feature.backup.domain.BackupCrypto
import com.aicode.feature.backup.domain.BackupFormat
import com.aicode.feature.backup.domain.BackupManager
import com.aicode.feature.backup.domain.BackupSnapshot
import com.aicode.feature.backup.domain.ChatSessionDto
import com.aicode.feature.backup.domain.GitCredentialDto
import com.aicode.feature.backup.domain.ProviderDto
import com.aicode.feature.backup.domain.RemoteConnectionDto
import com.aicode.feature.backup.domain.RemoteMountDto
import com.aicode.feature.backup.domain.RestoreStats
import com.aicode.feature.backup.domain.TodoItemDto
import com.aicode.feature.credentials.data.local.dao.GitCredentialDao
import com.aicode.feature.credentials.data.local.entity.GitCredentialEntity
import com.aicode.feature.settings.data.local.dao.AIProviderDao
import com.aicode.feature.settings.data.local.entity.AIProviderEntity
import com.aicode.feature.settings.data.repository.KeepaliveSettingsRepository
import com.aicode.feature.settings.data.repository.LogSettingsRepository
import com.aicode.feature.settings.data.repository.SyncSettingsRepository
import com.aicode.feature.settings.data.repository.ThemeSettingsRepository
import com.aicode.feature.settings.data.repository.VisionModelSettingsRepository
import com.aicode.feature.workspace.data.local.dao.RemoteConnectionDao
import com.aicode.feature.workspace.data.local.entity.RemoteConnectionEntity
import com.aicode.feature.workspace.data.local.entity.RemoteMountEntity
import com.aicode.feature.workspace.domain.model.RemoteProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManagerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val aiProviderDao: AIProviderDao,
    private val gitCredentialDao: GitCredentialDao,
    private val remoteConnectionDao: RemoteConnectionDao,
    private val chatSessionDao: ChatSessionDao,
    private val agentMessageDao: AgentMessageDao,
    private val todoItemDao: TodoItemDao,
    private val mcpConfigRepository: McpConfigRepository,
    private val mcpManager: McpManager,
    private val permissionRulesRepository: PermissionRulesRepository,
    private val themeSettingsRepository: ThemeSettingsRepository,
    private val keepaliveSettingsRepository: KeepaliveSettingsRepository,
    private val logSettingsRepository: LogSettingsRepository,
    private val visionModelSettingsRepository: VisionModelSettingsRepository,
    private val syncSettingsRepository: SyncSettingsRepository
) : BackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun currentSchemaVersion(): Int = 21

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    override suspend fun export(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val providers = aiProviderDao.getAllProvidersOnce().map { it.toDto() }
        val credentials = gitCredentialDao.getAllOnce().map { it.toDto() }
        val connections = remoteConnectionDao.getAllConnectionsOnce().map { it.toDto() }
        val mounts = remoteConnectionDao.getAllMountsOnce().map { it.toDto() }
        val sessions = chatSessionDao.getAllOnce().map { it.toDto() }
        val messages = agentMessageDao.getAllOnce().map { it.toDto() }
        val todos = todoItemDao.getAllOnce().map { it.toDto() }
        val mcpServers = mcpConfigRepository.getServers()
        val globalRules = permissionRulesRepository.getGlobalRulesOnce()
        val themeMode = themeSettingsRepository.snapshot()
        val keepalive = keepaliveSettingsRepository.snapshot()
        val logLevel = logSettingsRepository.snapshot()
        val visionProviderId = visionModelSettingsRepository.getVisionProviderId()
        val visionModel = visionModelSettingsRepository.getVisionModel()
        val syncSettings = syncSettingsRepository.snapshot()

        val snapshot = BackupSnapshot(
            schemaVersion = currentSchemaVersion(),
            appVersion = appVersionName(),
            createdAt = System.currentTimeMillis(),
            providers = providers,
            gitCredentials = credentials,
            remoteConnections = connections,
            remoteMounts = mounts,
            chatSessions = sessions,
            agentMessages = messages,
            todoItems = todos,
            mcpServers = mcpServers,
            globalPermissionRules = globalRules,
            themeMode = themeMode,
            keepaliveEnabled = keepalive,
            logLevel = logLevel,
            visionProviderId = visionProviderId,
            visionModel = visionModel,
            syncSettings = syncSettings
        )
        val plain = json.encodeToString(BackupSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
        val salt = BackupCrypto.newSalt()
        val iv = BackupCrypto.newIv()
        val ciphertext = BackupCrypto.encrypt(plain, password, salt, iv)
        BackupFormat.pack(BackupSnapshot.FORMAT_VERSION, salt, iv, ciphertext)
    }

    override suspend fun import(data: ByteArray, password: CharArray): Result<RestoreStats> = withContext(Dispatchers.IO) {
        runCatching {
            val header = BackupFormat.unpack(data)
                ?: error("不是有效的 AiCode 备份文件")
            if (header.formatVersion > BackupSnapshot.FORMAT_VERSION) {
                error("备份格式版本 ${header.formatVersion} 高于本应用支持版本，请升级应用")
            }
            val ciphertext = data.copyOfRange(header.ciphertextOffset, data.size)
            val plain = BackupCrypto.decrypt(ciphertext, password, header.salt, header.iv)
            val snapshot = json.decodeFromString(BackupSnapshot.serializer(), String(plain, Charsets.UTF_8))
            if (snapshot.schemaVersion > currentSchemaVersion()) {
                error("备份的数据库版本 v${snapshot.schemaVersion} 高于本应用 v${currentSchemaVersion()}，请升级应用")
            }
            restore(snapshot)
        }
    }

    private suspend fun restore(snapshot: BackupSnapshot): RestoreStats {
        if (snapshot.providers.isNotEmpty()) {
            aiProviderDao.insertAllProviders(snapshot.providers.map { it.toEntity() })
        }
        if (snapshot.gitCredentials.isNotEmpty()) {
            gitCredentialDao.upsertAll(snapshot.gitCredentials.map { it.toEntity() })
        }
        if (snapshot.remoteConnections.isNotEmpty()) {
            remoteConnectionDao.insertAllConnections(snapshot.remoteConnections.map { it.toEntity() })
        }
        if (snapshot.remoteMounts.isNotEmpty()) {
            remoteConnectionDao.insertAllMounts(snapshot.remoteMounts.map { it.toEntity() })
        }
        if (snapshot.chatSessions.isNotEmpty()) {
            chatSessionDao.upsertAll(snapshot.chatSessions.map { it.toEntity() })
        }
        if (snapshot.agentMessages.isNotEmpty()) {
            agentMessageDao.insertAll(snapshot.agentMessages.map { it.toEntity() })
        }
        if (snapshot.todoItems.isNotEmpty()) {
            todoItemDao.upsertAll(snapshot.todoItems.map { it.toEntity() })
        }
        if (snapshot.mcpServers.isNotEmpty()) {
            mcpConfigRepository.setServers(snapshot.mcpServers)
            mcpManager.reload()
        }
        if (snapshot.globalPermissionRules.isNotEmpty()) {
            permissionRulesRepository.setGlobalRules(snapshot.globalPermissionRules)
        }
        snapshot.themeMode?.let { themeSettingsRepository.restore(it) }
        keepaliveSettingsRepository.restore(snapshot.keepaliveEnabled)
        logSettingsRepository.restore(snapshot.logLevel)
        if (snapshot.visionProviderId.isNotBlank() || snapshot.visionModel.isNotBlank()) {
            visionModelSettingsRepository.setVisionModel(snapshot.visionProviderId, snapshot.visionModel)
        }
        snapshot.syncSettings?.let { syncSettingsRepository.restore(it) }

        return RestoreStats(
            providers = snapshot.providers.size,
            gitCredentials = snapshot.gitCredentials.size,
            remoteConnections = snapshot.remoteConnections.size,
            remoteMounts = snapshot.remoteMounts.size,
            chatSessions = snapshot.chatSessions.size,
            agentMessages = snapshot.agentMessages.size,
            todoItems = snapshot.todoItems.size,
            mcpServers = snapshot.mcpServers.size,
            globalPermissionRules = snapshot.globalPermissionRules.size
        )
    }

    // ── Entity ↔ DTO 转换 ──────────────────────────────────────

    private fun AIProviderEntity.toDto() = ProviderDto(
        id, name, type, apiKey, baseUrl, defaultModel, isActive, models, selectedModel, isEnabled, apiPath, useResponseApi
    )

    private fun ProviderDto.toEntity() = AIProviderEntity(
        id, name, type, apiKey, baseUrl, defaultModel, isActive, models, selectedModel, isEnabled, apiPath, useResponseApi
    )

    private fun GitCredentialEntity.toDto() = GitCredentialDto(id, host, username, token, label, isDefault, createdAt, updatedAt)
    private fun GitCredentialDto.toEntity() = GitCredentialEntity(id, host, username, token, label, isDefault, createdAt, updatedAt)

    private fun RemoteConnectionEntity.toDto() = RemoteConnectionDto(
        id, name, protocol.name, host, port, username, authType, authData, passphrase
    )

    private fun RemoteConnectionDto.toEntity() = RemoteConnectionEntity(
        id, name, RemoteProtocol.valueOf(protocol), host, port, username, authType, authData, passphrase
    )

    private fun RemoteMountEntity.toDto() = RemoteMountDto(id, connectionId, remotePath, localMountPath, isActive, autoConnect)
    private fun RemoteMountDto.toEntity() = RemoteMountEntity(id, connectionId, remotePath, localMountPath, isActive, autoConnect)

    private fun ChatSessionEntity.toDto() = ChatSessionDto(id, title, createdAt, updatedAt, workspacePath, mode, providerId, model)
    private fun ChatSessionDto.toEntity() = ChatSessionEntity(id, title, createdAt, updatedAt, workspacePath, mode, providerId, model)

    private fun AgentMessageEntity.toDto() = AgentMessageDto(
        id, sessionId, role, content, timestamp, toolCallsJson, toolCallId, toolName, toolArgs,
        isError, reasoning, attachmentsJson, isCompacted, isContextSummary, isCompactionMarker
    )

    private fun AgentMessageDto.toEntity() = AgentMessageEntity(
        id, sessionId, role, content, timestamp, toolCallsJson, toolCallId, toolName, toolArgs,
        isError, reasoning, attachmentsJson, isCompacted, isContextSummary, isCompactionMarker
    )

    private fun TodoItemEntity.toDto() = TodoItemDto(id, sessionId, subject, description, status, priority, order, createdAt, updatedAt)
    private fun TodoItemDto.toEntity() = TodoItemEntity(id, sessionId, subject, description, status, priority, order, createdAt, updatedAt)
}
