package com.aicodeeditor.feature.agent.domain.skill

import android.content.Context
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.ContainerInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDirectorySkillSource @Inject constructor(
    @ApplicationContext private val context: Context,
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
        if (!skillsRoot.exists()) return emptyList()
        val skillFiles = skillsRoot.walkTopDown()
            .maxDepth(4) // 允许一定的嵌套深度（比如 repo/skills/my-skill/SKILL.md）
            .filter { it.isFile && (it.name.equals("SKILL.md", ignoreCase = true) || it.name.equals("CLAUDE.md", ignoreCase = true)) }
            .toList()

        return skillFiles.mapNotNull { file -> file.parentFile }
            .distinct() // 如果同一个目录下同时存在这两种文件，只解析一次
            .mapNotNull { dir -> SkillParser.parse(dir) }
            .sortedBy { it.name.lowercase() }
    }

    override fun loadInstructions(name: String): String? {
        return listSkills()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.instructions
    }
}
