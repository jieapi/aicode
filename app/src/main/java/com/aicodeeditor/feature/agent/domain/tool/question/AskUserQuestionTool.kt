package com.aicodeeditor.feature.agent.domain.tool.question

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.tool.AgentTool
import com.aicodeeditor.feature.agent.domain.tool.ParameterType
import com.aicodeeditor.feature.agent.domain.tool.ToolParameter
import com.aicodeeditor.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

/**
 * 让 AI 向用户提出结构化的选择题。
 *
 * 当 AI 遇到需要用户决策的场景（选库、选方案、确认环境安装等）时，调用本工具可暂停执行、
 * 在 UI 弹出一个问题面板、等待用户做出选择，然后拿到用户的回答继续工作。
 *
 * 每次调用可同时问 1-4 个问题，每个问题 2-4 个预设选项，支持单选/多选。
 * 用户侧每个问题自动附带一个「其他」选项，选中后可输入自定义文本。
 */
class AskUserQuestionTool @Inject constructor(
    private val questionManager: AskUserQuestionManager
) : AgentTool() {

    private companion object {
        const val TAG = "AskUserQuestionTool"
        const val MAX_QUESTIONS = 4
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 4
    }

    override val name = "ask_user_question"

    override val description =
        "向用户提出结构化的选择题，等待用户做出选择后返回其回答。" +
            "用于需要用户决策的场景：选择库/框架/方案、确认环境安装、在多个选项间抉择等。" +
            "每次可问 1-4 个问题，每个问题 2-4 个选项。用户可选择预设选项或输入自定义答案。" +
            "返回用户对每个问题的回答（选中的选项标签及可能的自定义文本）。"

    /** 单个选项的 JSON Schema */
    private val optionItemSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "label" to mapOf(
                "type" to "string",
                "description" to "选项显示文本（简短 1-5 个词），如「OkHttp（推荐）」。"
            ),
            "description" to mapOf(
                "type" to "string",
                "description" to "选项说明，解释选它意味着什么或有什么利弊。"
            )
        ),
        "required" to listOf("label", "description")
    )

    /** 单个问题的 JSON Schema */
    private val questionItemSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "question" to mapOf(
                "type" to "string",
                "description" to "完整的问题文本，应清晰、具体、以问号结尾。"
            ),
            "header" to mapOf(
                "type" to "string",
                "description" to "短标签（≤12 字符），展示为标签头，如「方案」「库」「工具」。"
            ),
            "options" to mapOf(
                "type" to "array",
                "description" to "2-4 个预设选项。UI 会自动追加一个「其他」选项供用户自由输入。" +
                    "如果你有推荐项，放在第一位并在 label 末尾加「（推荐）」。",
                "items" to optionItemSchema
            ),
            "multiSelect" to mapOf(
                "type" to "boolean",
                "description" to "是否允许多选。默认 false（单选）。多选时问题表述应相应调整。"
            )
        ),
        "required" to listOf("question", "header", "options")
    )

    override val parameters = mapOf(
        "questions" to ToolParameter(
            name = "questions",
            type = ParameterType.ARRAY,
            description = "要问用户的问题列表（1-4 个问题）。每个问题包含 question、header、options 和可选的 multiSelect。",
            required = true,
            itemsSchema = questionItemSchema
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val questionsJson = args["questions"]?.jsonArray
        if (questionsJson == null || questionsJson.isEmpty()) {
            return ToolResult.Error("缺少必需参数: questions（至少包含一个问题）", "MISSING_QUESTIONS")
        }

        if (questionsJson.size > MAX_QUESTIONS) {
            return ToolResult.Error(
                "最多只能同时提问 $MAX_QUESTIONS 个问题，当前传入了 ${questionsJson.size} 个",
                "TOO_MANY_QUESTIONS"
            )
        }

        // 解析每个问题
        val questions = mutableListOf<QuestionItem>()
        for ((idx, qElement) in questionsJson.withIndex()) {
            val qObj = qElement.jsonObject

            val questionText = qObj["question"]?.jsonPrimitive?.contentOrNull?.trim()
            if (questionText.isNullOrEmpty()) {
                return ToolResult.Error("第 ${idx + 1} 个问题缺少 question 字段", "INVALID_QUESTION")
            }

            val header = qObj["header"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""

            val optionsJson = qObj["options"]?.jsonArray
            if (optionsJson == null || optionsJson.size < MIN_OPTIONS) {
                return ToolResult.Error(
                    "第 ${idx + 1} 个问题至少需要 $MIN_OPTIONS 个选项",
                    "TOO_FEW_OPTIONS"
                )
            }
            if (optionsJson.size > MAX_OPTIONS) {
                return ToolResult.Error(
                    "第 ${idx + 1} 个问题最多 $MAX_OPTIONS 个选项，当前 ${optionsJson.size} 个",
                    "TOO_MANY_OPTIONS"
                )
            }

            val options = optionsJson.map { optElement ->
                val optObj = optElement.jsonObject
                QuestionOption(
                    label = optObj["label"]?.jsonPrimitive?.contentOrNull?.trim() ?: "",
                    description = optObj["description"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                )
            }.filter { it.label.isNotEmpty() }

            if (options.size < MIN_OPTIONS) {
                return ToolResult.Error(
                    "第 ${idx + 1} 个问题解析后有效选项不足 $MIN_OPTIONS 个",
                    "INVALID_OPTIONS"
                )
            }

            val multiSelect = qObj["multiSelect"]?.jsonPrimitive?.let {
                runCatching { it.boolean }.getOrNull()
            } ?: false

            questions.add(
                QuestionItem(
                    question = questionText,
                    header = header,
                    options = options,
                    multiSelect = multiSelect
                )
            )
        }

        val requestId = UUID.randomUUID().toString()
        val pending = PendingUserQuestion(id = requestId, questions = questions)

        FileLogger.d(TAG, "ask_user_question 发起: ${questions.size} 个问题，等待用户回答...")

        // 挂起，等待用户在 UI 做出选择
        val answer = questionManager.awaitAnswer(pending)

        FileLogger.d(TAG, "ask_user_question 用户已回答: ${answer.answers.size} 条")

        // 将用户回答序列化为 JSON 字符串，喂回给模型
        val resultText = buildString {
            if (answer.answers.isEmpty()) {
                append("用户跳过了问题，未做出选择。请根据合理默认值继续，或换一种方式提问。")
            } else {
                for (a in answer.answers) {
                    append("「${a.question}」= ")
                    if (a.customText != null) {
                        append("其他：${a.customText}")
                    } else {
                        append(a.selected.joinToString("、"))
                    }
                    append("\n")
                }
            }
        }.trim()

        return ToolResult.Success(JsonPrimitive(resultText))
    }
}
