package com.aicode.feature.workspace.domain.remote.sftp

import com.aicode.feature.workspace.domain.remote.RemoteAuth
import com.aicode.feature.workspace.domain.remote.RemoteFileInfo
import com.aicode.feature.workspace.domain.remote.RemoteSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File

class SftpSyncClient : RemoteSyncClient {

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    override suspend fun connect(host: String, port: Int, username: String, auth: RemoteAuth) = withContext(Dispatchers.IO) {
        sshClient = SSHClient().apply {
            addHostKeyVerifier(PromiscuousVerifier()) // 简化：暂时信任所有主机密钥
            connect(host, port)
            
            when (auth) {
                is RemoteAuth.Password -> authPassword(username, auth.password)
                is RemoteAuth.PrivateKey -> {
                    val keyProvider = if (auth.passphrase != null) {
                        loadKeys(auth.privateKeyPath, auth.passphrase)
                    } else {
                        loadKeys(auth.privateKeyPath)
                    }
                    authPublickey(username, keyProvider)
                }
            }
        }
        sftpClient = sshClient?.newSFTPClient()
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        sftpClient?.close()
        sshClient?.disconnect()
        sftpClient = null
        sshClient = null
    }

    override suspend fun listFiles(remotePath: String): List<RemoteFileInfo> = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        sftp.ls(remotePath).map {
            RemoteFileInfo(
                name = it.name,
                isDirectory = it.attributes.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY,
                size = it.attributes.size,
                lastModified = it.attributes.mtime * 1000L // mtime is in seconds
            )
        }
    }

    override suspend fun downloadFile(remotePath: String, localPath: String) = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()
        sftp.get(remotePath, localPath)
    }

    override suspend fun uploadFile(localPath: String, remotePath: String) = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        val localFile = File(localPath)
        if (localFile.exists()) {
            sftp.put(localPath, remotePath)
        }
    }

    override suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        sftp.mkdirs(remotePath)
    }

    override suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        val sftp = sftpClient ?: throw IllegalStateException("SFTP Client is not connected")
        val attrs = sftp.statExistence(remotePath)
        if (attrs != null) {
            if (attrs.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
                // 递归删除暂未实现
                sftp.rmdir(remotePath)
            } else {
                sftp.rm(remotePath)
            }
        }
    }

    override suspend fun isConnected(): Boolean = sshClient?.isConnected == true && sshClient?.isAuthenticated == true
}
