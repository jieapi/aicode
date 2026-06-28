package com.aicodeeditor.feature.workspace.domain.model

enum class RemoteProtocol {
    SFTP,
    FTP
}

data class RemoteConnection(
    val id: String,
    val name: String,
    val protocol: RemoteProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String = ""
)

data class RemoteMount(
    val id: String,
    val connectionId: String,
    val remotePath: String,
    val localMountPath: String,
    val isActive: Boolean = false,
    val autoConnect: Boolean = true,
    // Provide a convenient reference to the underlying connection when used in UI
    val connection: RemoteConnection? = null
)
