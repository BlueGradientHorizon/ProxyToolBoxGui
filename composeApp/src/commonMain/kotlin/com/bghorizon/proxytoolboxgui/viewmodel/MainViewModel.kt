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
                module.settingsRepository.loadSettings(module.platform)
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

    fun updateAppStatus(status: AppStatus, description: String? = null) {
        _uiState.update { it.copy(appStatus = status, statusDescription = description) }
    }

    fun discoverWorkers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val libraryPath = module.platform.getWorkerLibraryPath()
                val json = GoBridge.discoverWorkers(libraryPath)
                val workers = JsonConfig.json.decodeFromString<List<WorkerInfo>>(json)

                _uiState.update { state ->
                    val hasWorkers = workers.isNotEmpty()
                    
                    // If we were in an error state because of missing workers, and now we have them, reset to IDLE.
                    // If we still have no workers, stay/enter ERROR state.
                    val newStatus = if (!hasWorkers) {
                        AppStatus.ERROR
                    } else if (state.appStatus == AppStatus.ERROR) {
                        AppStatus.IDLE
                    } else {
                        state.appStatus
                    }

                    state.copy(
                        workers = workers,
                        appStatus = newStatus,
                        statusDescription = if (!hasWorkers) getString(Res.string.no_workers_found) else null
                    )
                }

                val currentSettings = module.settingsRepository.settings.value
                val savedName = currentSettings.selectedWorkerName
                val savedPath = currentSettings.selectedWorker

                val workersList = _uiState.value.workers
                // Find matching worker by path first, then by name
                val matchedWorker = workersList.find { it.path == savedPath }
                    ?: workersList.find { it.name == savedName }
                    ?: if (workersList.isNotEmpty()) workersList[0] else null

                if (matchedWorker != null && (matchedWorker.path != savedPath || savedName.isBlank())) {
                    module.settingsRepository.updateSelectedWorker(
                        matchedWorker.name,
                        matchedWorker.path
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateAppStatus(AppStatus.ERROR, e.message)
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
            host = host
        ) {
            module.subscriptionRepository.getWorkingConfigs()
                .joinToString("\n") { it.connURI }
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
