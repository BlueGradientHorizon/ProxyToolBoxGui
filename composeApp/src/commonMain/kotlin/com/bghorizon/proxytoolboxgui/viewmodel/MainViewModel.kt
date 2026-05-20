package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.ProxyConfig
import com.bghorizon.proxytoolboxgui.di.AppModule
import com.bghorizon.proxytoolboxgui.ui.screens.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeSource
import kotlin.time.TimeMark
import org.jetbrains.compose.resources.getString
import proxytoolboxgui.composeapp.generated.resources.*

class MainViewModel(val module: AppModule) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            screen = HomeScreenState(),
            settings = module.settingsRepository.settings.value,
            isDynamicColorSupported = module.platform.isDynamicColorSupported,
            isQrScannerSupported = module.platform.isQrScannerSupported,
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    var navigationStartTimeMark: TimeMark? = null
        private set

    init {
        viewModelScope.launch {
            module.appStatusManager.statusInfo.collect { info ->
                _uiState.update {
                    it.copy(
                        appStatus = info.status,
                        statusDescription = info.description
                    )
                }
            }
        }
        viewModelScope.launch {
            module.settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            module.workerRepository.workers.collect { workers ->
                _uiState.update { it.copy(workers = workers) }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        navigationStartTimeMark = TimeSource.Monotonic.markNow()
        _uiState.update { it.copy(screen = screen) }
    }

    fun clearNavigationStartTimeMark() {
        navigationStartTimeMark = null
    }

    fun toggleWebServer() {
        if (_uiState.value.webServerRunning) {
            stopWebServer()
        } else {
            startWebServer()
        }
    }

    fun startWebServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = module.settingsRepository.settings.value
                val port = settings.webServerPort
                val host = if (settings.webServerLocalhost) "127.0.0.1" else "0.0.0.0"

                module.webServer.start(
                    port = port,
                    host = host
                ) {
                    val currentSettings = module.settingsRepository.settings.value
                    var configs = module.subscriptionRepository.getWorkingConfigs()
                    
                    if (currentSettings.sortProfilesByDelay) {
                        configs = configs.sortedWith(compareBy<ProxyConfig> { it.delay }.thenBy { it.tag })
                    }
                    
                    configs.joinToString("\n") { it.connURI }
                }

                _uiState.update { it.copy(webServerRunning = true) }
                val msg = getString(Res.string.web_server_started, port)
                module.platform.showToast(msg)
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = getString(Res.string.web_server_failed, e.message ?: "")
                module.platform.showToast(msg)
            }
        }
    }

    fun stopWebServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                module.webServer.stop()
                _uiState.update { it.copy(webServerRunning = false) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateDialog(dialog: UiDialog?) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun hideDialog() {
        updateDialog(null)
    }

    override fun onCleared() {
        super.onCleared()
        stopWebServer()
    }
}
