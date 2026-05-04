package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.*
import com.bghorizon.proxytoolboxgui.platform.Platform
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(
    private val platform: Platform,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Main)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _stats = MutableStateFlow(ConfigStats())
    val stats: StateFlow<ConfigStats> = _stats.asStateFlow()

    private val _testProgress = MutableStateFlow(TestProgress())
    val testProgress: StateFlow<TestProgress> = _testProgress.asStateFlow()

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _workers = MutableStateFlow<List<WorkerInfo>>(emptyList())
    val workers: StateFlow<List<WorkerInfo>> = _workers.asStateFlow()

    private val _appStatus = MutableStateFlow(AppStatus.IDLE)
    val appStatus: StateFlow<AppStatus> = _appStatus.asStateFlow()

    private val _webServerRunning = MutableStateFlow(false)
    val webServerRunning: StateFlow<Boolean> = _webServerRunning.asStateFlow()

    private val _workingConfigs = MutableStateFlow<List<ProxyConfig>>(emptyList())
    val workingConfigs: StateFlow<List<ProxyConfig>> = _workingConfigs.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _showAddSubscription = MutableStateFlow(false)
    val showAddSubscription: StateFlow<Boolean> = _showAddSubscription.asStateFlow()

    private val _editingSubscription = MutableStateFlow<Subscription?>(null)
    val editingSubscription: StateFlow<Subscription?> = _editingSubscription.asStateFlow()

    private val _showDeleteConfirmation = MutableStateFlow<Subscription?>(null)
    val showDeleteConfirmation: StateFlow<Subscription?> = _showDeleteConfirmation.asStateFlow()

    private val _showThemeDialog = MutableStateFlow(false)
    val showThemeDialog: StateFlow<Boolean> = _showThemeDialog.asStateFlow()

    private val _showWorkerDialog = MutableStateFlow(false)
    val showWorkerDialog: StateFlow<Boolean> = _showWorkerDialog.asStateFlow()

    private var testJob: Job? = null
    private var downloadJob: Job? = null
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
        viewModelScope.launch {
            settingsRepository.loadSettings()
            discoverWorkers()
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun navigateBack() {
        _currentScreen.value = Screen.Main
    }

    fun discoverWorkers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val libraryPath = platform.getWorkerLibraryPath()
                // Call Go wrapper: wrapper.DiscoverWorkers(libraryPath)
                // Parse JSON into List<WorkerInfo>
                _workers.value = emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectWorker(path: String) {
        viewModelScope.launch {
            settingsRepository.updateSelectedWorker(path)
        }
    }

    fun startTest() {
        if (_workers.value.isEmpty()) return
        if (testJob?.isActive == true) return

        testJob = viewModelScope.launch(Dispatchers.IO) {
            _appStatus.value = AppStatus.TESTING
            _testProgress.value = TestProgress(isRunning = true)

            try {
                // 1. Download subscriptions
                // 2. Parse configs via Go wrapper
                // 3. Validate via Go wrapper
                // 4. Run latency tests via Go wrapper with callbacks

                val totalBatches = 4
                val totalRounds = _settings.value.latencyRounds

                for (batch in 1..totalBatches) {
                    for (round in 1..totalRounds) {
                        _testProgress.value = _testProgress.value.copy(
                            currentBatch = batch,
                            totalBatches = totalBatches,
                            currentRound = round,
                            totalRounds = totalRounds,
                            phase = 2
                        )
                        delay(1000)
                    }
                }

                _appStatus.value = AppStatus.COMPLETED
                _stats.value = _stats.value.copy(
                    working = _stats.value.found - _stats.value.parseErr - _stats.value.validErr
                )
            } catch (e: Exception) {
                _appStatus.value = AppStatus.ERROR
                e.printStackTrace()
            } finally {
                _testProgress.value = _testProgress.value.copy(isRunning = false)
                if (_settings.value.autoStartWebServer) {
                    startWebServer()
                }
            }
        }
    }

    fun stopTest() {
        testJob?.cancel()
        _appStatus.value = AppStatus.IDLE
        _testProgress.value = TestProgress()
    }

    fun updateSubscriptions() {
        if (downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _appStatus.value = AppStatus.DOWNLOADING
            val subs = _subscriptions.value
            _downloadProgress.value = DownloadProgress(
                total = subs.size,
                isRunning = true
            )

            var succeeded = 0
            var failed = 0

            for (sub in subs) {
                try {
                    // Download subscription content
                    // Parse and update stats
                    succeeded++
                } catch (e: Exception) {
                    failed++
                }
                _downloadProgress.value = _downloadProgress.value.copy(
                    succeeded = succeeded,
                    failed = failed
                )
            }

            _downloadProgress.value = _downloadProgress.value.copy(isRunning = false)
            _appStatus.value = AppStatus.IDLE
        }
    }

    fun copyWorkingConfigs() {
        val uris = _workingConfigs.value.joinToString("\n") { it.connURI }
        platform.copyToClipboard(uris)
        platform.showToast("Copied to clipboard")
    }

    fun exportWorkingConfigs() {
        val uris = _workingConfigs.value.joinToString("\n") { it.connURI }
        platform.exportToFile(uris, "working_configs.txt")
    }

    fun toggleWebServer() {
        if (_webServerRunning.value) {
            stopWebServer()
        } else {
            startWebServer()
        }
    }

    private fun startWebServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stopWebServer()

                val port = _settings.value.webServerPort
                val localhostOnly = _settings.value.webServerLocalhost
                val host = if (localhostOnly) "127.0.0.1" else "0.0.0.0"

                server = embeddedServer(CIO, port = port, host = host) {
                    routing {
                        get("/") {
                            val uris = _workingConfigs.value.joinToString("\n") { it.connURI }
                            call.respondText(uris, contentType = io.ktor.http.ContentType.Text.Plain)
                        }
                    }
                }.start(wait = false)

                _webServerRunning.value = true
                platform.showToast("Web server started on port $port")
            } catch (e: Exception) {
                e.printStackTrace()
                platform.showToast("Failed to start web server: ${e.message}")
            }
        }
    }

    private fun stopWebServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                server?.stop(1000, 2000)
                server = null
                _webServerRunning.value = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun showAddSubscription() {
        _editingSubscription.value = null
        _showAddSubscription.value = true
    }

    fun showEditSubscription(sub: Subscription) {
        _editingSubscription.value = sub
        _showAddSubscription.value = true
    }

    fun hideAddSubscription() {
        _showAddSubscription.value = false
        _editingSubscription.value = null
    }

    fun saveSubscription(note: String, url: String) {
        val existing = _editingSubscription.value
        val newSub = if (existing != null) {
            existing.copy(note = note, url = url)
        } else {
            Subscription(
                id = System.currentTimeMillis().toString(),
                note = note,
                url = url
            )
        }

        _subscriptions.value = if (existing != null) {
            _subscriptions.value.map { if (it.id == existing.id) newSub else it }
        } else {
            _subscriptions.value + newSub
        }
        hideAddSubscription()
    }

    fun showDeleteSubscription(sub: Subscription) {
        _showDeleteConfirmation.value = sub
    }

    fun hideDeleteConfirmation() {
        _showDeleteConfirmation.value = null
    }

    fun confirmDeleteSubscription() {
        _showDeleteConfirmation.value?.let { sub ->
            _subscriptions.value = _subscriptions.value.filter { it.id != sub.id }
        }
        hideDeleteConfirmation()
    }

    fun showThemeDialog() { _showThemeDialog.value = true }
    fun hideThemeDialog() { _showThemeDialog.value = false }

    fun showWorkerDialog() { _showWorkerDialog.value = true }
    fun hideWorkerDialog() { _showWorkerDialog.value = false }

    fun updateTheme(theme: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.updateTheme(theme)
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.saveSettings(newSettings)
        }
    }

    fun onRoundStarted(batchNum: Int, roundNum: Int, total: Int) {
        _testProgress.value = _testProgress.value.copy(
            currentBatch = batchNum,
            currentRound = roundNum,
            elapsedSeconds = 0,
            totalSeconds = total * _settings.value.roundTimeout
        )
    }

    fun onProgress(tag: String, delay: Long, failed: Boolean) {
        val current = _testProgress.value
        val updatedProgresses = current.batchProgresses.toMutableList()
        val batchIndex = updatedProgresses.indexOfFirst { it.batchNum == current.currentBatch }

        if (batchIndex >= 0) {
            val bp = updatedProgresses[batchIndex]
            updatedProgresses[batchIndex] = bp.copy(
                running = bp.running - 1,
                failed = if (failed) bp.failed + 1 else bp.failed,
                succeeded = if (!failed) bp.succeeded + 1 else bp.succeeded
            )
        }

        _testProgress.value = current.copy(
            batchProgresses = updatedProgresses,
            elapsedSeconds = current.elapsedSeconds + 1
        )
    }

    fun onRoundEnded(batchNum: Int, roundNum: Int) {}

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
        downloadJob?.cancel()
        stopWebServer()
    }
}

enum class Screen {
    Main, Subscriptions, Settings
}