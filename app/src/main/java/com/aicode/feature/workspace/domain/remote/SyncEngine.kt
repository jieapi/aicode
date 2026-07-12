package com.aicode.feature.workspace.domain.remote

import android.os.FileObserver
import com.aicode.core.util.FileLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

class SyncEngine(
    private val mount: com.aicode.feature.workspace.domain.model.RemoteMount,
    private val connection: com.aicode.feature.workspace.domain.model.RemoteConnection,
    private val syncClient: RemoteSyncClient,
    private val ignoredPatternsStr: String,
    private val useGitIgnore: Boolean,
    private val maxSyncBatchSize: Int
) {
    companion object {
        private const val TAG = "SyncEngine"
    }

    private val customIgnores = ignoredPatternsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    private var gitIgnorePatterns = emptyList<String>()

    private fun isIgnored(path: String, fileName: String? = null): Boolean {
        val nameToCheck = fileName ?: File(path).name
        val parts = path.split(File.separatorChar, '/')
        
        // 1. 检查自定义忽略规则 (主要针对目录名和文件名)
        if (parts.any { it in customIgnores }) return true
        
        // 2. 检查 .gitignore 规则
        if (useGitIgnore && gitIgnorePatterns.isNotEmpty()) {
            if (parts.any { p -> gitIgnorePatterns.any { g -> p == g || (g.startsWith("*.") && p.endsWith(g.drop(1))) } }) {
                return true
            }
        }
        return false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileObservers = mutableListOf<FileObserver>()
    private val retryCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    
    // 使用 Channel 做缓冲和防抖
    private val syncChannel = Channel<String>(Channel.UNLIMITED)

    init {
        if (useGitIgnore) {
            val gitignore = File(mount.localMountPath, ".gitignore")
            if (gitignore.exists()) {
                gitIgnorePatterns = gitignore.readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { it.trim().removeSuffix("/") }
            }
        }

        scope.launch {
            while (isActive) {
                // 等待队列中的第一个事件
                val firstItem = syncChannel.receive()
                val batch = mutableSetOf(firstItem) // 使用 Set 去重，避免极短时间内同一文件多次触发上传

                // 尝试收集更多就绪的事件，但不超过最大批处理数量
                while (batch.size < maxSyncBatchSize) {
                    val next = syncChannel.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }

                // 按顺序处理批次
                for (localPath in batch) {
                    handleLocalChange(localPath)
                }

                // 如果达到批次上限，说明可能正处于大量修改阶段，短暂延迟让服务器缓冲一下
                if (batch.size >= maxSyncBatchSize) {
                    delay(300)
                }
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
        val localRoot = File(mount.localMountPath)
        if (!localRoot.exists()) {
            localRoot.mkdirs()
        }
        
        // 简单递归下载实现
        suspend fun pull(remoteDir: String, localDir: File) {
            val files = syncClient.listFiles(remoteDir)
            for (file in files) {
                if (isIgnored("$remoteDir/${file.name}", file.name)) continue
                val rPath = "$remoteDir/${file.name}"
                val lFile = File(localDir, file.name)
                if (file.isDirectory) {
                    lFile.mkdirs()
                    pull(rPath, lFile)
                } else {
                    try {
                        syncClient.downloadFile(rPath, lFile.absolutePath)
                        delay(50) // 延时加大到50ms
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "Download Error for $rPath: ${e.message}")
                        forceReconnect()
                    }
                }
            }
        }
        pull(mount.remotePath, localRoot)
    }

    /**
     * 全量推送本地工作区到远程
     */
    suspend fun uploadWorkspace() = withContext(Dispatchers.IO) {
        if (!syncClient.isConnected()) {
            throw IllegalStateException("Client not connected")
        }
        val localRoot = File(mount.localMountPath)
        if (!localRoot.exists()) return@withContext

        suspend fun push(localDir: File, remoteDir: String) {
            val files = localDir.listFiles() ?: return
            for (file in files) {
                if (isIgnored(file.absolutePath, file.name)) continue
                val rPath = "$remoteDir/${file.name}"
                if (file.isDirectory) {
                    try { syncClient.createDirectory(rPath) } catch (e: Exception) {}
                    push(file, rPath)
                } else {
                    try {
                        syncClient.uploadFile(file.absolutePath, rPath)
                        delay(50) // 延时加大到50ms
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "Upload Error for $rPath: ${e.message}")
                        forceReconnect()
                        // 全量同步失败的也放入增量队列兜底重传
                        scope.launch {
                            delay(1000)
                            syncChannel.send(file.absolutePath)
                        }
                    }
                }
            }
        }
        push(localRoot, mount.remotePath)
    }

    /**
     * 开始监听本地镜像目录的改变
     */
    fun startWatching() {
        val mask = FileObserver.CREATE or FileObserver.MODIFY or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM
        
        fun watchDirectory(dir: File) {
            if (!dir.exists() || !dir.isDirectory || isIgnored(dir.absolutePath)) return
            val observer = object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    val fullLocalPath = File(dir, path).absolutePath
                    
                    // 如果新建了子目录，递归增加监听
                    if ((event and FileObserver.CREATE) != 0) {
                        val file = File(fullLocalPath)
                        if (file.isDirectory) {
                            watchDirectory(file)
                        }
                    }
                    
                    scope.launch {
                        syncChannel.send(fullLocalPath)
                    }
                }
            }
            observer.startWatching()
            fileObservers.add(observer)
            
            // 递归监听已有的子目录
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    watchDirectory(child)
                }
            }
        }
        
        watchDirectory(File(mount.localMountPath))
    }

    fun stopWatching() {
        fileObservers.forEach { it.stopWatching() }
        fileObservers.clear()
    }

    private suspend fun handleLocalChange(localPath: String) {
        if (isIgnored(localPath)) return
        val file = File(localPath)
        // 计算对应的远程路径
        val relativePath = localPath.removePrefix(mount.localMountPath).removePrefix("/")
        val remotePath = "${mount.remotePath}/$relativePath"

        try {
            if (file.exists()) {
                if (file.isDirectory) {
                    syncClient.createDirectory(remotePath)
                    FileLogger.i(TAG, "Sync: Created remote directory $remotePath")
                } else {
                    syncClient.uploadFile(localPath, remotePath)
                    FileLogger.i(TAG, "Sync: Uploaded to $remotePath")
                    delay(50) // 延时加大到50ms
                }
            } else {
                syncClient.delete(remotePath)
                FileLogger.i(TAG, "Sync: Deleted $remotePath")
                delay(50)
            }
            retryCounts.remove(localPath) // 成功后清除重试计数
        } catch (e: Exception) {
            FileLogger.e(TAG, "Sync Error for $localPath: ${e.message}", e)
            forceReconnect()
            val count = retryCounts.getOrDefault(localPath, 0)
            if (count < 3) {
                retryCounts[localPath] = count + 1
                FileLogger.i(TAG, "Sync: Retry ${count + 1}/3 for $localPath")
                // 将失败的文件重新放入队列
                scope.launch {
                    delay(1000)
                    syncChannel.send(localPath)
                }
            } else {
                FileLogger.e(TAG, "Sync: Max retries reached for $localPath. Giving up.")
                retryCounts.remove(localPath)
            }
        }
    }

    fun shutdown() {
        stopWatching()
        scope.cancel()
    }

    private suspend fun forceReconnect() {
        try {
            syncClient.disconnect()
        } catch (e: Exception) {}
        try {
            syncClient.connect(
                connection.host,
                connection.port,
                connection.username,
                RemoteAuth.Password(connection.password)
            )
            FileLogger.i(TAG, "Sync: Force reconnected to server successfully.")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Sync: Failed to force reconnect: ${e.message}")
        }
    }
}
