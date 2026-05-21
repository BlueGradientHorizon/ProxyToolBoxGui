package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.AppLanguage
import com.bghorizon.proxytoolboxgui.data.AppSettings
import com.bghorizon.proxytoolboxgui.data.ThemeMode
import com.bghorizon.proxytoolboxgui.di.AppModule
import com.bghorizon.proxytoolboxgui.ui.screens.SettingsScreenUiMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import proxytoolboxgui.composeapp.generated.resources.*

class SettingsScreenViewModel(private val module: AppModule) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsScreenUiState())
    val uiState = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = module.settingsRepository.settings

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

    fun getThemeLabel(theme: ThemeMode): StringResource = when (theme) {
        ThemeMode.LIGHT -> Res.string.app_theme_light
        ThemeMode.DARK -> Res.string.app_theme_dark
        ThemeMode.SYSTEM -> Res.string.app_theme_system
    }

    fun getLanguageNativeName(lang: AppLanguage): StringResource? = when (lang) {
        AppLanguage.ENGLISH -> Res.string.app_language_english_native
        AppLanguage.RUSSIAN -> Res.string.app_language_russian_native
        AppLanguage.SYSTEM -> null
    }

    fun getSpeedTestModeLabel(mode: String): StringResource = when (mode) {
        "download" -> Res.string.speed_test_mode_download
        else -> Res.string.speed_test_mode_upload
    }
}

data class SettingsScreenUiState(
    val mode: SettingsScreenUiMode = SettingsScreenUiMode.Normal,
)
