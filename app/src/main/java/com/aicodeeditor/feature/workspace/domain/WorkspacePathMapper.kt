package com.aicodeeditor.feature.workspace.domain

import com.aicodeeditor.feature.workspace.data.repository.WorkspaceRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在「容器内路径」与「宿主真实路径」之间互转，让 AI 只看到 / 只使用容器路径 [CONTAINER_ROOT]。
 *
 * 背景：当前工作区在宿主上是一个真实目录（如 `/storage/emulated/0/Android/data/…/projects/x`），
 * 经 PRoot 绑定为容器内的 [CONTAINER_ROOT]。`execute_command` 在容器里以 [CONTAINER_ROOT] 为
 * 工作目录执行，因此 AI 从 shell 看到的是 `/workspace/…`；但文件类工具直接操作宿主 [File] 并
 * 回显宿主绝对路径，两者混用会让模型困惑。
 *
 * 用本映射器统一：
 * - 工具入参（AI 给的路径）经 [toHostFile] 落到宿主真实文件；
 * - 工具回显/返回的路径经 [toContainerPath] 还原成 `/workspace/…`，对 AI 只暴露容器路径。
 */
@Singleton
class WorkspacePathMapper @Inject constructor(
    private val workspaceRepository: WorkspaceRepository
) {
    companion object {
        const val CONTAINER_ROOT = "/workspace"
    }

    /** 当前工作区在宿主上的根目录。 */
    private fun hostRoot(): File = File(workspaceRepository.currentPath())

    /**
     * 把 AI 提供的路径解析为宿主真实文件。兼容三种写法：
     * - 容器绝对路径 `/workspace[/…]` → 映射到宿主工作区；
     * - 相对路径 `src/Main.kt` → 挂到宿主工作区根下；
     * - 其它绝对路径 → 原样（容器系统路径等，少用；保证向后兼容旧的宿主路径）。
     */
    fun toHostFile(path: String): File {
        val root = hostRoot()
        val p = path.trim()
        return when {
            p == CONTAINER_ROOT || p == "$CONTAINER_ROOT/" -> root
            p.startsWith("$CONTAINER_ROOT/") -> File(root, p.removePrefix("$CONTAINER_ROOT/"))
            p.startsWith("/") -> File(p)
            else -> File(root, p)
        }
    }

    /**
     * 把宿主路径还原为容器路径：位于工作区内的转成 `/workspace/…`，
     * 其余原样返回（极少出现，如指向工作区外的系统文件）。
     */
    fun toContainerPath(hostPath: String): String {
        val rootPath = hostRoot().absolutePath
        val abs = File(hostPath).absolutePath
        return when {
            abs == rootPath -> CONTAINER_ROOT
            abs.startsWith("$rootPath/") -> CONTAINER_ROOT + "/" + abs.removePrefix("$rootPath/")
            else -> hostPath
        }
    }
}
