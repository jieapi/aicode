package com.aicode.feature.agent.domain.permission

import android.content.Context
import com.aicode.core.util.FileLogger
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具授权规则的持久化。全局规则存 app 私有目录 (`filesDir/aicode/permissions.json`)，
 * 项目级规则存工作区目录 (`.aicode/permissions.json`)，方便团队通过 git 共享。
 *
 * 文件格式为紧凑的 `Tool(pattern)` 风格，示例：
 * ```json
 * {
 *   "permissions": {
 *     "allow": ["Bash(git pull)", "writeFile"],
 *     "deny": ["Bash(rm -rf /)"]
 *   }
 * }
 * ```
 *
 * 安全要点：全局规则存在 app 私有目录，AI 无法篡改；项目级规则存在工作区内，
 * 可被 AI 修改，但作为项目级声明式配置这是有意为之（可 git 追踪/回滚）。
 *
 * 并发模式参考 [McpConfigRepository]：Mutex 保护文件 IO + MutableStateFlow 缓存。
 */
@Singleton
class PermissionRulesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workspaceRepository: WorkspaceRepository
) {
    private companion object {
        const val TAG = "PermissionRules"
        const val PERMISSIONS_FILE = "permissions.json"
        const val AICODE_DIR = ".aicode"
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    }

    /** 全局权限文件：`filesDir/aicode/permissions.json`，与 mcp.json 同级。 */
    private val globalFile: File
        get() = File(File(context.filesDir, "aicode"), PERMISSIONS_FILE)

    /** 当前工作区的项目级权限文件：`workspacePath/.aicode/permissions.json`。 */
    private fun projectFileForPath(workspacePath: String): File =
        File(File(workspacePath, AICODE_DIR), PERMISSIONS_FILE)

    // ── 内存缓存与响应式流 ──────────────────────────────────────

    private val globalState = MutableStateFlow<List<PermissionRule>?>(null)
    private val projectStates = ConcurrentHashMap<String, MutableStateFlow<List<PermissionRule>?>>()
    private val mutex = Mutex()

    private fun getProjectState(workspacePath: String): MutableStateFlow<List<PermissionRule>?> =
        projectStates.getOrPut(workspacePath) { MutableStateFlow(null) }

    // ── 懒加载 ──────────────────────────────────────────────────

    private suspend fun ensureGlobalLoaded() {
        if (globalState.value != null) return
        mutex.withLock {
            if (globalState.value != null) return
            globalState.value = loadFromFile(globalFile)
        }
    }

    private suspend fun ensureProjectLoaded(workspacePath: String) {
        val state = getProjectState(workspacePath)
        if (state.value != null) return
        mutex.withLock {
            if (state.value != null) return
            state.value = loadFromFile(projectFileForPath(workspacePath))
        }
    }

    private suspend fun loadFromFile(file: File): List<PermissionRule> =
        withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext emptyList()
            runCatching {
                JSON.decodeFromString<PermissionFile>(file.readText()).toRuleList()
            }.getOrElse {
                FileLogger.w(TAG, "读取 ${file.path} 失败: ${it.message}")
                emptyList()
            }
        }

    private fun writeToFile(file: File, rules: List<PermissionRule>) {
        file.parentFile?.mkdirs()
        file.writeText(JSON.encodeToString(PermissionFile.serializer(), rules.toPermissionFile()))
    }

    // ── 公共 API ────────────────────────────────────────────────

    /** 当前选中的项目名；无选中时为 null（此时项目级规则不可用，仅全局生效）。 */
    fun currentProjectName(): String? = workspaceRepository.current.value?.name

    /** 全局规则流，供管理界面观察。 */
    val globalRulesFlow: Flow<List<PermissionRule>> = flow {
        ensureGlobalLoaded()
        emitAll(globalState.filterNotNull())
    }

    /** 一次性读取全部全局规则（备份用）。 */
    suspend fun getGlobalRulesOnce(): List<PermissionRule> {
        ensureGlobalLoaded()
        return globalState.value ?: emptyList()
    }

    /** 全量替换全局规则（备份导入用），原子写文件并更新缓存。 */
    suspend fun setGlobalRules(rules: List<PermissionRule>) {
        mutex.withLock {
            withContext(Dispatchers.IO) { writeToFile(globalFile, rules) }
            globalState.value = rules
        }
    }

    /** 指定项目的规则流，供管理界面观察。 */
    fun projectRulesFlow(projectName: String): Flow<List<PermissionRule>> {
        val workspacePath = workspaceRepository.currentPath()
        val state = getProjectState(workspacePath)
        return flow {
            ensureProjectLoaded(workspacePath)
            emitAll(state.filterNotNull())
        }
    }

    /**
     * 评估用：当前项目规则 + 全局规则合并（项目在前）。一次性读取快照。
     */
    suspend fun loadEffectiveForCurrentProject(): List<PermissionRule> {
        ensureGlobalLoaded()
        val global = globalState.value ?: emptyList()
        val workspacePath = workspaceRepository.currentPath()
        ensureProjectLoaded(workspacePath)
        val project = getProjectState(workspacePath).value ?: emptyList()
        return project + global
    }

    /** 按 scope 新增规则。PROJECT 写入当前项目；无当前项目则忽略并告警。 */
    suspend fun add(scope: PermissionScope, rule: PermissionRule) {
        when (scope) {
            PermissionScope.GLOBAL -> editGlobal { if (rule !in it) it.add(rule) }
            PermissionScope.PROJECT -> {
                val workspacePath = workspaceRepository.currentPath()
                editProject(workspacePath) { if (rule !in it) it.add(rule) }
            }
        }
        FileLogger.i(TAG, "记忆授权规则[$scope]: ${rule.toolName} ${rule.pattern}")
    }

    /** 删除全局规则。 */
    suspend fun removeGlobalRule(rule: PermissionRule) = editGlobal { it.remove(rule) }

    /** 删除指定项目的规则。 */
    suspend fun removeProjectRule(projectName: String, rule: PermissionRule) {
        val workspacePath = workspaceRepository.currentPath()
        editProject(workspacePath) { it.remove(rule) }
    }

    /** 把一条项目规则提升为全局：项目删、全局加。 */
    suspend fun promoteToGlobal(projectName: String, rule: PermissionRule) {
        val workspacePath = workspaceRepository.currentPath()
        editProject(workspacePath) { it.remove(rule) }
        editGlobal { if (rule !in it) it.add(rule) }
        FileLogger.i(TAG, "提升为全局: ${rule.toolName} ${rule.pattern}")
    }

    // ── 内部写入 ────────────────────────────────────────────────

    private suspend fun editGlobal(mutate: (MutableList<PermissionRule>) -> Unit) {
        ensureGlobalLoaded()
        mutex.withLock {
            val list = (globalState.value ?: emptyList()).toMutableList()
            mutate(list)
            withContext(Dispatchers.IO) { writeToFile(globalFile, list) }
            globalState.value = list
        }
    }

    private suspend fun editProject(workspacePath: String, mutate: (MutableList<PermissionRule>) -> Unit) {
        ensureProjectLoaded(workspacePath)
        mutex.withLock {
            val state = getProjectState(workspacePath)
            val list = (state.value ?: emptyList()).toMutableList()
            mutate(list)
            withContext(Dispatchers.IO) { writeToFile(projectFileForPath(workspacePath), list) }
            state.value = list
        }
    }
}
