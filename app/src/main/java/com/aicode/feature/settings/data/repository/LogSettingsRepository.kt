package com.aicode.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aicode.core.util.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.logDataStore by preferencesDataStore(name = "log_prefs")

/**
 * 持久化「日志最低记录等级」。等级以枚举名（字符串）存取，默认 [LogLevel.VERBOSE]（开发期全量）。
 *
 * 取值由 `AIEditorApp` 在启动与设置变更时 collect [levelFlow] 同步到 [com.aicode.core.util.FileLogger]。
 * DataStore 用法与 `WorkspaceRepository` 一致（构造注入即可，无需 DI module）。
 */
@Singleton
class LogSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val LEVEL_KEY = stringPreferencesKey("log_min_level")
        val DEFAULT_LEVEL = LogLevel.VERBOSE
    }

    /** 当前持久化的日志等级流；解析失败/未设置时回退到默认值。 */
    val levelFlow: Flow<LogLevel> = context.logDataStore.data.map { prefs ->
        prefs[LEVEL_KEY]?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() } ?: DEFAULT_LEVEL
    }

    /** 写入新的日志等级。 */
    suspend fun setLevel(level: LogLevel) {
        context.logDataStore.edit { it[LEVEL_KEY] = level.name }
    }

    /** 备份快照：当前日志等级名。 */
    suspend fun snapshot(): String = levelFlow.first().name

    /** 从备份还原日志等级；解析失败时忽略。 */
    suspend fun restore(value: String?) {
        val level = value?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() } ?: return
        setLevel(level)
    }
}
