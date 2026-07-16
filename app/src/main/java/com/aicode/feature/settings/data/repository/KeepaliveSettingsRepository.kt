
package com.aicode.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.keepaliveDataStore by preferencesDataStore(name = "keepalive_prefs")

/**
 * 持久化「后台保活常驻通知」开关。默认关闭——必须由用户在设置页手动开启并授予通知权限。
 *
 * 开启后由 [com.aicode.AIEditorApp] 监听本开关，启动
 * [com.aicode.feature.terminal.domain.TerminalKeepaliveService] 的常驻前台通知，
 * 使 App 进程在后台保持存活。DataStore 用法与 [LogSettingsRepository] 一致。
 */
@Singleton
class KeepaliveSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("keepalive_enabled")
    }

    /** 当前持久化的开关流；未设置时回退到 false（默认关闭，需手动开启）。 */
    val enabledFlow: Flow<Boolean> = context.keepaliveDataStore.data.map { it[ENABLED_KEY] ?: false }

    /** 写入开关。实际启停 Service 由 [com.aicode.AIEditorApp] 监听本流统一完成。 */
    suspend fun setEnabled(enabled: Boolean) {
        context.keepaliveDataStore.edit { it[ENABLED_KEY] = enabled }
    }

    /** 读取一次当前值（冷启动恢复用）。 */
    suspend fun isEnabled(): Boolean = enabledFlow.first()

    /** 备份快照：当前保活开关是否开启。 */
    suspend fun snapshot(): Boolean = enabledFlow.first()

    /** 从备份还原保活开关。 */
    suspend fun restore(enabled: Boolean) = setEnabled(enabled)
}
