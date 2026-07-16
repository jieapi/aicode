package com.aicode.feature.agent.domain.memory

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.ContainerInstaller
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalMemorySource @Inject constructor(
    private val containerInstaller: ContainerInstaller
) : MemorySource {

    private val memoryRoot: File by lazy {
        File(containerInstaller.aicodeDir, "memory").also { it.mkdirs() }
    }

    override fun listMemories(): List<Memory> {
        if (!memoryRoot.exists()) return emptyList()
        val files = memoryRoot.listFiles { file -> file.isFile && file.extension == "md" } ?: return emptyList()
        
        return files.mapNotNull { file -> MemoryParser.parse(file, MemoryScope.GLOBAL) }
            .sortedBy { it.name.lowercase() }
    }

    override fun loadContent(name: String): String? {
        return listMemories()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.content
    }

    override fun saveMemory(name: String, description: String, content: String): Boolean {
        return try {
            if (!memoryRoot.exists()) memoryRoot.mkdirs()
            val file = File(memoryRoot, "$name.md")
            file.writeText(MemoryParser.format(name, description, content))
            true
        } catch (e: Exception) {
            FileLogger.e("GlobalMemorySource", "Failed to save memory: $name", e)
            false
        }
    }

    override fun deleteMemory(name: String): Boolean {
        val file = File(memoryRoot, "$name.md")
        return if (file.exists()) file.delete() else false
    }
}
