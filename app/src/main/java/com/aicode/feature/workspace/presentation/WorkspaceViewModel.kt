package com.aicode.feature.workspace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import com.aicode.feature.workspace.domain.model.Workspace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val repository: WorkspaceRepository
) : ViewModel() {

    val workspaces: StateFlow<List<Workspace>> = repository.workspaces
    val current: StateFlow<Workspace?> = repository.current

    init {
        viewModelScope.launch { repository.initialize() }
    }

    fun selectWorkspace(name: String) = viewModelScope.launch {
        repository.selectWorkspace(name)
    }

    fun createWorkspace(name: String, onResult: (Workspace?) -> Unit = {}) = viewModelScope.launch {
        val ws = repository.createWorkspace(name)
        if (ws != null) repository.selectWorkspace(ws.name)
        onResult(ws)
    }

    fun deleteWorkspace(name: String) = viewModelScope.launch {
        repository.deleteWorkspace(name)
    }
}
