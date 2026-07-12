package com.aicode.feature.agent.domain.skill

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skill 仓库，现已升级为聚合层（Composite）。
 * 它负责聚合各个 SkillSource（如 LocalDirectorySkillSource, UrlSkillSource 等）提供的技能。
 */
@Singleton
class SkillRepository @Inject constructor(
    private val localDirectorySkillSource: LocalDirectorySkillSource
    // 未来可在这里注入更多的 SkillSource，例如：
    // private val urlSkillSource: UrlSkillSource
) {
    /** 扫描并聚合当前所有合法的 skill。 */
    fun listSkills(): List<Skill> {
        val allSkills = mutableListOf<Skill>()
        
        // 依次从各个数据源获取技能
        allSkills.addAll(localDirectorySkillSource.listSkills())
        
        // 如果有同名技能，可以做去重或优先级覆盖
        return allSkills.distinctBy { it.name.lowercase() }
    }

    /** 读取指定 skill 的完整指令正文；不存在 / 解析失败返回 null。 */
    fun loadInstructions(name: String): String? {
        // 按优先级尝试从各个数据源加载
        return localDirectorySkillSource.loadInstructions(name)
    }
}
