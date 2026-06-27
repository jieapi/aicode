package com.aicodeeditor.feature.agent.domain.skill

import android.content.Context
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.ContainerInstaller
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDirectorySkillSource @Inject constructor(
    private val context: Context,
    private val containerInstaller: ContainerInstaller
) : SkillSource {
    private companion object {
        const val TAG = "LocalDirectorySkillSource"
    }

    val skillsRoot: File by lazy {
        File(containerInstaller.aicodeDir, "skills").also { dest ->
            migrateLegacySkills(dest)
            dest.mkdirs()
        }
    }

    private fun migrateLegacySkills(dest: File) {
        val legacy = File(context.filesDir, "skills")
        if (dest.exists() || !legacy.isDirectory) return
        runCatching {
            dest.parentFile?.mkdirs()
            if (legacy.renameTo(dest)) {
                FileLogger.i(TAG, "已迁移旧 skill 目录 $legacy -> $dest")
            } else {
                legacy.copyRecursively(dest, overwrite = false)
                legacy.deleteRecursively()
                FileLogger.i(TAG, "已复制迁移旧 skill 目录 $legacy -> $dest")
            }
        }.onFailure { FileLogger.w(TAG, "迁移旧 skill 目录失败: ${it.message}", it) }
    }

    override fun listSkills(): List<Skill> {
        val dirs = skillsRoot.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?: return emptyList()
        return dirs.mapNotNull { dir -> SkillParser.parse(dir) }
    }

    override fun loadInstructions(name: String): String? {
        return listSkills()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.instructions
    }
}
