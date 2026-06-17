package com.aicodeeditor.feature.workspace.data.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.aicodeeditor.R
import java.io.File
import java.io.FileNotFoundException
import java.util.LinkedList

/**
 * SAF DocumentsProvider，把 app 私有 `filesDir` 暴露为**单一根**到系统「文件」app 及其它 app 的
 * SAF 选择器，满足「对外可见」。根下只放出两个子目录：
 * - `projects`：工作区（各项目）；
 * - `aicode`：AI 配置目录（skills/ 与 mcp.json，容器内即 `/root/.aicode`）。
 *
 * 其余内部目录（rootfs、容器二进制、数据库、DataStore 等）刻意不在根下列出，避免对外泄露/误删。
 *
 * 工作区物理上在 app 私有 ext4（filesDir），换取 symlink 支持（npm/pnpm/yarn/git 零配置可用）；
 * emulated 存储虽天然可见但内核拒绝 symlink。可见性这里用官方 SAF API 在 API 层补回，与物理位置解耦。
 *
 * docId 直接用文件绝对路径（与 Termux 实现一致），简单且跨进程稳定。
 */
class WorkspaceDocumentsProvider : DocumentsProvider() {

    private companion object {
        const val ALL_MIME_TYPES = "*/*"
        const val MAX_SEARCH_RESULTS = 50

        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES,
        )

        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
        )
    }

    /** 暴露的单一根：app 私有 filesDir。 */
    private fun baseDir(): File =
        ctx().filesDir

    /**
     * 根下唯一对外可见的子目录名白名单：工作区 `projects` 与 AI 配置 `aicode`（容器内 /root/.aicode，
     * 含 skills/ 与 mcp.json）。两者首次访问即创建，其余 filesDir 内部目录不列出。
     */
    private fun exposedChildren(): List<File> = listOf(
        File(baseDir(), "projects").apply { mkdirs() },
        File(baseDir(), "aicode").apply { mkdirs() },
    )

    /** minSdk 26 上基类无 requireContext()，这里自取非空 context。 */
    private fun ctx() =
        context ?: throw IllegalStateException("Provider context unavailable")

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        addRoot(result, baseDir(), ctx().getString(R.string.app_name))
        return result
    }

    /** 往根游标追加一个 root：docId 用目录绝对路径，与 [docIdForFile] 一致。 */
    private fun addRoot(result: MatrixCursor, dir: File, title: String) {
        result.newRow().apply {
            add(Root.COLUMN_ROOT_ID, docIdForFile(dir))
            add(Root.COLUMN_DOCUMENT_ID, docIdForFile(dir))
            add(Root.COLUMN_SUMMARY, null)
            add(
                Root.COLUMN_FLAGS,
                Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_IS_CHILD,
            )
            add(Root.COLUMN_TITLE, title)
            add(Root.COLUMN_MIME_TYPES, ALL_MIME_TYPES)
            add(Root.COLUMN_AVAILABLE_BYTES, dir.freeSpace)
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, documentId, null)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = fileForDocId(parentDocumentId)
        // 根目录（filesDir 本身）只放出白名单子目录，隐藏 rootfs/数据库等内部目录；
        // 其余层级照常列出全部内容。
        val children = if (parent.absolutePath == baseDir().absolutePath) {
            exposedChildren()
        } else {
            parent.listFiles()?.toList() ?: emptyList()
        }
        children.forEach { child -> includeFile(result, null, child) }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = fileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent = fileForDocId(parentDocumentId)
        var newFile = File(parent, displayName)
        var conflictId = 2
        while (newFile.exists()) {
            newFile = File(parent, "$displayName ($conflictId)")
            conflictId++
        }
        val ok = try {
            if (Document.MIME_TYPE_DIR == mimeType) newFile.mkdir() else newFile.createNewFile()
        } catch (e: Exception) {
            throw FileNotFoundException("Failed to create document: ${newFile.path}")
        }
        if (!ok) throw FileNotFoundException("Failed to create document: ${newFile.path}")
        return docIdForFile(newFile)
    }

    override fun deleteDocument(documentId: String) {
        val file = fileForDocId(documentId)
        if (!file.deleteRecursively()) {
            throw FileNotFoundException("Failed to delete document: $documentId")
        }
    }

    override fun getDocumentType(documentId: String): String =
        mimeType(fileForDocId(documentId))

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?,
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val root = fileForDocId(rootId)
        val rootCanonical = root.canonicalPath
        // 从根（filesDir）搜索时只下钻白名单子目录，避免扫到 rootfs 等内部目录；其余层级照常。
        val seeds = if (root.absolutePath == baseDir().absolutePath) exposedChildren() else listOf(root)
        val pending = LinkedList<File>().apply { addAll(seeds) }
        val needle = query.lowercase()

        while (pending.isNotEmpty() && result.count < MAX_SEARCH_RESULTS) {
            val file = pending.removeFirst()
            // 仅在根目录内搜索，避免 symlink 指向外部导致扫到整个磁盘
            val insideRoot = try {
                file.canonicalPath.startsWith(rootCanonical)
            } catch (e: Exception) {
                false
            }
            if (!insideRoot) continue
            if (file.isDirectory) {
                file.listFiles()?.let { pending.addAll(it) }
            } else if (file.name.lowercase().contains(needle)) {
                includeFile(result, null, file)
            }
        }
        return result
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith(parentDocumentId)

    private fun docIdForFile(file: File): String = file.absolutePath

    private fun fileForDocId(docId: String): File {
        val f = File(docId)
        if (!f.exists()) throw FileNotFoundException("${f.absolutePath} not found")
        return f
    }

    private fun mimeType(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return "application/octet-stream"
    }

    private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
        val resolvedFile = file ?: docId?.let { fileForDocId(it) }
        ?: throw FileNotFoundException("docId and file both null")
        val resolvedDocId = docId ?: docIdForFile(resolvedFile)

        var flags = 0
        if (resolvedFile.isDirectory) {
            if (resolvedFile.canWrite()) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (resolvedFile.canWrite()) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
        }
        if (resolvedFile.parentFile?.canWrite() == true) flags = flags or Document.FLAG_SUPPORTS_DELETE

        val mime = mimeType(resolvedFile)
        if (mime.startsWith("image/")) flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL

        result.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, resolvedDocId)
            add(Document.COLUMN_DISPLAY_NAME, resolvedFile.name)
            add(Document.COLUMN_SIZE, resolvedFile.length())
            add(Document.COLUMN_MIME_TYPE, mime)
            add(Document.COLUMN_LAST_MODIFIED, resolvedFile.lastModified())
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_ICON, R.mipmap.ic_launcher)
        }
    }
}
