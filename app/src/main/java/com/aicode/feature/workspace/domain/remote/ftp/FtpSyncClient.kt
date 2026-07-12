package com.aicode.feature.workspace.domain.remote.ftp

import com.aicode.feature.workspace.domain.remote.RemoteAuth
import com.aicode.feature.workspace.domain.remote.RemoteFileInfo
import com.aicode.feature.workspace.domain.remote.RemoteSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FtpSyncClient : RemoteSyncClient {

    private val ftpClient = FTPClient()
    private var isConnected = false

    override suspend fun connect(host: String, port: Int, username: String, auth: RemoteAuth) = withContext(Dispatchers.IO) {
        if (auth !is RemoteAuth.Password) {
            throw IllegalArgumentException("FTP only supports Password authentication")
        }

        ftpClient.connect(host, port)
        val reply = ftpClient.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftpClient.disconnect()
            throw IllegalStateException("FTP server refused connection.")
        }

        if (!ftpClient.login(username, auth.password)) {
            ftpClient.disconnect()
            throw IllegalStateException("FTP login failed")
        }

        ftpClient.enterLocalPassiveMode()
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
        isConnected = true
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        if (ftpClient.isConnected) {
            ftpClient.logout()
            ftpClient.disconnect()
        }
        isConnected = false
    }

    override suspend fun listFiles(remotePath: String): List<RemoteFileInfo> = withContext(Dispatchers.IO) {
        val files = ftpClient.listFiles(remotePath) ?: emptyArray()
        files.map {
            RemoteFileInfo(
                name = it.name,
                isDirectory = it.isDirectory,
                size = it.size,
                lastModified = it.timestamp.timeInMillis
            )
        }
    }

    override suspend fun downloadFile(remotePath: String, localPath: String) = withContext(Dispatchers.IO) {
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()
        FileOutputStream(localFile).use { fos ->
            val success = ftpClient.retrieveFile(remotePath, fos)
            if (!success) {
                throw IllegalStateException("Failed to download file from FTP: $remotePath")
            }
        }
    }

    override suspend fun uploadFile(localPath: String, remotePath: String) = withContext(Dispatchers.IO) {
        val localFile = File(localPath)
        if (!localFile.exists()) return@withContext

        // 确保远程目录存在
        val remoteDir = remotePath.substringBeforeLast("/")
        if (remoteDir.isNotEmpty() && remoteDir != remotePath) {
             // 递归创建目录较复杂，暂简化处理
             ftpClient.makeDirectory(remoteDir)
        }

        FileInputStream(localFile).use { fis ->
            val success = ftpClient.storeFile(remotePath, fis)
            if (!success) {
                throw IllegalStateException("Failed to upload file to FTP: $remotePath")
            }
        }
    }

    override suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        ftpClient.makeDirectory(remotePath)
        Unit
    }

    override suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        // 先尝试当做文件删除，如果失败则当做目录删除
        if (!ftpClient.deleteFile(remotePath)) {
            ftpClient.removeDirectory(remotePath)
        }
    }

    override suspend fun isConnected(): Boolean = isConnected && ftpClient.isConnected
}
