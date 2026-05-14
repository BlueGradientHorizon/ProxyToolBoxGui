package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.*
import com.bghorizon.proxytoolboxgui.di.AppModule
import com.bghorizon.proxytoolboxgui.ui.screens.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.getString
import proxytoolboxgui.composeapp.generated.resources.*

class MainViewModel(val module: AppModule) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            screen = HomeScreenState(),
            isDynamicColorSupported = module.platform.isDynamicColorSupported,
            isQrScannerSupported = module.platform.isQrScannerSupported
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            module.settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            try {
                module.settingsRepository.loadSettings()
                // Set completed status if we have configs already
                if (module.subscriptionRepository.getWorkingConfigs().isNotEmpty()) {
                    updateAppStatus(AppStatus.COMPLETED)
                }
                discoverWorkers()
            } catch (e: Exception) {
                e.printStackTrace()
                updateAppStatus(AppStatus.ERROR)
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(screen = screen) }
    }

    fun updateAppStatus(status: AppStatus) {
        _uiState.update { it.copy(appStatus = status) }
    }

    fun discoverWorkers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val libraryPath = module.platform.getWorkerLibraryPath()
                val json = GoBridge.discoverWorkers(libraryPath)
                val workers = JsonConfig.json.decodeFromString<List<WorkerInfo>>(json)
                _uiState.update { it.copy(workers = workers) }

                val currentSettings = module.settingsRepository.settings.value
                val savedName = currentSettings.selectedWorkerName
                val savedPath = currentSettings.selectedWorker

                // Find matching worker by path first, then by name
                val matchedWorker = workers.find { it.path == savedPath }
                    ?: workers.find { it.name == savedName }
                    ?: if (workers.isNotEmpty()) workers[0] else null

                if (matchedWorker != null && (matchedWorker.path != savedPath || savedName.isBlank())) {
                    module.settingsRepository.updateSelectedWorker(
                        matchedWorker.name,
                        matchedWorker.path
                    )
                }

                if (workers.isEmpty()) {
                    updateAppStatus(AppStatus.ERROR)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateAppStatus(AppStatus.ERROR)
            }
        }
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
                    host = host,
                    getConfigUris = {
                        module.subscriptionRepository.getWorkingConfigs()
                            .joinToString("\n") { it.connURI }
                    }
                )

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
