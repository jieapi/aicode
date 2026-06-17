package com.aicodeeditor.feature.agent.domain.container

/**
 * 累积命令输出但限制内存占用：保留「开头 [headLimit] 个字符」与「结尾 [tailLimit] 个字符」，
 * 丢弃中间，避免超大输出（如 `cat` 大文件 / 安装日志）撑爆内存、模型上下文与 UI 渲染。
 *
 * 为何 head+tail 都留：开头通常含命令意图与起始信息，结尾通常含错误与最终结果，二者对模型
 * 与用户最有价值；中间的海量重复输出可安全省略，并在省略处标注被丢弃的字符数。
 *
 * 非线程安全：仅供单一读取循环顺序 append 使用。
 */
class BoundedOutput(
    private val headLimit: Int = DEFAULT_HEAD,
    private val tailLimit: Int = DEFAULT_TAIL
) {
    private val head = StringBuilder()
    // 仅保留最近 tailLimit 个字符的滑动窗口（超出即从头部丢弃）。
    private val tail = StringBuilder()
    private var total = 0L

    /** 已接收的原始字符总数（截断之前）。 */
    val totalChars: Long get() = total

    /** 是否发生了中间截断（接收量超过 head+tail 容量）。 */
    val truncated: Boolean get() = total > head.length.toLong() + tail.length.toLong()

    fun append(text: String) {
        if (text.isEmpty()) return
        total += text.length
        if (head.length < headLimit) {
            val room = headLimit - head.length
            if (text.length <= room) {
                head.append(text)
                return
            }
            // 先把开头填满，剩余溢出进入尾部窗口。
            head.append(text, 0, room)
            appendTail(text, room, text.length)
        } else {
            appendTail(text, 0, text.length)
        }
    }

    private fun appendTail(text: String, start: Int, end: Int) {
        tail.append(text, start, end)
        if (tail.length > tailLimit) tail.delete(0, tail.length - tailLimit)
    }

    /** 组装最终文本：未截断时即完整输出；截断时为 head + 省略提示 + tail。 */
    fun build(): String {
        if (!truncated) return head.toString() + tail.toString()
        val omitted = total - head.length - tail.length
        return buildString {
            append(head)
            append("\n\n…[输出过长，已省略中间 ")
            append(omitted)
            append(" 个字符；仅保留开头与结尾]…\n\n")
            append(tail)
        }
    }

    companion object {
        const val DEFAULT_HEAD = 20_000
        const val DEFAULT_TAIL = 20_000
    }
}
