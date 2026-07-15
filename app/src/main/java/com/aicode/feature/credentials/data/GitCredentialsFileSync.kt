package com.aicode.feature.credentials.data

import android.content.Context
import com.aicode.core.util.FileLogger
import com.aicode.feature.credentials.domain.model.GitCredential
import com.aicode.feature.credentials.domain.repository.CredentialRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 Room 凭据落盘到容器持久挂载目录，让 UI / 终端 / AI 三端 git 经 `credential.helper=store` 共用同一份凭据。
 *
 * 宿主目录 `filesDir/aicode` 即容器内 `/root/.aicode`（[LinuxContainerEngine] 的 `-b` 绑定，跨 rootfs 升级不丢）。
 * 容器 [buildContainerEnv] 已设 `GIT_CONFIG_GLOBAL=/root/.aicode/.gitconfig`，`.gitconfig` 里的
 * `[credential] helper = store --file=/root/.aicode/git-credentials` 由 [provisionIfNeeded] 装容器时
 * 跑一次 `git config --global` 写入（git 自身增量维护 ini）。
 *
 * 本类只负责**凭据文件** `git-credentials`（每 host 默认条一行），直接宿主侧写盘，不进容器、不跑 git 命令。
 * `.gitconfig` 不由本类写——它由 git 命令维护：`[credential]` 段在容器初始化时写、`[user]` 署名段由用户经
 * UI `git config --global user.name/email` 增量写。这样署名真源就是 `.gitconfig` 本身，UI 与命令行共用同一份，
 * 不存在两套写入路径竞争。
 *
 * 调用方：[AIEditorApp] 启动兜底、[CredentialViewModel] 增删改后、[GitViewModel] 弹窗保存后。
 */
@Singleton
class GitCredentialsFileSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val credentialRepository: CredentialRepository
) {
    private companion object {
        const val TAG = "GitCredentialsFileSync"
        /** 容器内 `.aicode` 挂载点对应的宿主目录（即文件实际落盘处）。 */
        const val AICODE_DIR_NAME = "aicode"
        const val CREDENTIALS_NAME = "git-credentials"
    }

    /** 目标宿主目录（容器内 /root/.aicode 的物理后端）。懒建。 */
    private val aicodeDir: File get() = File(context.filesDir, AICODE_DIR_NAME).apply { mkdirs() }

    /** 全量重建凭据文件。幂等、可重复调用。凭据为空时写空文件（不删文件，让 git 知无凭据）。 */
    suspend fun syncAll() {
        runCatching {
            val creds = credentialRepository.getAll().first()
            writeCredentials(creds)
        }.onFailure { FileLogger.e(TAG, "同步 git 凭据落盘失败", it) }
    }

    /** 写 `git-credentials`：每 host 默认条一行，按 git-credential-store 格式；空凭据写空文件。 */
    private fun writeCredentials(creds: List<GitCredential>) {
        // 每 host 取默认条（isDefault 优先），无默认回退该 host 首条——与 findForHost 同语义。
        val byHost: Map<String, GitCredential> = creds
            .filter { it.host.isNotBlank() && it.username.isNotBlank() }
            .sortedWith(compareByDescending<GitCredential> { it.isDefault }.thenBy { it.host })
            .associateBy { it.host.trim().lowercase() }

        val sb = StringBuilder()
        byHost.values.sortedBy { it.host }.forEach { c ->
            sb.append("https://")
                .append(enc(c.username)).append(':').append(enc(c.token))
                .append('@').append(c.host.trim().lowercase())
                .append('\n')
        }
        writeAtomically(File(aicodeDir, CREDENTIALS_NAME), sb.toString())
    }

    /** 先写 `.tmp` 再 rename，避免容器侧/git 读到半截文件。 */
    private fun writeAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            // 兜底：rename 失败（某些 FS 跨 inode）则直接写目标。
            target.writeText(content)
            tmp.delete()
        }
    }

    private fun enc(part: String): String = URLEncoder.encode(part, "UTF-8")
}
