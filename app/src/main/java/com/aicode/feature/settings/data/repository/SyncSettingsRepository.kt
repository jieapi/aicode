package com.aicode.feature.settings.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    private val _ignoredPatterns = MutableStateFlow(
        prefs.getString("ignored_patterns", ".git,node_modules,build,dist,.gradle,.idea,.cxx,.vscode,tmp")!!
    )
    val ignoredPatterns: StateFlow<String> = _ignoredPatterns.asStateFlow()

    private val _useGitIgnore = MutableStateFlow(
        prefs.getBoolean("use_gitignore", true)
    )
    val useGitIgnore: StateFlow<Boolean> = _useGitIgnore.asStateFlow()

    private val _maxSyncBatchSize = MutableStateFlow(
        prefs.getInt("max_sync_batch_size", 5)
    )
    val maxSyncBatchSize: StateFlow<Int> = _maxSyncBatchSize.asStateFlow()

    fun setIgnoredPatterns(patterns: String) {
        prefs.edit { putString("ignored_patterns", patterns) }
        _ignoredPatterns.value = patterns
    }

    fun setUseGitIgnore(use: Boolean) {
        prefs.edit { putBoolean("use_gitignore", use) }
        _useGitIgnore.value = use
    }

    fun setMaxSyncBatchSize(size: Int) {
        prefs.edit { putInt("max_sync_batch_size", size) }
        _maxSyncBatchSize.value = size
    }
}
