package com.aicodeeditor.feature.agent.domain.tool.todo

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.data.local.dao.TodoItemDao
import com.aicodeeditor.feature.agent.data.local.entity.TodoItemEntity
import com.aicodeeditor.feature.agent.domain.model.AgentContext
import com.aicodeeditor.feature.agent.domain.model.TodoStatus
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolCapability
import com.aicodeeditor.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

/**
 * 管理 AI Agent 当前会话的任务清单（待办列表）。
 *
 * 工具采用快照式接口：每次传入当前完整 items 列表，工具用它替换会话里的待办清单。
 * AI 不需要查询或记忆 todo_id，也不需要区分创建、更新、删除动作。
 */
class TodoTool @Inject constructor(
    private val todoItemDao: TodoItemDao
) : AgentTool() {

    private companion object {
        const val TAG = "TodoTool"
    }

    override val name = "todo"
    override val description = "用当前完整 items 列表替换会话任务清单。只需提交完整列表，" +
        "无需 action、todo_id 或单项更新。items 可为空数组表示清空；每项为 " +
        "{subject, description, status, priority}。status 可为 pending、in_progress、completed。"

    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.MODIFY_TODO_STATE)

    /** items 数组中单个待办项的 JSON Schema */
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
        "items" to ToolParameter(
            name = "items",
            type = ParameterType.ARRAY,
            description = "当前完整待办列表。传空数组会清空任务清单；每项为含 subject 的对象。",
            required = true,
            itemsSchema = todoItemSchema
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return ToolResult.Error("TodoTool requires context", "MISSING_CONTEXT")
    }

    override suspend fun executeWithContext(
        args: Map<String, JsonElement>,
        context: AgentContext
    ): ToolResult {
        val sessionId = context.sessionId
            ?: return ToolResult.Error("未关联会话", "NO_SESSION")

        return try {
            replaceTodos(args, sessionId)
        } catch (e: Exception) {
            FileLogger.e(TAG, "todo 工具执行失败: ${e.message}", e)
            ToolResult.Error("待办操作失败: ${e.message}")
        }
    }

    private suspend fun replaceTodos(args: Map<String, JsonElement>, sessionId: String): ToolResult {
        val itemElements = args["items"] as? JsonArray
            ?: return ToolResult.Error("需要 items 数组", "MISSING_ITEMS")
        val existingBySubject = todoItemDao.getBySessionOnce(sessionId)
            .groupBy { normalizeSubject(it.subject) }
            .mapValues { (_, items) -> items.toMutableList() }
        val now = System.currentTimeMillis()
        val entities = mutableListOf<TodoItemEntity>()

        for ((idx, element) in itemElements.withIndex()) {
            val draft = parseTodoDraft(element, idx) ?: return ToolResult.Error(
                "第 ${idx + 1} 项需要字符串标题或含 subject 的对象",
                "INVALID_ITEM"
            )
            val previous = existingBySubject[normalizeSubject(draft.subject)]?.removeFirstOrNull()

            entities.add(TodoItemEntity(
                id = previous?.id ?: UUID.randomUUID().toString(),
                sessionId = sessionId,
                subject = draft.subject,
                description = draft.description,
                status = draft.status.name,
                priority = draft.priority,
                order = idx,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now
            ))
        }

        todoItemDao.deleteBySession(sessionId)
        if (entities.isNotEmpty()) {
            todoItemDao.upsertAll(entities)
        }
        FileLogger.d(TAG, "todo replace: 同步了 ${entities.size} 项待办")

        return listTodos(sessionId)
    }

    private suspend fun listTodos(sessionId: String): ToolResult {
        val items = todoItemDao.getBySessionOnce(sessionId)
        val total = items.size
        val completed = items.count { it.status == "COMPLETED" }

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

    private fun parseTodoDraft(element: JsonElement, index: Int): TodoDraft? {
        if (element is JsonPrimitive) {
            val subject = element.contentOrNull?.trim().orEmpty()
            return if (subject.isBlank()) null else TodoDraft(subject = subject)
        }

        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        val subject = obj["subject"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (subject.isBlank()) return null

        val status = parseStatus(obj["status"]?.jsonPrimitive?.contentOrNull)
            ?: throw IllegalArgumentException("第 ${index + 1} 项 status 无效")

        return TodoDraft(
            subject = subject,
            description = obj["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            status = status,
            priority = obj["priority"]?.jsonPrimitive?.intOrNull ?: 0
        )
    }

    private fun parseStatus(raw: String?): TodoStatus? {
        val normalized = raw
            ?.trim()
            ?.replace("-", "_")
            ?.replace(" ", "_")
            ?.uppercase()
            ?: return TodoStatus.PENDING
        return runCatching { TodoStatus.valueOf(normalized) }.getOrNull()
    }

    private fun normalizeSubject(subject: String): String {
        return subject.trim().lowercase()
    }
}

private data class TodoDraft(
    val subject: String,
    val description: String = "",
    val status: TodoStatus = TodoStatus.PENDING,
    val priority: Int = 0
)
