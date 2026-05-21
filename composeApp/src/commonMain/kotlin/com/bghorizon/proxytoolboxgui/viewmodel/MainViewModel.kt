package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.*
import com.bghorizon.proxytoolboxgui.di.AppModule
import com.bghorizon.proxytoolboxgui.ui.screens.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeSource
import kotlin.time.TimeMark
import org.jetbrains.compose.resources.getString
import proxytoolboxgui.composeapp.generated.resources.*
import kotlin.time.Clock

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
            module.settingsRepository.loadSettings(module.platform)
            module.runtimeSettingsManager.discoverWorkers()
            module.runtimeSettingsManager.discoverSpeedtestPresets()
            _uiState.update { it.copy(isInitialized = true) }
        }
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
            module.runtimeSettingsManager.workers.collect { workers ->
                _uiState.update { it.copy(workers = workers) }
            }
        }
        viewModelScope.launch {
            module.runtimeSettingsManager.speedTestPresets.collect { presets ->
                _uiState.update { it.copy(speedTestPresets = presets) }
            }
        }
        viewModelScope.launch {
            module.webServerManager.isRunning.collect { running ->
                _uiState.update { it.copy(webServerRunning = running) }
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
        viewModelScope.launch {
            if (_uiState.value.webServerRunning) {
                stopWebServer()
            } else {
                startWebServer()
            }
        }
    }

    suspend fun getWorkingConfigsString(): String {
        val settings = module.settingsRepository.settings.value
        var configs = module.subscriptionRepository.getWorkingConfigs()

        if (settings.performSpeedTests) {
            configs = configs.filter { it.speed > 0 }
        }

        if (settings.sortProfilesByDelay) {
            configs = if (settings.performSpeedTests && !settings.sortByLatencyDelay) {
                configs.sortedWith(
                    compareByDescending<ProxyConfig> { it.speed }
                        .thenBy { it.delay }
                        .thenBy { it.tag }
                )
            } else {
                configs.sortedWith(
                    compareBy<ProxyConfig> { it.delay }
                        .thenBy { it.tag }
                )
            }
        }

        return configs.joinToString("\n") { it.connURI }
    }

    fun startWebServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configs = getWorkingConfigsString()
                module.webServerManager.start(configs)
                val port = module.settingsRepository.settings.value.webServerPort
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
                module.webServerManager.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun copyWorkingConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            val uris = getWorkingConfigsString()
            val msg = getString(Res.string.msg_copied_to_clipboard)
            val label = getString(Res.string.label_proxy_configs)
            withContext(Dispatchers.Main) {
                module.platform.copyToClipboard(uris, label)
                module.platform.showToast(msg)
            }
        }
    }

    fun exportWorkingConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            val uris = getWorkingConfigsString()
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

            val day = now.day.toString().padStart(2, '0')
            val month = now.month.ordinal.toString().padStart(2, '0')
            val dmy = "$day$month${now.year}"

            val hour = now.hour.toString().padStart(2, '0')
            val minute = now.minute.toString().padStart(2, '0')
            val second = now.second.toString().padStart(2, '0')
            val hms = "$hour$minute$second"
            val filename = "ProxyToolBoxGui_export_${dmy}_${hms}.txt"

            val path = module.platform.exportToFile(uris, filename)
            val msg = if (path != null) {
                getString(Res.string.msg_exported_to, path)
            } else {
                getString(Res.string.msg_export_failed)
            }
            withContext(Dispatchers.Main) {
                module.platform.showToast(msg)
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
