package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.AppSettings
import com.bghorizon.proxytoolboxgui.data.ThemeMode
import com.bghorizon.proxytoolboxgui.di.AppModule
import com.bghorizon.proxytoolboxgui.ui.screens.SettingsUiMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(private val module: AppModule) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = module.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun updateMode(mode: SettingsUiMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun updateTheme(theme: ThemeMode) {
        viewModelScope.launch {
            module.settingsRepository.updateTheme(theme)
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            module.settingsRepository.saveSettings(newSettings)
        }
    }

    fun selectWorker(name: String, path: String) {
        viewModelScope.launch {
            module.settingsRepository.updateSelectedWorker(name, path)
        }
    }

    fun savePort(port: Int): Boolean {
        if (port !in 1024..65535) return false
        viewModelScope.launch {
            val current = settings.value
            module.settingsRepository.saveSettings(current.copy(webServerPort = port))
        }
        return true
    }
}

data class SettingsUiState(
    val mode: SettingsUiMode = SettingsUiMode.Normal
)
