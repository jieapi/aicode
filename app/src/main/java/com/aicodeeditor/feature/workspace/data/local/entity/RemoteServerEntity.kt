package com.aicodeeditor.feature.workspace.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aicodeeditor.feature.workspace.domain.model.RemoteProtocol
import com.aicodeeditor.feature.workspace.domain.model.RemoteServer

@Entity(tableName = "remote_servers")
data class RemoteServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String, // "SFTP" or "FTP"
    val host: String,
    val port: Int,
    val username: String,
    val authType: String, // "PASSWORD" or "PRIVATE_KEY"
    val authData: String, // password or private key path (For production, this should be encrypted)
    val passphrase: String?, // optional passphrase for private key
    val remotePath: String,
    val localMountPath: String
) {
    fun toDomainModel(): RemoteServer {
        val proto = try { RemoteProtocol.valueOf(protocol) } catch (e: Exception) { RemoteProtocol.SFTP }
        return RemoteServer(
            id = id,
            name = name,
            protocol = proto,
            host = host,
            port = port,
            username = username,
            remotePath = remotePath,
            localMountPath = localMountPath,
            isActive = false // isActive is runtime state
        )
    }
}
