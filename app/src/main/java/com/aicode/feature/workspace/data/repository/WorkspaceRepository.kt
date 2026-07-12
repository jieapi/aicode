package com.aicode.feature.workspace.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aicode.core.util.FileLogger
import com.aicode.feature.workspace.domain.model.Workspace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.workspaceDataStore by preferencesDataStore(name = "workspace_prefs")

/**
 * 管理 App 内的"工作区/项目"。
 *
 * 所有项目都放在内部私有目录 `filesDir/projects/<name>` 下：这是 ext4 真实路径，[java.io.File]
 * 工具与 PRoot 容器挂载都能直接使用，无需运行时存储权限，且支持 symlink（emulated 存储不支持）。
 * 当前选中的工作区名持久化在 DataStore 中，重启后保留。
 */
@Singleton
class WorkspaceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "WorkspaceRepository"
        const val DEFAULT_WORKSPACE = "default"
    }

    private val currentNameKey = stringPreferencesKey("current_workspace_name")

    /**
     * 所有项目的父目录，固定用内部 filesDir（app 私有 ext4）。
     *
     * 必须是 ext4：外部私有目录（getExternalFilesDir）落在 emulated/FUSE 存储，内核拒绝
     * symlink()，npm/pnpm/yarn/git 建软链时会 `EACCES symlink` 而失败。filesDir 是 ext4，
     * symlink 原生可用，所有工具链零配置即可跑。对外可见性由 DocumentsProvider 暴露，不依赖物理位置。
     */
    private val projectsRoot: File by lazy {
        File(context.filesDir, "projects").apply { mkdirs() }
    }

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _current = MutableStateFlow<Workspace?>(null)
    val current: StateFlow<Workspace?> = _current.asStateFlow()

    /** 扫描磁盘并恢复上次选中的工作区；首次启动会创建默认工作区。应在 App/ViewModel 启动时调用一次。 */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        refreshWorkspaces()

        if (_workspaces.value.isEmpty()) {
            createWorkspace(DEFAULT_WORKSPACE)
        }

        val savedName = context.workspaceDataStore.data.first()[currentNameKey]
        val target = _workspaces.value.firstOrNull { it.name == savedName }
            ?: _workspaces.value.firstOrNull()
        _current.value = target
        FileLogger.i(TAG, "工作区初始化完成，当前: ${target?.name}，根目录: ${projectsRoot.absolutePath}")
    }

    /** 重新读取项目目录列表。 */
    private fun refreshWorkspaces() {
        val dirs = projectsRoot.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
        _workspaces.value = dirs.map { Workspace(name = it.name, path = it.absolutePath) }
    }

    /** 切换当前工作区并持久化。 */
    suspend fun selectWorkspace(name: String) = withContext(Dispatchers.IO) {
        val target = _workspaces.value.firstOrNull { it.name == name } ?: return@withContext
        _current.value = target
        context.workspaceDataStore.edit { it[currentNameKey] = name }
        FileLogger.i(TAG, "切换工作区: $name")
    }

    /**
     * 新建工作区目录。名称会被清洗为安全的文件夹名。
     * @return 创建成功的 [Workspace]；名称非法或已存在返回 null。
     */
    suspend fun createWorkspace(rawName: String): Workspace? = withContext(Dispatchers.IO) {
        val name = sanitize(rawName)
        if (name.isEmpty()) {
            FileLogger.w(TAG, "新建工作区失败：名称非法 '$rawName'")
            return@withContext null
        }
        val dir = File(projectsRoot, name)
        if (dir.exists()) {
            FileLogger.w(TAG, "新建工作区失败：已存在 '$name'")
            return@withContext null
        }
        if (!dir.mkdirs()) {
            FileLogger.e(TAG, "新建工作区失败：无法创建目录 ${dir.absolutePath}")
            return@withContext null
        }
        refreshWorkspaces()
        val ws = Workspace(name = name, path = dir.absolutePath)
        FileLogger.i(TAG, "新建工作区: $name")
        ws
    }

    /** 删除工作区（连同其文件）。若删的是当前工作区，则自动切到剩余的第一个。 */
    suspend fun deleteWorkspace(name: String) = withContext(Dispatchers.IO) {
        val dir = File(projectsRoot, name)
        if (dir.exists()) dir.deleteRecursively()
        refreshWorkspaces()
        if (_current.value?.name == name) {
            val fallback = _workspaces.value.firstOrNull()
            _current.value = fallback
            context.workspaceDataStore.edit { prefs ->
                if (fallback != null) prefs[currentNameKey] = fallback.name else prefs.remove(currentNameKey)
            }
        }
        FileLogger.i(TAG, "删除工作区: $name")
    }

    /** 当前工作区的绝对路径，供 projectRoot / 命令执行目录使用；无选中时回退到项目根目录。 */
    fun currentPath(): String = _current.value?.path ?: projectsRoot.absolutePath

    /** 仅保留字母数字、下划线、连字符、点和空格，去掉路径分隔符等危险字符。 */
    private fun sanitize(raw: String): String =
        raw.trim()
            .replace(Regex("[^A-Za-z0-9 ._\\u4e00-\\u9fa5-]"), "")
            .trim()
            .take(64)
}
