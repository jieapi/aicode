package com.aicodeeditor.feature.agent.domain.tool.explorer

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import com.aicodeeditor.feature.workspace.domain.WorkspacePathMapper
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject

/**
 * 列出目录内容/树形结构——纯只读探索工具。
 *
 * 不进容器，通过 [WorkspacePathMapper] 映射容器路径到宿主文件系统直接遍历，
 * 与 `readFile` 一致的路径解析方式。两种模式下均可使用。
 */
class ListFilesTool @Inject constructor(
    private val pathMapper: WorkspacePathMapper
) : AgentTool() {

    private companion object {
        const val TAG = "ListTool"
        const val DEFAULT_MAX_DEPTH = 3
        const val MAX_MAX_DEPTH = 10
        const val MAX_ENTRIES = 500
    }

    override val name = "list"
    override val description = "列出指定目录下的文件和子目录，以缩进树形格式展示。支持 glob 模式过滤、深度限制和排除目录。纯只读工具，不会修改任何文件。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE

    override val parameters: Map<String, ToolParameter> = mapOf(
        "path" to ToolParameter(
            name = "path",
            type = ParameterType.STRING,
            description = "要列出的目录路径。默认为 /workspace（项目根目录）。",
            required = false
        ),
        "pattern" to ToolParameter(
            name = "pattern",
            type = ParameterType.STRING,
            description = "glob 过滤模式，如 '*.kt' 只列出 Kotlin 文件，'*.md' 只列出 Markdown 文件。不填则列出所有条目。",
            required = false
        ),
        "max_depth" to ToolParameter(
            name = "max_depth",
            type = ParameterType.INTEGER,
            description = "递归深度上限，默认 $DEFAULT_MAX_DEPTH，最大 $MAX_MAX_DEPTH。设为 1 则只列顶层。",
            required = false
        ),
        "exclude" to ToolParameter(
            name = "exclude",
            type = ParameterType.STRING,
            description = "要排除的目录名，逗号分隔，如 '.git,node_modules,build'。不填则不排除任何目录。",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { WorkspacePathMapper.CONTAINER_ROOT }
                ?: WorkspacePathMapper.CONTAINER_ROOT
            val pattern = args["pattern"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
            val maxDepth = args["max_depth"]?.jsonPrimitive?.intOrNull?.coerceIn(1, MAX_MAX_DEPTH) ?: DEFAULT_MAX_DEPTH
            val excludeStr = args["exclude"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
            val excludeDirs = excludeStr?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

            val dir = pathMapper.toHostFile(path)
            FileLogger.d(TAG, "list path=$path -> ${dir.absolutePath} (pattern=$pattern, max_depth=$maxDepth)")

            if (!dir.exists()) {
                return ToolResult.Error("目录不存在: $path", "DIR_NOT_FOUND")
            }
            if (!dir.isDirectory) {
                return ToolResult.Error("路径不是目录: $path", "NOT_DIRECTORY")
            }

            val globRegex = pattern?.let { globToRegex(it) }
            var entryCount = 0
            val truncated = StringBuilder()
            val sb = StringBuilder()

            fun walk(file: File, depth: Int, prefix: String) {
                if (entryCount >= MAX_ENTRIES) return
                val children = file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return

                for ((index, child) in children.withIndex()) {
                    if (entryCount >= MAX_ENTRIES) {
                        truncated.append("... (已达 $MAX_ENTRIES 条上限，剩余条目未列出)\n")
                        break
                    }

                    // 跳过用户指定的排除目录
                    if (child.isDirectory && child.name in excludeDirs) continue

                    val isLast = index == children.size - 1
                    val connector = if (isLast) "└── " else "├── "
                    val name = child.name + if (child.isDirectory) "/" else ""

                    // glob 过滤：目录始终递归进入（子文件可能匹配），文件才做过滤
                    if (child.isFile && globRegex != null && !globRegex.matches(child.name)) continue

                    sb.append(prefix).append(connector).append(name).append('\n')
                    entryCount++

                    if (child.isDirectory && depth < maxDepth) {
                        val extension = if (isLast) "    " else "│   "
                        walk(child, depth + 1, prefix + extension)
                    }
                }
            }

            sb.append(path.trimEnd('/')).append("/\n")
            walk(dir, 0, "")

            val result = sb.toString()
            val wasTruncated = entryCount >= MAX_ENTRIES

            FileLogger.v(TAG, "list 完成 path=$path entries=$entryCount truncated=$wasTruncated")

            val resultMap = mutableMapOf<String, JsonElement>(
                "content" to JsonPrimitive(result),
                "path" to JsonPrimitive(pathMapper.toContainerPath(dir.absolutePath)),
                "entries" to JsonPrimitive(entryCount),
                "truncated" to JsonPrimitive(wasTruncated)
            )
            if (wasTruncated) {
                resultMap["note"] = JsonPrimitive("已达 $MAX_ENTRIES 条上限，部分条目未列出。可缩小 pattern 范围或减小 max_depth。")
            }

            ToolResult.Success(JsonObject(resultMap))
        } catch (e: Exception) {
            FileLogger.e(TAG, "list 异常", e)
            ToolResult.Error(e.message ?: "列出目录失败", "LIST_ERROR")
        }
    }

    /** 将简单 glob 模式转为正则：支持 * 和 ? 通配符。 */
    private fun globToRegex(glob: String): Regex {
        val regex = glob.replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex("^$regex$")
    }
}
