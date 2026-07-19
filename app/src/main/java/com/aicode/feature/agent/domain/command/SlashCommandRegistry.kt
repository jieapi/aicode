package com.aicode.feature.agent.domain.command

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 斜杠命令注册表。通过 Hilt multibinding 在构造时汇集所有 [SlashCommandHandler]。
 *
 * 用法：
 * - 输入框实时过滤：[filterByPrefix] 返回 [SlashCommandHandler.trigger] 以输入文本开头的命令。
 * - 发送时完全匹配：[findExact] 返回 [SlashCommandHandler.matches] 命中的命令。
 *
 * 新增命令无需改动此类——实现 [SlashCommandHandler] + `@Binds @IntoSet` 即自动纳入。
 */
@Singleton
class SlashCommandRegistry @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards SlashCommandHandler>
) {
    /** 所有已注册命令，按 trigger 排序，保证菜单顺序稳定。 */
    val all: List<SlashCommandHandler> = handlers.sortedBy { it.trigger }

    /**
     * 前缀过滤：用于输入框实时匹配。
     * 当 [input] 为 "/" 时返回全部；否则返回 trigger 以 input 开头的命令。
     * 仅当 input 以 '/' 开头且不含换行时有意义，调用方负责此前提。
     */
    fun filterByPrefix(input: String): List<SlashCommandHandler> {
        if (input == "/") return all
        return all.filter { it.trigger.startsWith(input) }
    }

    /** 完全匹配查找：发送时调用，命中返回对应 handler，否则 null。 */
    fun findExact(input: String): SlashCommandHandler? =
        all.firstOrNull { it.matches(input) }
}
