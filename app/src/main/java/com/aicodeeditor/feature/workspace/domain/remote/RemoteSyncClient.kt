package com.aicodeeditor.feature.workspace.domain.remote

interface RemoteSyncClient {
    suspend fun connect(host: String, port: Int, username: String, auth: RemoteAuth)
    suspend fun disconnect()
    suspend fun listFiles(remotePath: String): List<RemoteFileInfo>
    suspend fun downloadFile(remotePath: String, localPath: String)
    suspend fun uploadFile(localPath: String, remotePath: String)
    suspend fun createDirectory(remotePath: String)
    suspend fun delete(remotePath: String)
    suspend fun isConnected(): Boolean
}

data class RemoteFileInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

sealed class RemoteAuth {
    data class Password(val password: String): RemoteAuth()
    data class PrivateKey(val privateKeyPath: String, val passphrase: String? = null): RemoteAuth()
}
