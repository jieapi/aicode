package com.aicode.feature.agent.domain.container

import kotlinx.serialization.Serializable

/**
 * 一个可切换的容器配置：镜像来源 + shell 路径 + 额外 proot 绑定/参数。
 *
 * 内置 [BUILTIN_ALPINE] 描述现有 Alpine rootfs（来自 assets），其行为与改动前逐字等价。
 * 用户自定义 profile 通过导入 tar.gz 提供 rootfs，只保证能起 shell 跑命令——不 provision、
 * 不接管镜像源与包管理，所需工具由用户自行在容器内安装。
 */
@Serializable
data class ContainerProfile(
    val id: String,
    val name: String,
    val rootfsSource: RootfsSource,
    /** 自定义镜像用的 shell（如 /bin/sh 或 /bin/bash）；内置忽略，走 provision 后的 bash/ash 选择。 */
    val shellPath: String?,
    /** 额外 -b 绑定，如 ["/sdcard:/mnt/sdcard"]，逐项作为 `-b <binding>` 拼进 proot argv。 */
    val extraBindings: List<String> = emptyList(),
    /** 额外 proot 参数，原样追加到基础 argv（如 ["-k","..."]）。 */
    val extraArgs: List<String> = emptyList(),
    val isBuiltin: Boolean
) {
    companion object {
        const val BUILTIN_ID = "builtin-alpine"

        /** 内置 Alpine profile：镜像来自 assets，复用现有安装/provision 全流程。 */
        val BUILTIN_ALPINE = ContainerProfile(
            id = BUILTIN_ID,
            name = "内置 Alpine",
            rootfsSource = RootfsSource.Asset("alpine-rootfs.bin"),
            shellPath = null,
            isBuiltin = true
        )
    }
}

@Serializable
sealed interface RootfsSource {
    /** 内置：assets 里的 rootfs 文件（[ContainerProfile.BUILTIN_ALPINE] 用）。 */
    @Serializable
    data class Asset(val path: String) : RootfsSource

    /** 用户导入的 tar.gz，经 content uri 引用，解压到 filesDir/rootfs_<id>。 */
    @Serializable
    data class LocalFile(val uri: String) : RootfsSource
}
