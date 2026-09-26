package com.termux.devcenter.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.devcenter.data.model.FileItem
import com.termux.devcenter.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FilesViewModel : ViewModel() {
    private val repository = FileRepository()
    private val homeDir = "/data/data/com.termux/files/home"

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _currentPath = MutableStateFlow(homeDir)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            
            repository.listFiles(_currentPath.value).fold(
                onSuccess = { fileList ->
                    _files.value = fileList.sortedWith(
                        compareBy<FileItem> { !it.isDirectory }
                            .thenBy { it.name }
                    )
                },
                onFailure = { error ->
                    _files.value = emptyList()
                }
            )
            
            _isLoading.value = false
        }
    }

    fun navigateTo(path: String) {
        _currentPath.value = path
        loadFiles()
    }

    fun navigateUp() {
        val parent = _currentPath.value.substringBeforeLast("/")
        if (parent.isNotEmpty() && parent != _currentPath.value) {
            navigateTo(parent)
        }
    }
}
