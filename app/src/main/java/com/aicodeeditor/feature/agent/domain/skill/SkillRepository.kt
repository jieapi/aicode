package com.aicodeeditor.feature.agent.domain.skill

import android.content.Context
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.ContainerInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 App 内的「技能(skill)」。
 *
 * 每个 skill = 一个目录 `<aicode>/skills/<name>/`，其中 `SKILL.md` 形如：
 * ```
 * ---
 * name: ui-ux-pro-max
 * description: 一句话说明何时该用这个技能
 * ---
 * 给 AI 的完整指令正文……可引用同目录脚本（如 `python /root/.aicode/skills/<name>/x.py`）。
 * ```
 *
 * 与「工作区」一致放在内部私有目录(filesDir，ext4)：[java.io.File] 与 PRoot 容器挂载都能直接用，
 * 无需运行时存储权限；对外可见性由 DocumentsProvider 在 API 层补回，与物理位置解耦。
 *
 * 物理位置在 [ContainerInstaller.aicodeDir]`/skills`（即 `filesDir/aicode/skills`），独立于 rootfs，
 * 容器升级重装 rootfs 时不受影响。容器内整个 `<aicode>` 绑定为 `/root/.aicode`（读写），
 * 故 skill 在容器内位于 `/root/.aicode/skills/<name>/`，AI 可执行 skill 目录里的脚本。
 */
@Singleton
class SkillRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val containerInstaller: ContainerInstaller
) {
    private companion object {
        const val TAG = "SkillRepository"
        const val SKILL_FILE = "SKILL.md"
        /** frontmatter 注入提示词时，单条 description 的最大字符数，避免异常文件撑爆提示词。 */
        const val MAX_DESC_CHARS = 500
    }

    /**
     * 所有 skill 的父目录：`<aicode>/skills`。首次访问时把旧位置 `filesDir/skills` 一次性迁移过来
     * （历史版本曾直接放在 filesDir 下），迁移后旧目录删除，保证用户既有 skill 不丢。
     */
    val skillsRoot: File by lazy {
        File(containerInstaller.aicodeDir, "skills").also { dest ->
            migrateLegacySkills(dest)
            dest.mkdirs()
        }
    }

    /** 把历史位置 `filesDir/skills` 的内容迁移到新目录（仅当新目录尚不存在时执行一次）。 */
    private fun migrateLegacySkills(dest: File) {
        val legacy = File(context.filesDir, "skills")
        if (dest.exists() || !legacy.isDirectory) return
        runCatching {
            dest.parentFile?.mkdirs()
            if (legacy.renameTo(dest)) {
                FileLogger.i(TAG, "已迁移旧 skill 目录 $legacy -> $dest")
            } else {
                // 跨目录 rename 失败（极少见）时退化为递归复制后删源。
                legacy.copyRecursively(dest, overwrite = false)
                legacy.deleteRecursively()
                FileLogger.i(TAG, "已复制迁移旧 skill 目录 $legacy -> $dest")
            }
        }.onFailure { FileLogger.w(TAG, "迁移旧 skill 目录失败: ${it.message}", it) }
    }

    /** 解析后的单个 skill。[instructions] 为 SKILL.md 去掉 frontmatter 后的正文。 */
    data class Skill(
        val name: String,
        val description: String,
        val dir: File,
        val instructions: String
    )

    /** 扫描磁盘，返回当前所有合法 skill（含 SKILL.md 且能解析出 name 的目录）。 */
    fun listSkills(): List<Skill> {
        val dirs = skillsRoot.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?: return emptyList()
        return dirs.mapNotNull { dir -> parse(dir) }
    }

    /** 读取指定 skill 的完整指令正文；不存在 / 解析失败返回 null。 */
    fun loadInstructions(name: String): String? = listSkills()
        .firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?.instructions

    /** 解析一个 skill 目录；无 SKILL.md 或无 name 时视为非法，返回 null。 */
    private fun parse(dir: File): Skill? {
        val file = File(dir, SKILL_FILE)
        val text = try {
            if (!file.isFile || !file.canRead()) return null
            file.readText()
        } catch (e: Exception) {
            FileLogger.w(TAG, "读取 $SKILL_FILE 失败: ${file.absolutePath}", e)
            return null
        }

        val (front, body) = splitFrontmatter(text)
        // name 优先取 frontmatter，缺省回退到目录名，保证扫描到的目录总能被引用。
        val name = front["name"]?.takeIf { it.isNotBlank() } ?: dir.name
        val description = (front["description"] ?: "").take(MAX_DESC_CHARS)
        return Skill(name = name, description = description, dir = dir, instructions = body.trim())
    }

    /**
     * 切分 YAML frontmatter。仅支持文件最开头由两行 `---` 包裹的块，按 `key: value` 逐行解析
     * （不引第三方 YAML 库，只取顶层标量；无 frontmatter 时整篇都是正文）。
     * @return (frontmatter 键值对, 正文)
     */
    private fun splitFrontmatter(text: String): Pair<Map<String, String>, String> {
        val normalized = text.replace("\r\n", "\n")
        if (!normalized.startsWith("---\n")) return emptyMap<String, String>() to normalized

        val end = normalized.indexOf("\n---", startIndex = 3)
        if (end < 0) return emptyMap<String, String>() to normalized

        val block = normalized.substring(4, end)
        val rest = normalized.substring(end + 4).removePrefix("\n")
        val map = LinkedHashMap<String, String>()
        for (line in block.split("\n")) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim().trim('"', '\'')
            if (key.isNotEmpty()) map[key] = value
        }
        return map to rest
    }
}
