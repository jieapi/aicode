package com.aicodeeditor.feature.agent.domain.tool.skill

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.skill.SkillRepository
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * 让 AI 按需加载一个技能的完整指令正文。
 *
 * 系统提示里只注入了各 skill 的 name+description 清单；AI 判断某个 skill 适用时，调用本工具拿到
 * SKILL.md 正文（可能含「先 apk add python3，再 python /root/.aicode/skills/<name>/x.py」之类的执行步骤），
 * 随后用 read_file/execute_command 等工具按正文行事。skill 目录在容器内为 `/root/.aicode/skills/<name>/`。
 */
class LoadSkillTool @Inject constructor(
    private val skillRepository: SkillRepository
) : AgentTool() {
    private companion object {
        const val TAG = "LoadSkillTool"
    }

    override val name = "load_skill"
    override val description =
        "加载指定技能（Skill）的完整指令内容。当系统提示清单中的技能适用于当前任务时，调用此工具获取其详细操作说明。"

    override val parameters: Map<String, ToolParameter> = mapOf(
        "skill_name" to ToolParameter(
            name = "skill_name",
            type = ParameterType.STRING,
            description = "要加载的技能名称（与系统提示「可用技能」清单中的名称一致）。",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val skillName = args["skill_name"]?.jsonPrimitive?.contentOrNull?.trim()
        if (skillName.isNullOrEmpty()) {
            return ToolResult.Error("缺少必需参数: skill_name", "MISSING_SKILL_NAME")
        }

        val instructions = skillRepository.loadInstructions(skillName)
        if (instructions == null) {
            val available = skillRepository.listSkills().joinToString(", ") { it.name }
            FileLogger.w(TAG, "load_skill 未找到: $skillName，可用: $available")
            return ToolResult.Error(
                "未找到技能「$skillName」。可用技能: ${available.ifEmpty { "（无）" }}",
                "SKILL_NOT_FOUND"
            )
        }

        FileLogger.d(TAG, "load_skill 加载成功: $skillName (${instructions.length} 字符)")
        return ToolResult.Success(JsonPrimitive(instructions))
    }
}
