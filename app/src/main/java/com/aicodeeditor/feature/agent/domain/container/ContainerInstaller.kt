package com.aicodeeditor.feature.agent.domain.container

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContainerInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Directory where the rootfs will be extracted
     */
    val rootfsDir: File
        get() = File(context.filesDir, "rootfs")

    /**
     * Checks if the rootfs is installed and ready to use
     */
    fun isInstalled(): Boolean {
        // In a real implementation, we'd check for specific files like bin/sh or proot
        val prootBin = File(rootfsDir, "usr/bin/proot")
        return rootfsDir.exists() && rootfsDir.isDirectory // && prootBin.exists()
    }

    /**
     * Installs the rootfs if it's not already installed.
     * In this POC phase, we might just create the directory or do a dummy setup.
     */
    suspend fun installRootfsIfNeed() = withContext(Dispatchers.IO) {
        if (!isInstalled()) {
            rootfsDir.mkdirs()

            // TODO: In Phase 2, extract alpine-minirootfs-aarch64.tar.gz from assets
            // Example flow:
            // 1. Open asset stream
            // 2. Untar to rootfsDir
            // 3. Make binaries executable (chmod +x)

            // For POC, we'll create a dummy marker
            File(rootfsDir, ".installed").createNewFile()
        }
    }
}