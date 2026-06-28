package com.aicodeeditor.feature.agent.domain.tool.skill

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.LinuxContainerEngine
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class ManageSkillTool @Inject constructor(
    private val containerEngine: LinuxContainerEngine
) : AgentTool() {
    private companion object {
        const val TAG = "ManageSkillTool"
    }

    override val name = "manage_skill"
    override val description = "管理 AI 技能库 (Skills)。支持从远程 Git 仓库安装或移除本地技能。"

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "执行的操作类型",
            enum = listOf("install", "remove")
        ),
        "name" to ToolParameter(
            name = "name",
            type = ParameterType.STRING,
            description = "技能名称（作为本地文件夹名称）",
            required = true
        ),
        "url" to ToolParameter(
            name = "url",
            type = ParameterType.STRING,
            description = "Git 仓库地址 (仅 install 必填)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("缺少 action 参数")
        val name = args["name"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("缺少 name 参数")
        
        return try {
            when (action) {
                "install" -> {
                    val url = args["url"]?.jsonPrimitive?.contentOrNull ?: return ToolResult.Error("install 缺少 url 参数")
                    
                    // 使用 containerEngine 执行 git clone，末尾追加一个成功标记
                    val targetDir = "/root/.aicode/skills/$name"
                    val cmd = "rm -rf $targetDir && git clone $url $targetDir && echo 'GIT_CLONE_SUCCESS'"
                    
                    FileLogger.i(TAG, "正在安装技能: $cmd")
                    val output = withContext(Dispatchers.IO) {
                        containerEngine.runCommandSync(cmd, "/", 120_000L) // 2分钟超时
                    }
                    
                    if (output.contains("GIT_CLONE_SUCCESS")) {
                        ToolResult.Success(kotlinx.serialization.json.JsonPrimitive("成功安装技能 $name。\n输出: $output\n技能将在下一次系统提示词刷新时可用（或直接查阅该目录）。"))
                    } else {
                        // 清理可能由于克隆失败留下的残缺目录
                        withContext(Dispatchers.IO) {
                            containerEngine.runCommandSync("rm -rf $targetDir", "/", 30_000L)
                        }
                        ToolResult.Error("安装技能 $name 失败（可能是网络异常、超时或仓库地址无效）。\n输出:\n$output")
                    }
                }
                "remove" -> {
                    val targetDir = "/root/.aicode/skills/$name"
                    val cmd = "rm -rf $targetDir"
                    
                    withContext(Dispatchers.IO) {
                        containerEngine.runCommandSync(cmd, "/", 30_000L)
                    }
                    
                    ToolResult.Success(kotlinx.serialization.json.JsonPrimitive("成功移除技能 $name。"))
                }
                else -> ToolResult.Error("未知的 action: $action")
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "manage_skill 执行失败: ${e.message}", e)
            ToolResult.Error("管理 Skill 失败: ${e.message}")
        }
    }
}
