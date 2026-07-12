package com.aicode.feature.workspace.domain.repository

import com.aicode.feature.workspace.data.local.dao.RemoteConnectionDao
import com.aicode.feature.workspace.data.local.entity.RemoteConnectionEntity
import com.aicode.feature.workspace.data.local.entity.RemoteMountEntity
import com.aicode.feature.workspace.domain.model.RemoteConnection
import com.aicode.feature.workspace.domain.model.RemoteMount
import com.aicode.feature.workspace.domain.model.RemoteProtocol
import com.aicode.feature.workspace.domain.remote.RemoteAuth
import com.aicode.feature.workspace.domain.remote.SyncEngine
import com.aicode.feature.workspace.domain.remote.ftp.FtpSyncClient
import com.aicode.feature.workspace.domain.remote.local.LocalSyncClient
import com.aicode.feature.workspace.domain.remote.sftp.SftpSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteRepository @Inject constructor(
    private val dao: RemoteConnectionDao,
    private val syncSettings: com.aicode.feature.settings.data.repository.SyncSettingsRepository
) {
    private val activeEngines = ConcurrentHashMap<String, SyncEngine>()
    private val activeEngineIds = MutableStateFlow<Set<String>>(emptySet())

    fun getConnections(): Flow<List<RemoteConnection>> = dao.getAllConnections().map { list ->
        list.map { it.toDomainModel() }
    }
    
    fun getMounts(): Flow<List<RemoteMount>> = combine(
        dao.getAllMounts(),
        activeEngineIds
    ) { list, activeIds ->
        list.map { mountEntity ->
            val connEntity = dao.getConnectionById(mountEntity.connectionId)
            mountEntity.toDomainModel(connEntity?.toDomainModel()).copy(
                isActive = activeIds.contains(mountEntity.id)
            )
        }
    }

    suspend fun addConnection(conn: RemoteConnection, auth: RemoteAuth) {
        val authType = if (auth is RemoteAuth.Password) "PASSWORD" else "PRIVATE_KEY"
        val authData = if (auth is RemoteAuth.Password) auth.password else (auth as RemoteAuth.PrivateKey).privateKeyPath
        val passphrase = if (auth is RemoteAuth.PrivateKey) auth.passphrase else null
        
        val entity = RemoteConnectionEntity(
            id = conn.id,
            name = conn.name,
            protocol = conn.protocol,
            host = conn.host,
            port = conn.port,
            username = conn.username,
            authType = authType,
            authData = authData,
            passphrase = passphrase
        )
        dao.insertConnection(entity)
    }

    suspend fun updateConnection(conn: RemoteConnection, auth: RemoteAuth) {
        // Will overwrite existing connection ID
        addConnection(conn, auth)
    }

    suspend fun deleteConnection(id: String) {
        val entity = dao.getConnectionById(id)
        if (entity != null) {
            // Associated mounts will cascade delete in DB, but we should disconnect them
            val mounts = dao.getMountsByConnectionId(id)
            // Just disconnect everything from memory to be safe, cascading handles DB
            activeEngines.keys.forEach { mountId -> disconnectMount(mountId) }
            dao.deleteConnection(entity)
        }
    }

    suspend fun addMount(mount: RemoteMount) {
        dao.insertMount(RemoteMountEntity(
            id = mount.id,
            connectionId = mount.connectionId,
            remotePath = mount.remotePath,
            localMountPath = mount.localMountPath,
            autoConnect = mount.autoConnect
        ))
    }
    
    suspend fun updateMount(mount: RemoteMount) {
        dao.updateMount(RemoteMountEntity(
            id = mount.id,
            connectionId = mount.connectionId,
            remotePath = mount.remotePath,
            localMountPath = mount.localMountPath,
            autoConnect = mount.autoConnect
        ))
    }
    
    suspend fun deleteMount(mountId: String) {
        disconnectMount(mountId)
        val entity = dao.getMountById(mountId)
        if (entity != null) {
            dao.deleteMount(entity)
        }
    }

    suspend fun connectMount(mountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mountEntity = dao.getMountById(mountId) ?: return@withContext Result.failure(Exception("Mount not found"))
            val connEntity = dao.getConnectionById(mountEntity.connectionId) ?: return@withContext Result.failure(Exception("Connection not found"))
            
            val conn = connEntity.toDomainModel()
            val mount = mountEntity.toDomainModel(conn)

            val client = when (conn.protocol) {
                RemoteProtocol.SFTP -> SftpSyncClient()
                RemoteProtocol.FTP -> FtpSyncClient()
                RemoteProtocol.LOCAL -> LocalSyncClient()
            }

            val auth = if (connEntity.authType == "PASSWORD") {
                RemoteAuth.Password(connEntity.authData)
            } else {
                RemoteAuth.PrivateKey(connEntity.authData, connEntity.passphrase)
            }

            client.connect(conn.host, conn.port, conn.username, auth)
            
            val engine = SyncEngine(
                mount = mount, 
                connection = conn, 
                syncClient = client, 
                ignoredPatternsStr = syncSettings.ignoredPatterns.value,
                useGitIgnore = syncSettings.useGitIgnore.value,
                maxSyncBatchSize = syncSettings.maxSyncBatchSize.value
            )
            // 移除默认的全量下载以免覆盖本地修改，交由用户手动点击同步
            engine.startWatching()     // 增量监听
            if (conn.protocol == RemoteProtocol.LOCAL) {
                engine.uploadWorkspace()
            }

            activeEngines[mountId] = engine
            activeEngineIds.update { it + mountId }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disconnectMount(mountId: String) {
        activeEngines[mountId]?.shutdown()
        activeEngines.remove(mountId)
        activeEngineIds.update { it - mountId }
    }

    suspend fun forceUploadMount(mountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val engine = activeEngines[mountId] ?: return@withContext Result.failure(Exception("请先连接该挂载点"))
            engine.uploadWorkspace()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun forceDownloadMount(mountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val engine = activeEngines[mountId] ?: return@withContext Result.failure(Exception("请先连接该挂载点"))
            engine.downloadWorkspace()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        auth: RemoteAuth,
        protocol: RemoteProtocol
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = when (protocol) {
                RemoteProtocol.SFTP -> SftpSyncClient()
                RemoteProtocol.FTP -> FtpSyncClient()
                RemoteProtocol.LOCAL -> LocalSyncClient()
            }
            client.connect(host, port, username, auth)
            client.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listRemoteDirectories(connectionId: String, path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val connEntity = dao.getConnectionById(connectionId) ?: return@withContext Result.failure(Exception("Connection not found"))
            val conn = connEntity.toDomainModel()
            
            val client = when (conn.protocol) {
                RemoteProtocol.SFTP -> SftpSyncClient()
                RemoteProtocol.FTP -> FtpSyncClient()
                RemoteProtocol.LOCAL -> LocalSyncClient()
            }
            val auth = if (connEntity.authType == "PASSWORD") {
                RemoteAuth.Password(connEntity.authData)
            } else {
                RemoteAuth.PrivateKey(connEntity.authData, connEntity.passphrase)
            }
            
            client.connect(conn.host, conn.port, conn.username, auth)
            val files = client.listFiles(path).filter { it.isDirectory }.map { it.name }
            client.disconnect()
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun RemoteConnectionEntity.toDomainModel() = RemoteConnection(
        id = id,
        name = name,
        protocol = protocol,
        host = host,
        port = port,
        username = username,
        password = if (authType == "PASSWORD") authData else ""
    )

    private fun RemoteMountEntity.toDomainModel(conn: RemoteConnection?) = RemoteMount(
        id = id,
        connectionId = connectionId,
        remotePath = remotePath,
        localMountPath = localMountPath,
        isActive = isActive,
        autoConnect = autoConnect,
        connection = conn
    )
}
