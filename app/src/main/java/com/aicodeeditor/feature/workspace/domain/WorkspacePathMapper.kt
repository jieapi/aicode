package com.aicodeeditor.feature.workspace.domain

import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.agent.domain.container.ContainerInstaller
import com.aicodeeditor.feature.workspace.data.repository.WorkspaceRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在「容器内路径」与「宿主真实路径」之间互转，让 AI 只看到 / 只使用容器路径。
 *
 * 背景：容器是 PRoot 以 [ContainerInstaller.rootfsDir]（宿主 `filesDir/rootfs`）为根（`-r rootfs`）跑起来的，
 * 当前工作区目录又被 bind 成容器内的 [CONTAINER_ROOT]。因此「容器内路径」到「宿主真实文件」有两条确定映射：
 * - `/workspace[/…]` → 宿主工作区目录（写它即写宿主，且容器内可见——bind mount）；
 * - 其它容器绝对路径 `/etc/…`、`/root/…` → 宿主 `rootfsDir/…`（与终端在容器里看到的完全是同一批文件）。
 *
 * 这样文件类工具（read/write/edit）无需进 PRoot 即可读写整个容器文件系统，与 `execute_command` 看到的一致。
 *
 * 用本映射器统一：
 * - 工具入参（AI 给的路径）经 [toHostFile] 落到宿主真实文件；
 * - 工具回显/返回的路径经 [toContainerPath] 还原成容器视角（`/workspace/…` 或 `/etc/…`），对 AI 只暴露容器路径。
 */
@Singleton
class WorkspacePathMapper @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val containerInstaller: ContainerInstaller
) {
    companion object {
        const val CONTAINER_ROOT = "/workspace"
        /** AI 配置目录在容器内的路径，绑定到宿主 [ContainerInstaller.aicodeDir]（独立于 rootfs）。 */
        const val AICODE_ROOT = "/root/.aicode"
        private const val TAG = "WorkspacePathMapper"
    }

    /** 当前工作区在宿主上的根目录。 */
    private fun hostRoot(): File = File(workspaceRepository.currentPath())

    /** 容器 rootfs 在宿主上的根目录（容器内 `/` 即此目录）。 */
    private fun rootfsRoot(): File = containerInstaller.rootfsDir

    /** AI 配置目录在宿主上的根（容器内 `/root/.aicode` 即此目录，独立于 rootfs）。 */
    private fun aicodeRoot(): File = containerInstaller.aicodeDir

    /**
     * 把 AI 提供的路径解析为宿主真实文件。兼容以下写法：
     * - 容器绝对路径 `/workspace[/…]` → 映射到宿主工作区；
     * - 容器绝对路径 `/root/.aicode[/…]` → 映射到宿主 AI 配置目录（skill / mcp.json，独立于 rootfs）；
     * - 其它容器绝对路径 `/etc/…`、`/root/…` → 映射到 rootfs 内对应文件（容器系统文件）；
     * - 相对路径 `src/Main.kt` → 挂到宿主工作区根下；
     *
     * `/root/.aicode` 必须先于通用 `/`→rootfs 规则匹配，否则会落到 rootfs 内的临时副本（升级即丢）。
     */
    fun toHostFile(path: String): File {
        val root = hostRoot()
        val p = path.trim()
        val file = when {
            p == CONTAINER_ROOT || p == "$CONTAINER_ROOT/" -> root
            p.startsWith("$CONTAINER_ROOT/") -> File(root, p.removePrefix("$CONTAINER_ROOT/"))
            p == AICODE_ROOT || p == "$AICODE_ROOT/" -> aicodeRoot()
            p.startsWith("$AICODE_ROOT/") -> File(aicodeRoot(), p.removePrefix("$AICODE_ROOT/"))
            p.startsWith("/") -> File(rootfsRoot(), p.removePrefix("/"))
            else -> File(root, p)
        }
        FileLogger.v(TAG, "toHostFile '$path' -> ${file.absolutePath}")
        return file
    }

    /**
     * 把宿主路径还原为容器路径：
     * - 位于工作区内 → `/workspace/…`；
     * - 位于 AI 配置目录内 → `/root/.aicode/…`；
     * - 位于 rootfs 内 → 去掉 rootfs 前缀的容器绝对路径（如 `/etc/apk/repositories`）；
     * - 其余原样返回（极少出现）。
     *
     * 工作区在 `filesDir/projects`、AI 配置在 `filesDir/aicode`、rootfs 在 `filesDir/rootfs`，三者互不重叠，
     * 判断顺序无歧义。
     */
    fun toContainerPath(hostPath: String): String {
        val rootPath = hostRoot().absolutePath
        val aicodePath = aicodeRoot().absolutePath
        val rootfsPath = rootfsRoot().absolutePath
        val abs = File(hostPath).absolutePath
        return when {
            abs == rootPath -> CONTAINER_ROOT
            abs.startsWith("$rootPath/") -> CONTAINER_ROOT + "/" + abs.removePrefix("$rootPath/")
            abs == aicodePath -> AICODE_ROOT
            abs.startsWith("$aicodePath/") -> AICODE_ROOT + "/" + abs.removePrefix("$aicodePath/")
            abs == rootfsPath -> "/"
            abs.startsWith("$rootfsPath/") -> "/" + abs.removePrefix("$rootfsPath/")
            else -> hostPath
        }
    }
}
