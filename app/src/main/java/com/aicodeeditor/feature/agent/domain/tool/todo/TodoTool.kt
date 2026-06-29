package com.aicodeeditor.feature.agent.domain.tool.todo

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.data.local.dao.TodoItemDao
import com.aicodeeditor.feature.agent.data.local.entity.TodoItemEntity
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.TodoStatus
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

/**
 * 管理 AI Agent 当前会话的任务清单（待办列表）。
 *
 * Action 分发：
 * - create: 批量创建待办项，传入 todos 数组
 * - update: 更新单个待办项（status/subject/description），必填 todo_id
 * - delete: 删除指定待办项，必填 todo_id
 * - list: 列出当前会话所有待办项
 *
 * 典型用法：接到复杂任务时先 create 拆分为步骤；开始处理某项时 update 其 status 为
 * in_progress；完成时标记 completed。帮助你和用户同步追踪进度。
 */
class TodoTool @Inject constructor(
    private val todoItemDao: TodoItemDao
) : AgentTool() {

    private companion object {
        const val TAG = "TodoTool"
    }

    override val name = "todo"
    override val description = "管理当前会话的任务清单。用 action 参数选操作：" +
        "create（批量创建）、update（更新状态/标题）、delete（删除）、list（列出全部）。" +
        "接到复杂任务时，先 create 拆分为步骤；开始处理某项时 update 其 status 为 in_progress；" +
        "完成时标记 completed。帮助你和用户同步追踪进度。"

    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE

    /** todos 数组中单个待办项的 JSON Schema */
    private val todoItemSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "subject" to mapOf(
                "type" to "string",
                "description" to "简短的待办标题（祈使句，如「分析项目结构」）"
            ),
            "description" to mapOf(
                "type" to "string",
                "description" to "详细说明（可选）"
            ),
            "status" to mapOf(
                "type" to "string",
                "enum" to listOf("pending", "in_progress", "completed"),
                "description" to "状态，默认 pending。in_progress 表示正在处理，completed 表示已完成。"
            ),
            "priority" to mapOf(
                "type" to "integer",
                "description" to "优先级，0=普通，越大越优先"
            )
        ),
        "required" to listOf("subject")
    )

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "操作类型",
            required = true,
            enum = listOf("create", "update", "delete", "list")
        ),
        "todos" to ToolParameter(
            name = "todos",
            type = ParameterType.ARRAY,
            description = "待办项列表（create 时必填，每项含 subject 等字段）",
            required = false,
            itemsSchema = todoItemSchema
        ),
        "todo_id" to ToolParameter(
            name = "todo_id",
            type = ParameterType.STRING,
            description = "待办项 ID（update/delete 时必填）",
            required = false
        ),
        "status" to ToolParameter(
            name = "status",
            type = ParameterType.STRING,
            description = "更新时的目标状态（update 时可选，pending/in_progress/completed）",
            required = false,
            enum = listOf("pending", "in_progress", "completed")
        ),
        "subject" to ToolParameter(
            name = "subject",
            type = ParameterType.STRING,
            description = "更新时的目标标题（update 时可选）",
            required = false
        ),
        "description" to ToolParameter(
            name = "description",
            type = ParameterType.STRING,
            description = "更新时的目标描述（update 时可选）",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return ToolResult.Error("TodoTool requires context", "MISSING_CONTEXT")
    }

    override suspend fun executeWithContext(
        args: Map<String, JsonElement>,
        context: AgentContext
    ): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("缺少 action 参数", "MISSING_ACTION")
        val sessionId = context.sessionId
            ?: return ToolResult.Error("未关联会话", "NO_SESSION")

        return try {
            when (action) {
                "create" -> createTodos(args, sessionId)
                "update" -> updateTodo(args, sessionId)
                "delete" -> deleteTodo(args, sessionId)
                "list" -> listTodos(sessionId)
                else -> ToolResult.Error("未知的 action: $action", "UNKNOWN_ACTION")
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "todo 工具执行失败: ${e.message}", e)
            ToolResult.Error("待办操作失败: ${e.message}")
        }
    }

    private suspend fun createTodos(args: Map<String, JsonElement>, sessionId: String): ToolResult {
        val todosJson = args["todos"]?.jsonArray
        if (todosJson == null || todosJson.isEmpty()) {
            return ToolResult.Error("create 需要 todos 数组且不能为空", "MISSING_TODOS")
        }

        val maxOrder = todoItemDao.getMaxOrder(sessionId) ?: -1
        val now = System.currentTimeMillis()
        val entities = mutableListOf<TodoItemEntity>()

        for ((idx, element) in todosJson.withIndex()) {
            val obj = element.jsonObject
            val subject = obj["subject"]?.jsonPrimitive?.contentOrNull?.trim()
            if (subject.isNullOrEmpty()) {
                return ToolResult.Error("第 ${idx + 1} 项缺少 subject", "MISSING_SUBJECT")
            }
            val description = obj["description"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val statusStr = obj["status"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
                ?: "PENDING"
            val status = try { TodoStatus.valueOf(statusStr) } catch (_: Exception) {
                return ToolResult.Error("第 ${idx + 1} 项 status 无效: $statusStr", "INVALID_STATUS")
            }
            val priority = obj["priority"]?.jsonPrimitive?.intOrNull ?: 0

            entities.add(TodoItemEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                subject = subject,
                description = description,
                status = status.name,
                priority = priority,
                order = maxOrder + idx + 1,
                createdAt = now,
                updatedAt = now
            ))
        }

        todoItemDao.upsertAll(entities)
        FileLogger.d(TAG, "todo create: 创建了 ${entities.size} 项待办")

        return listTodos(sessionId)
    }

    private suspend fun updateTodo(args: Map<String, JsonElement>, sessionId: String): ToolResult {
        val todoId = args["todo_id"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("update 需要 todo_id", "MISSING_TODO_ID")

        val existing = todoItemDao.getBySessionOnce(sessionId).find { it.id == todoId }
            ?: return ToolResult.Error("未找到待办项: $todoId", "NOT_FOUND")

        val now = System.currentTimeMillis()
        var updated = existing

        // 更新 status
        val statusStr = args["status"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
        if (statusStr != null) {
            try {
                TodoStatus.valueOf(statusStr)
                updated = updated.copy(status = statusStr, updatedAt = now)
            } catch (_: Exception) {
                return ToolResult.Error("无效 status: $statusStr", "INVALID_STATUS")
            }
        }

        // 更新 subject
        val subject = args["subject"]?.jsonPrimitive?.contentOrNull?.trim()
        if (!subject.isNullOrEmpty()) {
            updated = updated.copy(subject = subject, updatedAt = now)
        }

        // 更新 description
        if (args.containsKey("description")) {
            val desc = args["description"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            updated = updated.copy(description = desc, updatedAt = now)
        }

        todoItemDao.upsert(updated)
        FileLogger.d(TAG, "todo update: 更新了 $todoId → status=${updated.status}")

        return listTodos(sessionId)
    }

    private suspend fun deleteTodo(args: Map<String, JsonElement>, sessionId: String): ToolResult {
        val todoId = args["todo_id"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult.Error("delete 需要 todo_id", "MISSING_TODO_ID")

        val existing = todoItemDao.getBySessionOnce(sessionId).find { it.id == todoId }
            ?: return ToolResult.Error("未找到待办项: $todoId", "NOT_FOUND")

        todoItemDao.delete(todoId)
        FileLogger.d(TAG, "todo delete: 删除了 $todoId")

        return listTodos(sessionId)
    }

    private suspend fun listTodos(sessionId: String): ToolResult {
        val items = todoItemDao.getBySessionOnce(sessionId)
        val total = items.size
        val completed = items.count { it.status == "COMPLETED" }

        val resultJson = buildString {
            append("{\"total\":$total,\"completed\":$completed,\"items\":[")
            items.forEachIndexed { idx, entity ->
                if (idx > 0) append(",")
                append("{")
                append("\"id\":\"${entity.id}\",")
                append("\"subject\":\"${escapeJson(entity.subject)}\",")
                append("\"description\":\"${escapeJson(entity.description)}\",")
                append("\"status\":\"${entity.status.lowercase()}\",")
                append("\"priority\":${entity.priority},")
                append("\"order\":${entity.order}")
                append("}")
            }
            append("]}")
        }

        return ToolResult.Success(JsonObject(mapOf(
            "total" to JsonPrimitive(total),
            "completed" to JsonPrimitive(completed),
            "items" to kotlinx.serialization.json.JsonArray(
                items.map { entity ->
                    JsonObject(mapOf(
                        "id" to JsonPrimitive(entity.id),
                        "subject" to JsonPrimitive(entity.subject),
                        "description" to JsonPrimitive(entity.description),
                        "status" to JsonPrimitive(entity.status.lowercase()),
                        "priority" to JsonPrimitive(entity.priority),
                        "order" to JsonPrimitive(entity.order)
                    ))
                }
            )
        )))
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
