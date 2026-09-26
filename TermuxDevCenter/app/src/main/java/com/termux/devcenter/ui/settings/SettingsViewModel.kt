package com.termux.devcenter.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel : ViewModel() {
    private val _settings = MutableStateFlow(mapOf<String, String>())
    val settings: StateFlow<Map<String, String>> = _settings.asStateFlow()

    fun updateSetting(key: String, value: String) {
        _settings.value = _settings.value + (key to value)
    }
}
