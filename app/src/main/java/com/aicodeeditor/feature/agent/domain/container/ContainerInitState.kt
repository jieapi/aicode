package com.aicodeeditor.feature.agent.domain.container

/**
 * 容器初始化的实时进度状态。由 [LinuxContainerEngine.initProgress] 暴露，供终端页 / AI 页等所有
 * 初始化入口共享同一份进度（engine 为 @Singleton）。StateFlow 自动 conflation，高频更新只保留最新值。
 */
sealed interface ContainerInitState {
    /** 尚未开始初始化。 */
    data object Idle : ContainerInitState

    /** 正在解压 Alpine rootfs，[processed] 为已处理的 tar 条目数。 */
    data class ExtractingRootfs(val processed: Int) : ContainerInitState

    /** 正在部署 proot 二进制 / loader / 动态依赖库。 */
    data object DeployingProot : ContainerInitState

    /** 正在安装基础包（python3/git/pip/node/npm），[line] 为 apk 的实时输出行（可为 null）。 */
    data class InstallingPackages(val line: String?) : ContainerInitState

    /** 容器已就绪（rootfs + proot + 基础包均完成）。 */
    data object Ready : ContainerInitState

    /** 初始化失败，[reason] 为原因。 */
    data class Failed(val reason: String) : ContainerInitState
}
