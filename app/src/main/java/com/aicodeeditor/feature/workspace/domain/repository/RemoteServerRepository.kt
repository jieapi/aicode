package com.aicodeeditor.feature.workspace.domain.repository

import com.aicodeeditor.feature.workspace.data.local.dao.RemoteServerDao
import com.aicodeeditor.feature.workspace.data.local.entity.RemoteServerEntity
import com.aicodeeditor.feature.workspace.domain.model.RemoteServer
import com.aicodeeditor.feature.workspace.domain.model.RemoteProtocol
import com.aicodeeditor.feature.workspace.domain.remote.RemoteAuth
import com.aicodeeditor.feature.workspace.domain.remote.SyncEngine
import com.aicodeeditor.feature.workspace.domain.remote.ftp.FtpSyncClient
import com.aicodeeditor.feature.workspace.domain.remote.sftp.SftpSyncClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class RemoteServerRepository @Inject constructor(
    private val dao: RemoteServerDao
) {
    // 内存中维护运行中的 SyncEngine
    private val activeEngines = ConcurrentHashMap<String, SyncEngine>()

    fun getServers(): Flow<List<RemoteServer>> = dao.getAllServers().map { list ->
        list.map { it.toDomainModel().copy(isActive = activeEngines.containsKey(it.id)) }
    }

    suspend fun getServer(id: String): RemoteServer? = dao.getServerById(id)?.toDomainModel()?.copy(
        isActive = activeEngines.containsKey(id)
    )

    suspend fun addServer(server: RemoteServer, auth: RemoteAuth) {
        val authType = if (auth is RemoteAuth.Password) "PASSWORD" else "PRIVATE_KEY"
        val authData = if (auth is RemoteAuth.Password) auth.password else (auth as RemoteAuth.PrivateKey).privateKeyPath
        val passphrase = if (auth is RemoteAuth.PrivateKey) auth.passphrase else null
        
        val entity = RemoteServerEntity(
            id = server.id,
            name = server.name,
            protocol = server.protocol.name,
            host = server.host,
            port = server.port,
            username = server.username,
            authType = authType,
            authData = authData,
            passphrase = passphrase,
            remotePath = server.remotePath,
            localMountPath = server.localMountPath
        )
        dao.insertServer(entity)
    }

    suspend fun deleteServer(id: String) {
        val entity = dao.getServerById(id)
        if (entity != null) {
            disconnect(id)
            dao.deleteServer(entity)
        }
    }

    suspend fun connect(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getServerById(id) ?: return@withContext Result.failure(Exception("Server not found"))
            val server = entity.toDomainModel()

            val client = when (server.protocol) {
                RemoteProtocol.SFTP -> SftpSyncClient()
                RemoteProtocol.FTP -> FtpSyncClient()
            }

            val auth = if (entity.authType == "PASSWORD") {
                RemoteAuth.Password(entity.authData)
            } else {
                RemoteAuth.PrivateKey(entity.authData, entity.passphrase)
            }

            client.connect(server.host, server.port, server.username, auth)
            
            val engine = SyncEngine(server, client)
            engine.downloadWorkspace() // 全量同步一次
            engine.startWatching()     // 开启增量监听

            activeEngines[id] = engine
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disconnect(id: String) {
        activeEngines[id]?.shutdown()
        activeEngines.remove(id)
    }
}
