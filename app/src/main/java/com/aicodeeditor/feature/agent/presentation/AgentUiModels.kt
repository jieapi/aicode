package com.aicodeeditor.feature.agent.presentation

import androidx.compose.runtime.Immutable
import com.aicodeeditor.feature.agent.domain.model.WorkflowStatus

sealed class AgentUIState {
    object Idle : AgentUIState()
    object Loading : AgentUIState()
    object Streaming : AgentUIState()
    data class Result(val status: WorkflowStatus) : AgentUIState()
    object Applied : AgentUIState()
    data class Error(val message: String) : AgentUIState()
}

/**
 * 网络请求重试状态（仅用于 UI 实时展示「正在重试 (N/M)...」提示）。
 * 与 [AgentUIState] 解耦：重试是 Streaming 的子状态，不改变顶层 agent 状态机。
 */
@Immutable
data class RetryState(val attempt: Int, val maxRetries: Int)

@Immutable
data class AgentUIMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    // 仅 TOOL 消息：渲染用，不参与上下文回放。
    val toolName: String? = null,
    // 仅 TOOL 消息：本次调用传入的参数（JSON 文本），渲染「执行的指令」用。
    val toolArgs: String? = null,
    val isError: Boolean = false,
    // 仅 ASSISTANT 消息：本轮模型的思考过程，渲染为可折叠「思考过程」气泡；无则为 null。
    val reasoning: String? = null
)

enum class MessageRole {
    USER, ASSISTANT, TOOL
}

/**
 * 一次会话消息查询的结果快照。
 * - [sessionId]：这批消息所属会话；null 表示当前还没有解析出会话（冷启动中）。
 * - [messages]：已过滤、可直接渲染的消息列表。
 * - [loaded]：是否已经从数据库读到该会话的数据，用以区分「加载中」与「空会话」。
 */
@Immutable
data class ChatMessagesState(
    val sessionId: String?,
    val messages: List<AgentUIMessage>,
    val loaded: Boolean,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false
)

/**
 * 「不出墨」的码点：虽非空白、也不属格式(Cf)/控制(Cc)，却渲染为零宽或纯空白。
 * 关键是 Hangul filler 一族——它们的 Unicode 类别是 Lo(其他字母)，所以**任何按类别判定的
 * 方案都抓不到**，必须显式列举。这是上一版「只过滤 Cf/Cc」修复失效的真正原因：部分模型在
 * 纯工具调用轮次吐出 U+3164 等填充字符，每调一次工具就漏出一个空气泡。
 */
internal val BLANK_GLYPH_CODE_POINTS: Set<Char> = setOf(
    0x115F, 0x1160, 0x3164, 0xFFA0, // Hangul filler（Lo，零宽，按类别抓不到）
    0x2800,                         // Braille pattern blank（So，纯空点）
    0x034F,                         // 组合用字位连接符 CGJ
    0x17B4, 0x17B5,                 // Khmer 固有元音（零宽）
    0x2060, 0xFEFF,                 // 词连接符 / BOM（Cf，冗余兜底）
    0x200B, 0x200C, 0x200D,         // 零宽空格 / ZWNJ / ZWJ（Cf，冗余兜底）
).mapTo(HashSet()) { it.toChar() }

/**
 * 文本是否含「可见(出墨)」内容。判定 = 至少有一个字符既非空白、又不属不可见类别(Cf/Cc/代理)、
 * 也不在 [BLANK_GLYPH_CODE_POINTS] 黑名单内。比 [CharSequence.isBlank] 严格得多：
 * 后者只把 whitespace 当空，会让零宽/填充字符漏出空白气泡。
 * 这是「是否渲染助手气泡」的唯一关门，持久化归一化与各渲染/过滤层共用它，改此一处即全链路生效。
 * 注意：保留代理对(emoji 等)与普通可见字符；零宽连接符 ZWJ 只在「整条文本是否为空」上当空，
 * 不会从展示文本里被剔除，故 emoji 连字序列不受影响。
 */
fun CharSequence.hasVisibleContent(): Boolean = any { ch ->
    !ch.isWhitespace() &&
        ch.category != CharCategory.FORMAT &&
        ch.category != CharCategory.CONTROL &&
        ch.category != CharCategory.SURROGATE &&
        ch !in BLANK_GLYPH_CODE_POINTS
}

/**
 * 运行中工具的实时累积输出。仅存内存、不落库：用于在该工具消息气泡里实时叠加显示
 * 命令逐行 stdout；命令结束后清空，最终完整结果走正常 persist 落库。
 */
@Immutable
data class RunningToolOutput(val messageId: String, val text: String, val toolName: String = "", val toolArgs: String = "")

data class QueuedRequest(
    val id: String,
    val request: String,
    val currentFile: String?,
    val selectedCode: String?,
    val projectRoot: String
)
