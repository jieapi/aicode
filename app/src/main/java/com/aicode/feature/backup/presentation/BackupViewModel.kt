package com.aicode.feature.backup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.feature.backup.domain.BackupManager
import com.aicode.feature.backup.domain.BackupOptions
import com.aicode.feature.backup.domain.RestoreStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BackupState {
    data object Idle : BackupState()
    data object Working : BackupState()
    data class ExportSuccess(val bytes: ByteArray) : BackupState()
    data class ImportSuccess(val stats: RestoreStats) : BackupState()
    data class Error(val message: String) : BackupState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun export(password: String, options: BackupOptions) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            val pw = password.toCharArray().takeIf { it.isNotEmpty() }
            runCatching { backupManager.export(pw, options) }
                .onSuccess { _state.value = BackupState.ExportSuccess(it) }
                .onFailure { _state.value = BackupState.Error(it.message ?: "导出失败") }
        }
    }

    fun import(data: ByteArray, password: String) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            val pw = password.toCharArray().takeIf { it.isNotEmpty() }
            backupManager.import(data, pw)
                .onSuccess { _state.value = BackupState.ImportSuccess(it) }
                .onFailure {
                    val msg = when (it) {
                        is javax.crypto.AEADBadTagException -> "口令错误或备份文件已损坏"
                        else -> it.message ?: "导入失败"
                    }
                    _state.value = BackupState.Error(msg)
                }
        }
    }

    fun reset() {
        _state.value = BackupState.Idle
    }
}
