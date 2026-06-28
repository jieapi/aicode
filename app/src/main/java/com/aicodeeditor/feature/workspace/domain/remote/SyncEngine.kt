package com.aicodeeditor.feature.workspace.domain.remote

import android.os.FileObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

class SyncEngine(
    private val remoteServer: com.aicodeeditor.feature.workspace.domain.model.RemoteServer,
    private val syncClient: RemoteSyncClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fileObserver: FileObserver? = null
    
    // 使用 Channel 做缓冲和防抖
    private val syncChannel = Channel<String>(Channel.UNLIMITED)

    init {
        scope.launch {
            syncChannel.receiveAsFlow().collect { localPath ->
                // 简化的防抖处理可以加在这里，比如 delay(100)
                handleLocalChange(localPath)
            }
        }
    }

    /**
     * 全量拉取远程工作区到本地
     */
    suspend fun downloadWorkspace() = withContext(Dispatchers.IO) {
        if (!syncClient.isConnected()) {
            throw IllegalStateException("Client not connected")
        }
        val localRoot = File(remoteServer.localMountPath)
        if (!localRoot.exists()) {
            localRoot.mkdirs()
        }
        
        // 简单递归下载实现
        suspend fun pull(remoteDir: String, localDir: File) {
            val files = syncClient.listFiles(remoteDir)
            for (file in files) {
                val rPath = "$remoteDir/${file.name}"
                val lFile = File(localDir, file.name)
                if (file.isDirectory) {
                    lFile.mkdirs()
                    pull(rPath, lFile)
                } else {
                    syncClient.downloadFile(rPath, lFile.absolutePath)
                }
            }
        }
        pull(remoteServer.remotePath, localRoot)
    }

    /**
     * 开始监听本地镜像目录的改变
     */
    fun startWatching() {
        val mask = FileObserver.CREATE or FileObserver.MODIFY or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM
        
        // 在 Android 中递归监听比较麻烦，为了简化示例，这里演示监听单层目录
        // 实际开发中可以使用第三方库封装的递归 FileObserver，或者给每个子目录都绑定一个
        fileObserver = object : FileObserver(remoteServer.localMountPath, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                // path 是相对路径
                val fullLocalPath = File(remoteServer.localMountPath, path).absolutePath
                scope.launch {
                    syncChannel.send(fullLocalPath)
                }
            }
        }
        fileObserver?.startWatching()
    }

    fun stopWatching() {
        fileObserver?.stopWatching()
    }

    private suspend fun handleLocalChange(localPath: String) {
        val file = File(localPath)
        // 计算对应的远程路径
        val relativePath = localPath.removePrefix(remoteServer.localMountPath).removePrefix("/")
        val remotePath = "${remoteServer.remotePath}/$relativePath"

        try {
            if (file.exists()) {
                if (file.isDirectory) {
                    syncClient.createDirectory(remotePath)
                } else {
                    syncClient.uploadFile(localPath, remotePath)
                }
            } else {
                syncClient.delete(remotePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 失败重试逻辑可在此添加
        }
    }

    fun shutdown() {
        stopWatching()
        scope.cancel()
    }
}
