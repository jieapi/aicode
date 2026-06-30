package com.aicodeeditor.feature.agent.domain.container

import android.content.Context
import android.system.Os
import com.aicodeeditor.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 负责把打进 assets 的 Alpine rootfs 与 PRoot 二进制安装到 App 私有目录。
 *
 * 自动根据设备架构（ARM / x86）加载对应资源。targetSdk 锁定 28，数据目录文件才可执行（见 build.gradle.kts）。
 */
@Singleton
class ContainerInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ContainerInstaller"
        @Volatile private var docsExtractedSession = false

        /** 从 assets 提取文档到 ~/.aicode/docs (内置使用指导) */
        fun extractDocs(context: Context) {
            if (docsExtractedSession) return
            val destDir = File(File(context.filesDir, "aicode"), "docs")
            destDir.mkdirs()
            runCatching {
                val docs = context.assets.list("docs") ?: return
                for (doc in docs) {
                    val destFile = File(destDir, doc)
                    context.assets.open("docs/$doc").use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                docsExtractedSession = true
            }.onFailure {
                FileLogger.w(TAG, "提取内置文档失败: ${it.message}", it)
            }
        }

        /**
         * 与 assets 里 alpine-rootfs 版本对应的 apk 分支，用于拼镜像源地址。
         * 固定 v3.21：与 [INSTALL_VERSION]（alpine-3.21.3）一致；该版本 apk-tools 2.14 在 proot 下可靠。
         */
        const val ALPINE_BRANCH = "v3.21"

        /**
         * apk 镜像源；用阿里云国内镜像替代官方 dl-cdn，避免国外源过慢/被墙。
         *
         * 用 http 而非 https：Alpine minirootfs 不含 ca-certificates，容器内 apk 走自己的原生 TLS，
         * 无 CA 证书库会导致 HTTPS 握手失败并被 apk 误报成 "Permission denied"（首次安装直接卡死）。
         * apk 对索引与每个 .apk 都用 /etc/apk/keys 的签名独立校验，故 http 传输仍保证完整性。
         */
        const val ALPINE_MIRROR = "http://mirrors.aliyun.com/alpine"

        /** assets 内的架构特定目录 */
        val ASSET_DIR: String
            get() {
                val abis = android.os.Build.SUPPORTED_ABIS
                return if (abis.any { it.contains("x86") }) {
                    "container/x86"
                } else {
                    "container/arm"
                }
            }
            
        val ASSET_PROOT: String get() = "$ASSET_DIR/proot"
        // Termux proot 的 loader 分离（靠 PROOT_LOADER 定位），且动态依赖 libtalloc / libandroid-shmem。
        val ASSET_LOADER: String get() = "$ASSET_DIR/loader"
        val ASSET_LOADER32: String get() = "$ASSET_DIR/loader32"
        val ASSET_LIBTALLOC: String get() = "$ASSET_DIR/libtalloc.so.2"
        val ASSET_LIBSHMEM: String get() = "$ASSET_DIR/libandroid-shmem.so"
        // 故意用中性的 .bin 后缀：AGP 的 asset 合并会把 .tar.gz/.tgz 当归档自动解压并改名，
        // 导致运行时 open("...tar.gz") 找不到文件。.bin 让它当普通二进制原样打包。
        val ASSET_ROOTFS: String get() = "$ASSET_DIR/alpine-rootfs.bin"

        /**
         * 安装版本。换 rootfs / proot 或改安装逻辑时 +1，触发重新解压。
         * 与 assets 里实际放的 Alpine 版本保持一致以便排查。
         */
        private const val INSTALL_VERSION = "alpine-3.21.3-v6"
    }

    /** rootfs 解压根目录 */
    val rootfsDir: File
        get() = File(context.filesDir, "rootfs")

    /**
     * AI 配置数据根目录（skill 指令 + MCP 配置），固定在 app 私有 filesDir。
     *
     * 刻意**独立于 [rootfsDir]**：rootfs 在容器版本升级时会被整体删除重装（见 [installRootfsIfNeed]），
     * 而本目录承载用户数据，必须跨升级保留。它由 [com.aicodeeditor.feature.agent.domain.container.LinuxContainerEngine]
     * 绑定到容器内 `/root/.aicode`，故 AI / 终端看到的 `/root/.aicode` 实际落在这里。
     */
    val aicodeDir: File
        get() = File(context.filesDir, "aicode")

    /** PRoot 可执行文件路径（Termux 构建，含 statx，动态链接 libtalloc/libandroid-shmem） */
    val prootBin: File
        get() = File(context.filesDir, "container/bin/proot")

    /** PRoot 的 64/32 位 loader（Termux proot loader 分离，由 PROOT_LOADER/_32 指向）。 */
    val prootLoader: File
        get() = File(context.filesDir, "container/bin/loader")
    val prootLoader32: File
        get() = File(context.filesDir, "container/bin/loader32")

    /** proot 的动态依赖库目录（libtalloc.so.2 / libandroid-shmem.so），由 LD_LIBRARY_PATH 指向。 */
    val prootLibDir: File
        get() = File(context.filesDir, "container/lib")

    /** PRoot 在 Android 上必须的临时目录（Android 没有 /tmp） */
    val prootTmpDir: File
        get() = File(context.cacheDir, "proot_tmp")

    /** 标记文件，内容是已安装的版本号 */
    private val installedMarker: File
        get() = File(rootfsDir, ".installed")

    /** 检查 rootfs 与 proot 是否已按当前版本安装就绪 */
    fun isInstalled(): Boolean {
        if (!prootBin.exists() || !rootfsDir.isDirectory) return false
        val marker = installedMarker
        return marker.exists() && marker.readText().trim() == INSTALL_VERSION
    }

    /**
     * 若未安装（或版本不匹配）则从 assets 解压安装。幂等，可在每次执行命令前调用。
     *
     * [onProgress] 在真正解压/部署的各阶段被回调以更新 [ContainerInitState]；已安装的快路径不会调用。
     */
    suspend fun installRootfsIfNeed(
        onProgress: (ContainerInitState) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (isInstalled()) return@withContext

        FileLogger.i(TAG, "开始安装容器 rootfs（版本 $INSTALL_VERSION）")

        // 版本不匹配时清掉旧的，保证干净安装
        if (rootfsDir.exists()) rootfsDir.deleteRecursively()
        rootfsDir.mkdirs()

        onProgress(ContainerInitState.DeployingProot)
        installProot()
        extractRootfs(onProgress)
        configureResolvConf()
        configureApkRepositories()
        prootTmpDir.mkdirs()

        installedMarker.writeText(INSTALL_VERSION)
        FileLogger.i(TAG, "容器 rootfs 安装完成")
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            extractDocs(context)
        }
    }

    /** 从 assets 提取文档到 ~/.aicode/docs (内置使用指导) */
    fun extractDocs() = extractDocs(context)

    /** 从 assets 复制 proot 全套（二进制 + loader + 动态依赖库）到私有目录并赋权限 */
    private fun installProot() {
        // 二进制与 loader：需可执行
        copyAsset(ASSET_PROOT, prootBin, executable = true)
        copyAsset(ASSET_LOADER, prootLoader, executable = true)
        copyAsset(ASSET_LOADER32, prootLoader32, executable = true)
        // 动态依赖库：放到 lib 目录，由 LD_LIBRARY_PATH 指向；可读即可（给可执行位无害）
        copyAsset(ASSET_LIBTALLOC, File(prootLibDir, "libtalloc.so.2"), executable = true)
        copyAsset(ASSET_LIBSHMEM, File(prootLibDir, "libandroid-shmem.so"), executable = true)
    }

    /** 把单个 asset 复制到目标文件，按需赋「对所有用户」的可执行位（proot 进程以 App uid 运行）。 */
    private fun copyAsset(assetPath: String, dest: File, executable: Boolean) {
        dest.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (executable && !dest.setExecutable(true, false)) {
            FileLogger.w(TAG, "setExecutable 返回 false: ${dest.absolutePath}")
        }
    }

    /** 解压 alpine-minirootfs.tar.gz，正确处理目录/文件/符号链接/硬链接与权限位 */
    private fun extractRootfs(onProgress: (ContainerInitState) -> Unit) {
        var processed = 0
        context.assets.open(ASSET_ROOTFS).use { rawIn ->
            GZIPInputStream(rawIn).use { gzipIn ->
                TarArchiveInputStream(gzipIn).use { tarIn ->
                    var entry: TarArchiveEntry? = tarIn.nextEntry
                    while (entry != null) {
                        extractEntry(tarIn, entry)
                        processed++
                        onProgress(ContainerInitState.ExtractingRootfs(processed))
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
    }

    private fun extractEntry(tarIn: TarArchiveInputStream, entry: TarArchiveEntry) {
        val outFile = File(rootfsDir, entry.name)

        // 防 zip-slip：确保解压目标落在 rootfsDir 内
        val canonicalRoot = rootfsDir.canonicalPath
        if (!outFile.canonicalPath.startsWith(canonicalRoot + File.separator) &&
            outFile.canonicalPath != canonicalRoot
        ) {
            FileLogger.w(TAG, "跳过越界条目: ${entry.name}")
            return
        }

        when {
            entry.isDirectory -> outFile.mkdirs()

            entry.isSymbolicLink -> {
                outFile.parentFile?.mkdirs()
                // symlink 的目标可能是相对/绝对路径，原样创建（在容器内由 proot 解析）
                if (outFile.exists()) outFile.delete()
                runCatching { Os.symlink(entry.linkName, outFile.absolutePath) }
                    .onFailure { FileLogger.w(TAG, "symlink 失败 ${entry.name} -> ${entry.linkName}: ${it.message}") }
            }

            entry.isLink -> {
                // 硬链接：linkName 指向 tar 内已解压的另一文件
                outFile.parentFile?.mkdirs()
                val target = File(rootfsDir, entry.linkName)
                if (outFile.exists()) outFile.delete()
                runCatching { Os.link(target.absolutePath, outFile.absolutePath) }
                    .onFailure {
                        // 退化为复制，保证文件存在
                        FileLogger.w(TAG, "hardlink 失败 ${entry.name} -> ${entry.linkName}，改为复制: ${it.message}")
                        runCatching { target.copyTo(outFile, overwrite = true) }
                    }
            }

            entry.isFile -> {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { tarIn.copyTo(it) }
                applyMode(outFile, entry.mode)
            }

            else -> FileLogger.d(TAG, "忽略不支持的条目类型: ${entry.name}")
        }
    }

    /** 按 tar entry 的 mode 设置可执行位（owner 有 x 位则对所有人开放执行） */
    private fun applyMode(file: File, mode: Int) {
        val ownerExecutable = (mode and 0b001_000_000) != 0 // 0100
        if (ownerExecutable) {
            file.setExecutable(true, false)
        }
        file.setReadable(true, false)
        // owner 写位
        if ((mode and 0b010_000_000) != 0) file.setWritable(true, false)
    }

    /**
     * 写入容器内 DNS，否则 apk/npm 等联网操作会因无法解析域名而失败。
     * 用阿里云公共 DNS：国内解析更快/更稳，8.8.8.8 在部分网络环境会被拦截。
     */
    private fun configureResolvConf() {
        val etc = File(rootfsDir, "etc").apply { mkdirs() }
        File(etc, "resolv.conf").writeText("nameserver 223.5.5.5\nnameserver 223.6.6.6\n")
    }

    /**
     * 写入容器内 apk 源为阿里云国内镜像（[ALPINE_MIRROR]），替代 minirootfs 自带的官方 dl-cdn 源，
     * 否则首次 `apk add` 在国内会极慢或超时。启用 main + community 两个仓库。
     *
     * 用 http 的原因见 [ALPINE_MIRROR] 注释。provision 流程会再幂等覆盖一次以兜底存量（已解压旧 rootfs）设备。
     */
    private fun configureApkRepositories() {
        val apkDir = File(rootfsDir, "etc/apk").apply { mkdirs() }
        File(apkDir, "repositories").writeText(
            "$ALPINE_MIRROR/$ALPINE_BRANCH/main\n" +
                "$ALPINE_MIRROR/$ALPINE_BRANCH/community\n"
        )
    }
}
