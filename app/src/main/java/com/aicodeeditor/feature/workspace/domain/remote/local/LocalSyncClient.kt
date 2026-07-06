package com.aicodeeditor.feature.workspace.domain.remote.local

import com.aicodeeditor.feature.workspace.domain.remote.RemoteAuth
import com.aicodeeditor.feature.workspace.domain.remote.RemoteFileInfo
import com.aicodeeditor.feature.workspace.domain.remote.RemoteSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 把远程同步协议适配为本机文件复制。
 *
 * 连接的 host 字段作为镜像根目录；remotePath 仍沿用同步引擎的目标路径语义。
 * 这样现有的“上传全部 / 增量监听上传”可以直接复用为单向本地镜像。
 */
class LocalSyncClient : RemoteSyncClient {
    private var rootDir: File? = null
    private var connected = false

    override suspend fun connect(host: String, port: Int, username: String, auth: RemoteAuth) {
        withContext(Dispatchers.IO) {
            val target = File(host.trim()).absoluteFile
            if (!target.exists() && !target.mkdirs()) {
                throw IOException("无法创建本地目标目录: ${target.absolutePath}")
            }
            if (!target.isDirectory) {
                throw IOException("本地目标不是目录: ${target.absolutePath}")
            }
            verifyWritable(target)
            rootDir = target
            connected = true
        }
    }

    override suspend fun disconnect() {
        connected = false
    }

    override suspend fun listFiles(remotePath: String): List<RemoteFileInfo> = withContext(Dispatchers.IO) {
        val dir = resolve(remotePath)
        dir.listFiles()
            ?.map {
                RemoteFileInfo(
                    name = it.name,
                    isDirectory = it.isDirectory,
                    size = it.length(),
                    lastModified = it.lastModified()
                )
            }
            ?: emptyList()
    }

    override suspend fun downloadFile(remotePath: String, localPath: String) {
        withContext(Dispatchers.IO) {
            val source = resolve(remotePath)
            val target = File(localPath)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }

    override suspend fun uploadFile(localPath: String, remotePath: String) {
        withContext(Dispatchers.IO) {
            val source = File(localPath)
            val target = resolve(remotePath)
            val parent = target.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw IOException("无法创建本地目录: ${parent.absolutePath}")
            }
            runCatching {
                source.copyTo(target, overwrite = true)
            }.getOrElse { e ->
                throw IOException(
                    "写入本地镜像失败: ${target.absolutePath}。请确认已授予存储权限，或改用 App 专属目录 /storage/emulated/0/Android/data/com.aicodeeditor/files/ 下的路径。",
                    e
                )
            }
        }
    }

    override suspend fun createDirectory(remotePath: String) {
        withContext(Dispatchers.IO) {
            val dir = resolve(remotePath)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("无法创建本地目录: ${dir.absolutePath}")
            }
        }
    }

    override suspend fun delete(remotePath: String) {
        withContext(Dispatchers.IO) {
            val file = resolve(remotePath)
            if (file.exists()) file.deleteRecursively()
        }
    }

    override suspend fun isConnected(): Boolean = connected

    private fun resolve(remotePath: String): File {
        val root = rootDir ?: throw IllegalStateException("Local client not connected")
        val normalized = remotePath.trim().replace('\\', '/').trimStart('/')
        val target = if (normalized.isEmpty()) root else File(root, normalized)
        val rootPath = root.canonicalPath
        val targetPath = target.canonicalPath
        if (targetPath != rootPath && !targetPath.startsWith(rootPath + File.separator)) {
            throw IOException("目标路径越界: $remotePath")
        }
        return target
    }

    private fun verifyWritable(dir: File) {
        val probe = File(dir, ".aicode_write_test")
        runCatching {
            probe.writeText("ok")
            probe.delete()
        }.getOrElse { e ->
            throw IOException(
                "本地目标目录不可写: ${dir.absolutePath}。请授予存储权限，或改用 /storage/emulated/0/Android/data/com.aicodeeditor/files/ 下的目录。",
                e
            )
        }
    }
}
