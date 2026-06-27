package com.aicodeeditor.feature.agent.domain.skill

import java.io.File

/**
 * 解析后的单个 Skill 模型。
 *
 * @param name 技能名称（供大模型调用的唯一标识）
 * @param description 技能描述（何时使用该技能）
 * @param requiredTools 该技能所需的专属工具列表（可选）
 * @param dir 技能所在的本地目录（如果是远程技能，可能为空或为缓存目录）
 * @param instructions 技能指令正文（剥离 Frontmatter 后的内容）
 */
data class Skill(
    val name: String,
    val description: String,
    val requiredTools: List<String> = emptyList(),
    val dir: File? = null,
    val instructions: String
)
