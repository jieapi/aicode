package com.aicodeeditor.feature.agent.domain.prompt

import android.content.Context
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.skill.SkillRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 组装系统提示词：把 assets/prompts/ 下按文件存储的静态片段按序拼起来，再拼上运行时的动态上下文。
 *
 * 静态片段首次加载后缓存在内存，避免每轮对话反复读 asset。要增删/调优提示词，直接改对应 .md 文件，
 * 或调整 [fragments] 的顺序与组成即可。
 */
@Singleton
class SystemPromptProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val skillRepository: SkillRepository
) {
    /** 静态片段，按数组顺序拼装。 */
    private val fragments = listOf(
        "prompts/00-identity.md",
        "prompts/10-communication.md",
        "prompts/15-project-rules.md",
        "prompts/20-coding-discipline.md",
        "prompts/30-comments.md",
        "prompts/40-approach.md",
        "prompts/50-safety.md",
        "prompts/60-tools-and-paths.md",
        "prompts/70-skills-and-mcp.md",
    )

    @Volatile private var cachedBase: String? = null

    /** 完整 system prompt = 静态片段（缓存）+ 动态上下文 + 项目规则文件 AGENTS.md（每轮实时读取）。 */
    fun build(agentContext: AgentContext): String {
        val base = cachedBase ?: loadBase().also { cachedBase = it }
        // 优化 Prompt Caching 命中率：把静态或低频变化的部分放前面，高频变化的放末尾。
        return buildString {
            append(base)
            
            skillsSection()?.let { skills ->
                append("\n\n")
                append(skills)
            }
            
            agentRules(agentContext)?.let { rules ->
                append("\n\n")
                append(rules)
            }
            
            append("\n\n")
            append(contextSection(agentContext))
        }
    }

    private fun loadBase(): String = fragments.joinToString("\n\n") { path ->
        context.assets.open(path).bufferedReader().use { it.readText() }
            // 剥离每个文件开头用于自文档的 <!-- 用途 --> 注释。
            .replace(LEADING_COMMENT, "")
            .trim()
    }

    private fun contextSection(ctx: AgentContext): String {
        val hasWorkspace = ctx.projectRoot.isNotBlank()
        return """
            当前上下文:
            - 项目根目录: ${if (hasWorkspace) "/workspace" else "（未选择工作区）"}
            - 当前文件: ${ctx.currentFile ?: "无"}
            - 选中的代码: ${ctx.selectedCode ?: "无"}
            - 编程语言: ${ctx.language ?: "未知"}
        """.trimIndent()
    }

    /**
     * 列出当前所有可用技能(skill)的 name+description 清单，并提示用 load_skill 取完整指令。
     * 每轮实时扫描磁盘（不缓存），用户增删 skill 即时生效；无 skill 时返回 null（不注入）。
     */
    private fun skillsSection(): String? {
        val skills = try {
            skillRepository.listSkills()
        } catch (e: Exception) {
            FileLogger.w(TAG, "扫描 skills 失败", e)
            return null
        }
        if (skills.isEmpty()) return null

        val list = skills.joinToString("\n") { "- ${it.name}: ${it.description.ifBlank { "（无描述）" }}" }
        return buildString {
            append("可用技能 (skills)（格式为 名称: 何时使用；相关时用 load_skill 传入名称取完整正文，详见上文「技能」说明）：\n")
            append(list)
        }
    }

    /**
     * 读取项目根目录下的 `AGENTS.md`（项目专属规则，类似 CLAUDE.md），作为一段带标注的文本注入提示词末尾。
     * 每轮实时读取，不缓存，以便用户改完即生效。文件不存在 / 空 / 读不动 / 工作区未选时返回 null（不注入）。
     */
    private fun agentRules(ctx: AgentContext): String? {
        if (ctx.projectRoot.isBlank()) return null
        val file = File(ctx.projectRoot, AGENTS_FILE)
        val text = try {
            if (!file.isFile || !file.canRead()) return null
            file.readText()
        } catch (e: Exception) {
            FileLogger.w(TAG, "读取 $AGENTS_FILE 失败: ${file.absolutePath}", e)
            return null
        }
        if (text.isBlank()) return null

        val body = if (text.length > MAX_AGENTS_CHARS) {
            text.take(MAX_AGENTS_CHARS) + "\n…（$AGENTS_FILE 过长，已截断）"
        } else {
            text
        }
        return "项目规则 (来自 /workspace/$AGENTS_FILE，务必遵守):\n${body.trim()}"
    }

    private companion object {
        const val TAG = "SystemPromptProvider"
        const val AGENTS_FILE = "AGENTS.md"
        const val MAX_AGENTS_CHARS = 32_000
        val LEADING_COMMENT = Regex("(?s)^\\s*<!--.*?-->\\s*")
    }
}
