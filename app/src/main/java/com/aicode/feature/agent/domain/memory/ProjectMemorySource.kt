package com.aicode.feature.agent.domain.memory

import com.aicode.core.util.FileLogger
import java.io.File

/**
 * 项目级记忆数据源。由 Repository 动态创建（依赖当前会话的 projectRoot）。
 */
class ProjectMemorySource(private val projectRoot: String) : MemorySource {

    private val memoryRoot: File by lazy {
        File(projectRoot, ".aicode/memory")
    }

    override fun listMemories(): List<Memory> {
        if (projectRoot.isBlank() || !memoryRoot.exists()) return emptyList()
        val files = memoryRoot.listFiles { file -> file.isFile && file.extension == "md" } ?: return emptyList()
        
        return files.mapNotNull { file -> MemoryParser.parse(file, MemoryScope.PROJECT) }
            .sortedBy { it.name.lowercase() }
    }

    override fun loadContent(name: String): String? {
        if (projectRoot.isBlank()) return null
        return listMemories()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.content
    }

    override fun saveMemory(name: String, description: String, content: String): Boolean {
        if (projectRoot.isBlank()) return false
        return try {
            if (!memoryRoot.exists()) memoryRoot.mkdirs()
            val file = MemorySource.resolveMemoryFile(memoryRoot, name)
            file.writeText(MemoryParser.format(MemorySource.sanitizeName(name), description, content))
            true
        } catch (e: Exception) {
            FileLogger.e("ProjectMemorySource", "Failed to save memory: $name", e)
            false
        }
    }

    override fun deleteMemory(name: String): Boolean {
        if (projectRoot.isBlank()) return false
        val file = MemorySource.resolveMemoryFile(memoryRoot, name)
        return if (file.exists()) file.delete() else false
    }
}
