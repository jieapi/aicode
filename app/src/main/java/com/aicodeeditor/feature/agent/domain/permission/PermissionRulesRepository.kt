package com.aicodeeditor.feature.agent.domain.permission

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aicodeeditor.core.util.FileLogger
import com.aicodeeditor.feature.workspace.data.repository.WorkspaceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// 两个独立的 Preferences DataStore：全局规则一份，项目规则按项目名分桶各一份。
private val Context.permissionGlobalDataStore by preferencesDataStore(name = "permission_global_prefs")
private val Context.permissionProjectDataStore by preferencesDataStore(name = "permission_project_prefs")

/**
 * 工具授权规则的持久化。仿 [com.aicodeeditor.feature.settings.data.repository.LogSettingsRepository]
 * 用 Preferences DataStore（构造注入即可，无需 DI module）。
 *
 * 安全要点：规则一律存 app 私有 DataStore，**绝不**写进 `/workspace`——否则容器内的 AI 能读写该文件
 * 给自己授权，授权层形同虚设。项目身份用工作区名（[WorkspaceRepository.current]）作 key。
 */
@Singleton
class PermissionRulesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workspaceRepository: WorkspaceRepository
) {
    private companion object {
        const val TAG = "PermissionRules"
        val GLOBAL_KEY = stringPreferencesKey("rules")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(raw: String?): List<PermissionRule> =
        raw?.let { runCatching { json.decodeFromString<List<PermissionRule>>(it) }.getOrNull() } ?: emptyList()

    /** 当前选中的项目名；无选中时为 null（此时项目级规则不可用，仅全局生效）。 */
    fun currentProjectName(): String? = workspaceRepository.current.value?.name

    /** 全局规则流，供管理界面观察。 */
    val globalRulesFlow: Flow<List<PermissionRule>> =
        context.permissionGlobalDataStore.data.map { decode(it[GLOBAL_KEY]) }

    /** 指定项目的规则流，供管理界面观察。 */
    fun projectRulesFlow(projectName: String): Flow<List<PermissionRule>> =
        context.permissionProjectDataStore.data.map { decode(it[stringPreferencesKey(projectName)]) }

    /**
     * 评估用：当前项目规则 + 全局规则合并（项目在前）。一次性读取快照。
     */
    suspend fun loadEffectiveForCurrentProject(): List<PermissionRule> {
        val global = decode(context.permissionGlobalDataStore.data.first()[GLOBAL_KEY])
        val projectName = currentProjectName()
        val project = if (projectName != null) {
            decode(context.permissionProjectDataStore.data.first()[stringPreferencesKey(projectName)])
        } else emptyList()
        return project + global
    }

    /** 按 scope 新增规则。PROJECT 写入当前项目；无当前项目则忽略并告警。 */
    suspend fun add(scope: PermissionScope, rule: PermissionRule) {
        when (scope) {
            PermissionScope.GLOBAL -> editGlobal { if (rule !in it) it.add(rule) }
            PermissionScope.PROJECT -> {
                val projectName = currentProjectName()
                if (projectName == null) {
                    FileLogger.w(TAG, "无当前项目，跳过项目级规则写入: $rule")
                    return
                }
                editProject(projectName) { if (rule !in it) it.add(rule) }
            }
        }
        FileLogger.i(TAG, "记忆授权规则[$scope]: ${rule.toolName} ${rule.pattern}")
    }

    /** 删除全局规则。 */
    suspend fun removeGlobalRule(rule: PermissionRule) = editGlobal { it.remove(rule) }

    /** 删除指定项目的规则。 */
    suspend fun removeProjectRule(projectName: String, rule: PermissionRule) =
        editProject(projectName) { it.remove(rule) }

    /** 把一条项目规则提升为全局：项目删、全局加。 */
    suspend fun promoteToGlobal(projectName: String, rule: PermissionRule) {
        editProject(projectName) { it.remove(rule) }
        editGlobal { if (rule !in it) it.add(rule) }
        FileLogger.i(TAG, "提升为全局: ${rule.toolName} ${rule.pattern}")
    }

    private suspend fun editGlobal(mutate: (MutableList<PermissionRule>) -> Unit) {
        context.permissionGlobalDataStore.edit { prefs ->
            val list = decode(prefs[GLOBAL_KEY]).toMutableList()
            mutate(list)
            prefs[GLOBAL_KEY] = json.encodeToString(list)
        }
    }

    private suspend fun editProject(projectName: String, mutate: (MutableList<PermissionRule>) -> Unit) {
        val key = stringPreferencesKey(projectName)
        context.permissionProjectDataStore.edit { prefs ->
            val list = decode(prefs[key]).toMutableList()
            mutate(list)
            prefs[key] = json.encodeToString(list)
        }
    }
}
