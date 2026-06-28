package com.aicodeeditor.feature.workspace.domain.model

enum class RemoteProtocol {
    SFTP,
    FTP
}

data class RemoteServer(
    val id: String,
    val name: String,
    val protocol: RemoteProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val remotePath: String, // 远程挂载的根路径
    val localMountPath: String, // 本地镜像路径
    val isActive: Boolean = false
)
