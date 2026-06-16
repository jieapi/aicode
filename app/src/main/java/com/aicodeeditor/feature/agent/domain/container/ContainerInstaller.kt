package com.aicodeeditor.feature.agent.domain.container

import android.content.Context
import android.system.Os
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
 * 仅支持 arm64-v8a。targetSdk 锁定 28，数据目录文件才可执行（见 build.gradle.kts）。
 */
@Singleton
class ContainerInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ContainerInstaller"

        /** assets 内的二进制位置 */
        private const val ASSET_DIR = "container"
        private const val ASSET_PROOT = "$ASSET_DIR/proot"
        // 故意用中性的 .bin 后缀：AGP 的 asset 合并会把 .tar.gz/.tgz 当归档自动解压并改名，
        // 导致运行时 open("...tar.gz") 找不到文件。.bin 让它当普通二进制原样打包。
        private const val ASSET_ROOTFS = "$ASSET_DIR/alpine-rootfs.bin"

        /**
         * 安装版本。换 rootfs / proot 或改安装逻辑时 +1，触发重新解压。
         * 与 assets 里实际放的 Alpine 版本保持一致以便排查。
         */
        private const val INSTALL_VERSION = "alpine-3.24.1-v1"
    }

    /** rootfs 解压根目录 */
    val rootfsDir: File
        get() = File(context.filesDir, "rootfs")

    /** PRoot 可执行文件路径 */
    val prootBin: File
        get() = File(context.filesDir, "container/bin/proot")

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
     */
    suspend fun installRootfsIfNeed() = withContext(Dispatchers.IO) {
        if (isInstalled()) return@withContext

        Log.i(TAG, "开始安装容器 rootfs（版本 $INSTALL_VERSION）")

        // 版本不匹配时清掉旧的，保证干净安装
        if (rootfsDir.exists()) rootfsDir.deleteRecursively()
        rootfsDir.mkdirs()

        installProot()
        extractRootfs()
        configureResolvConf()
        prootTmpDir.mkdirs()

        installedMarker.writeText(INSTALL_VERSION)
        Log.i(TAG, "容器 rootfs 安装完成")
    }

    /** 从 assets 复制 proot 到私有目录并赋可执行权限 */
    private fun installProot() {
        prootBin.parentFile?.mkdirs()
        context.assets.open(ASSET_PROOT).use { input ->
            prootBin.outputStream().use { output -> input.copyTo(output) }
        }
        // 对所有用户可执行（proot 进程以 App uid 运行）
        if (!prootBin.setExecutable(true, false)) {
            Log.w(TAG, "setExecutable 返回 false: ${prootBin.absolutePath}")
        }
    }

    /** 解压 alpine-minirootfs.tar.gz，正确处理目录/文件/符号链接/硬链接与权限位 */
    private fun extractRootfs() {
        context.assets.open(ASSET_ROOTFS).use { rawIn ->
            GZIPInputStream(rawIn).use { gzipIn ->
                TarArchiveInputStream(gzipIn).use { tarIn ->
                    var entry: TarArchiveEntry? = tarIn.nextEntry
                    while (entry != null) {
                        extractEntry(tarIn, entry)
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
            Log.w(TAG, "跳过越界条目: ${entry.name}")
            return
        }

        when {
            entry.isDirectory -> outFile.mkdirs()

            entry.isSymbolicLink -> {
                outFile.parentFile?.mkdirs()
                // symlink 的目标可能是相对/绝对路径，原样创建（在容器内由 proot 解析）
                if (outFile.exists()) outFile.delete()
                runCatching { Os.symlink(entry.linkName, outFile.absolutePath) }
                    .onFailure { Log.w(TAG, "symlink 失败 ${entry.name} -> ${entry.linkName}: ${it.message}") }
            }

            entry.isLink -> {
                // 硬链接：linkName 指向 tar 内已解压的另一文件
                outFile.parentFile?.mkdirs()
                val target = File(rootfsDir, entry.linkName)
                if (outFile.exists()) outFile.delete()
                runCatching { Os.link(target.absolutePath, outFile.absolutePath) }
                    .onFailure {
                        // 退化为复制，保证文件存在
                        Log.w(TAG, "hardlink 失败 ${entry.name} -> ${entry.linkName}，改为复制: ${it.message}")
                        runCatching { target.copyTo(outFile, overwrite = true) }
                    }
            }

            entry.isFile -> {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { tarIn.copyTo(it) }
                applyMode(outFile, entry.mode)
            }

            else -> Log.d(TAG, "忽略不支持的条目类型: ${entry.name}")
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

    /** 写入容器内 DNS，否则 apk/npm 等联网操作会因无法解析域名而失败 */
    private fun configureResolvConf() {
        val etc = File(rootfsDir, "etc").apply { mkdirs() }
        File(etc, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
    }
}
