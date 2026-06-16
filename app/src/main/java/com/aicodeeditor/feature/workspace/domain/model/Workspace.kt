package com.aicodeeditor.feature.workspace.domain.model

/**
 * 一个工作区 = App 私有项目根目录下的一个子文件夹。
 *
 * AI 的文件工具与命令执行都以 [path] 为根，切换工作区即切换 AI 的操作范围。
 */
data class Workspace(
    /** 文件夹名，作为唯一标识。 */
    val name: String,
    /** 绝对路径，可直接用于 java.io.File 与容器挂载。 */
    val path: String
)
