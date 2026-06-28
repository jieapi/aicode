package com.aicodeeditor.feature.agent.domain.prompt

import android.content.Context
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.skill.SkillRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阶段二优化：增量式提示词更新 (SystemContext 化)
 * 拆分为独立的 Source 模块，并且为每个 Source 维护缓存和快照。
 * 对于高频变化的 WorkspaceSource，提供增量 Diff 逻辑，最大化节省冗余 Token，提升大模型 KV Cache 命中率。
 */
@Singleton
class SystemPromptProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val skillRepository: SkillRepository
) {
    // 抽象独立的 Source
    interface PromptSource {
        fun build(ctx: AgentContext): String?
    }

    private inner class StaticRuleSource : PromptSource {
        private val fragments = listOf(
            "prompts/00-identity.md",
            "prompts/10-communication.md",
            "prompts/15-project-rules.md",
            "prompts/20-coding-discipline.md",
            "prompts/30-comments.md",
            "prompts/40-approach.md",
            "prompts/50-safety.md",
            "prompts/60-tools-and-paths.md",
            "prompts/70-skills-and-mcp.md"
        )
        @Volatile private var cached: String? = null

        override fun build(ctx: AgentContext): String {
            return cached ?: fragments.joinToString("\n\n") { path ->
                context.assets.open(path).bufferedReader().use { it.readText() }
                    .replace(LEADING_COMMENT, "")
                    .trim()
            }.also { cached = it }
        }
    }

    private inner class ActiveSkillsSource : PromptSource {
        @Volatile private var cached: String? = null

        override fun build(ctx: AgentContext): String? {
            // 每轮实时扫描磁盘，如有新增立即生效。这里为了避免每次大体积反序列化造成开销，可以简单做内存对比。
            val skills = try { skillRepository.listSkills() } catch (e: Exception) { return null }
            if (skills.isEmpty()) return null
            
            val list = skills.joinToString("\n") { "- ${it.name}: ${it.description.ifBlank { "（无描述）" }}" }
            val newContent = "可用技能 (skills)（格式为 名称: 何时使用；相关时用 load_skill 传入名称取完整正文，详见上文「技能」说明）：\n$list"
            
            if (cached != newContent) {
                cached = newContent
            }
            return cached
        }
    }

    private inner class ProjectRuleSource : PromptSource {
        @Volatile private var cached: String? = null
        private var lastModified: Long = 0
        private var lastProjectRoot: String = ""

        override fun build(ctx: AgentContext): String? {
            if (ctx.projectRoot.isBlank()) return null
            val file = File(ctx.projectRoot, AGENTS_FILE)
            if (!file.isFile || !file.canRead()) return null
            
            val currentMod = file.lastModified()
            // 如果文件未修改且路径一致，直接返回快照基线，避免重复读取与格式化
            if (ctx.projectRoot == lastProjectRoot && currentMod == lastModified && cached != null) {
                return cached
            }
            
            val text = try { file.readText() } catch (e: Exception) { return null }
            if (text.isBlank()) return null
            
            val body = if (text.length > MAX_AGENTS_CHARS) {
                text.take(MAX_AGENTS_CHARS) + "\n…（$AGENTS_FILE 过长，已截断）"
            } else {
                text
            }
            cached = "项目规则 (来自 /workspace/$AGENTS_FILE，务必遵守):\n${body.trim()}"
            lastModified = currentMod
            lastProjectRoot = ctx.projectRoot
            return cached
        }
    }

    private inner class WorkspaceSource : PromptSource {
        override fun build(ctx: AgentContext): String {
            val hasWorkspace = ctx.projectRoot.isNotBlank()
            return """
                当前上下文:
                - 项目根目录: ${if (hasWorkspace) "/workspace" else "（未选择工作区）"}
                - 当前文件: ${ctx.currentFile ?: "无"}
                - 选中的代码: ${ctx.selectedCode ?: "无"}
                - 编程语言: ${ctx.language ?: "未知"}
            """.trimIndent()
        }
    }

    // 会话级别的快照缓存 (Baseline & Snapshot)
    data class SessionSnapshot(
        val lastWorkspaceContext: String = ""
    )
    private val sessionSnapshots = ConcurrentHashMap<String, SessionSnapshot>()

    private val staticRuleSource = StaticRuleSource()
    private val activeSkillsSource = ActiveSkillsSource()
    private val projectRuleSource = ProjectRuleSource()
    private val workspaceSource = WorkspaceSource()

    fun build(agentContext: AgentContext): String {
        // 1. 获取各个 Source 的基线快照。
        val staticContent = staticRuleSource.build(agentContext)
        val skillsContent = activeSkillsSource.build(agentContext)
        val projectRules = projectRuleSource.build(agentContext)
        
        // 2. 增量 Diff 处理 (仅针对高频变化的 Workspace)
        val currentWorkspaceContext = workspaceSource.build(agentContext)
        val sessionId = agentContext.sessionId ?: "default"
        
        val snapshot = sessionSnapshots[sessionId] ?: SessionSnapshot()
        
        val effectiveWorkspaceContent = if (snapshot.lastWorkspaceContext == currentWorkspaceContext) {
            // 没有变化，维持基线不变，仅向大模型注入极简指令，大幅降低重复 Token 处理
            "当前上下文: [未发生变化，请参考之前的会话记忆]"
        } else {
            // 发生变化，输出完整内容并更新本次快照
            sessionSnapshots[sessionId] = snapshot.copy(lastWorkspaceContext = currentWorkspaceContext)
            currentWorkspaceContext
        }

        // 3. 组装最终提示词：把稳定不变的重头基线放最前面（享受 KV Cache），变化部分放末尾
        return buildString {
            append(staticContent)
            
            skillsContent?.let { 
                append("\n\n")
                append(it)
            }
            
            projectRules?.let {
                append("\n\n")
                append(it)
            }
            
            append("\n\n")
            append(effectiveWorkspaceContent)
        }
    }

    private companion object {
        const val TAG = "SystemPromptProvider"
        const val AGENTS_FILE = "AGENTS.md"
        const val MAX_AGENTS_CHARS = 32_000
        val LEADING_COMMENT = Regex("(?s)^\\s*<!--.*?-->\\s*")
    }
}
