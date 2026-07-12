package com.aicode.feature.agent.domain.skill

import com.aicode.feature.agent.domain.container.ContainerInstaller
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDirectorySkillSource @Inject constructor(
    private val containerInstaller: ContainerInstaller
) : SkillSource {

    val skillsRoot: File by lazy {
        File(containerInstaller.aicodeDir, "skills").also { it.mkdirs() }
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
