package com.termux.devcenter.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.model.TermuxProject
import com.termux.devcenter.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectsViewModel : ViewModel() {
    private val repository = ProjectRepository()

    private val _projects = MutableStateFlow<List<TermuxProject>>(emptyList())
    val projects: StateFlow<List<TermuxProject>> = _projects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            
            repository.listProjects().fold(
                onSuccess = { projectList ->
                    _projects.value = projectList
                },
                onFailure = { error ->
                    // Handle error
                    _projects.value = emptyList()
                }
            )
            
            _isLoading.value = false
        }
    }
}
