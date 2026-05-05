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

    private val _showDownloadTimeoutDialog = MutableStateFlow(false)
    val showDownloadTimeoutDialog: StateFlow<Boolean> = _showDownloadTimeoutDialog.asStateFlow()

    private val _showLatencyRoundsDialog = MutableStateFlow(false)
    val showLatencyRoundsDialog: StateFlow<Boolean> = _showLatencyRoundsDialog.asStateFlow()

    private val _showRoundTimeoutDialog = MutableStateFlow(false)
    val showRoundTimeoutDialog: StateFlow<Boolean> = _showRoundTimeoutDialog.asStateFlow()

    private val _showBatchSizeDialog = MutableStateFlow(false)
    val showBatchSizeDialog: StateFlow<Boolean> = _showBatchSizeDialog.asStateFlow()

    private val _showPortDialog = MutableStateFlow(false)
    val showPortDialog: StateFlow<Boolean> = _showPortDialog.asStateFlow()

    private var testJob: Job? = null
    private var downloadJob: Job? = null
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
        viewModelScope.launch {
            settingsRepository.loadSettings()
            _subscriptions.value = loadSubscriptions()
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
                val json = GoBridge.discoverWorkers(libraryPath)
                _workers.value = JsonConfig.json.decodeFromString<List<WorkerInfo>>(json)
                if (_workers.value.isEmpty()) {
                    _appStatus.value = AppStatus.ERROR
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _appStatus.value = AppStatus.ERROR
            }
        }
    }

    fun selectWorker(path: String) {
        viewModelScope.launch {
            settingsRepository.updateSelectedWorker(path)
        }
    }

    fun startTest() {
        if (_workers.value.isEmpty() || testJob?.isActive == true) return

        testJob = viewModelScope.launch(Dispatchers.IO) {
            _appStatus.value = AppStatus.TESTING
            _testProgress.value = TestProgress(isRunning = true)

            try {
                _appStatus.value = AppStatus.PARSING
                val storedUris = loadSubscriptionUris()

                val (parsedJson, dupCount, parseErrs) = GoBridge.parseConfigs(storedUris, _settings.value.performDedup)
                val parsedConfigs = JsonConfig.json.decodeFromString<List<ProxyConfig>>(parsedJson)
                _stats.value = ConfigStats(
                    found = parsedConfigs.size,
                    duplicated = dupCount,
                    parseErr = parseErrs
                )

                _appStatus.value = AppStatus.VALIDATING
                val workerPath = _settings.value.selectedWorker
                val (validatedJson, validErrs) = GoBridge.validateConfigs(workerPath, parsedJson)
                val validatedConfigs = JsonConfig.json.decodeFromString<List<ProxyConfig>>(validatedJson)
                _stats.value = _stats.value.copy(validErr = validErrs)

                _appStatus.value = AppStatus.TESTING
                val resultJson = GoBridge.runLatencyTests(
                    workerPath = workerPath,
                    configsJson = validatedJson,
                    settings = _settings.value,
                    callback = object : GoTestCallback {
                        override fun onRoundStarted(batch: Long, round: Long, total: Long) {
                            _testProgress.value = _testProgress.value.copy(
                                currentBatch = batch.toInt(),
                                currentRound = round.toInt(),
                                totalSeconds = total.toInt() * _settings.value.roundTimeout,
                                elapsedSeconds = 0,
                                isRunning = true,
                                isRoundActive = true
                            )
                        }

                        override fun onProgress(tag: String, delay: Long, failed: Boolean) {
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

                        override fun onRoundEnded(batch: Long, round: Long) {
                            _testProgress.value = _testProgress.value.copy(isRoundActive = false)
                        }
                    }
                )

                val working = JsonConfig.json.decodeFromString<List<ProxyConfig>>(resultJson)
                _workingConfigs.value = working
                _stats.value = _stats.value.copy(working = working.size)
                _appStatus.value = AppStatus.COMPLETED
            } catch (e: Exception) {
                _appStatus.value = AppStatus.ERROR
                e.printStackTrace()
            } finally {
                _testProgress.value = _testProgress.value.copy(isRunning = false, isRoundActive = false)
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
            val downloadedUris = mutableListOf<String>()

            for (sub in subs) {
                try {
                    val content = SubscriptionDownloader.download(sub.url, _settings.value.downloadTimeout)
                    val lines = content.lines().filter { it.isNotBlank() }
                    downloadedUris.addAll(lines)
                    succeeded++
                } catch (e: Exception) {
                    failed++
                }
                _downloadProgress.value = _downloadProgress.value.copy(
                    succeeded = succeeded,
                    failed = failed
                )
            }

            saveSubscriptionUris(downloadedUris)
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
        viewModelScope.launch { saveSubscriptions() }
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
        viewModelScope.launch { saveSubscriptions() }
        hideDeleteConfirmation()
    }

    fun showThemeDialog() { _showThemeDialog.value = true }
    fun hideThemeDialog() { _showThemeDialog.value = false }

    fun showWorkerDialog() { _showWorkerDialog.value = true }
    fun hideWorkerDialog() { _showWorkerDialog.value = false }

    fun showDownloadTimeoutDialog() { _showDownloadTimeoutDialog.value = true }
    fun hideDownloadTimeoutDialog() { _showDownloadTimeoutDialog.value = false }

    fun showLatencyRoundsDialog() { _showLatencyRoundsDialog.value = true }
    fun hideLatencyRoundsDialog() { _showLatencyRoundsDialog.value = false }

    fun showRoundTimeoutDialog() { _showRoundTimeoutDialog.value = true }
    fun hideRoundTimeoutDialog() { _showRoundTimeoutDialog.value = false }

    fun showBatchSizeDialog() { _showBatchSizeDialog.value = true }
    fun hideBatchSizeDialog() { _showBatchSizeDialog.value = false }

    fun showPortDialog() { _showPortDialog.value = true }
    fun hidePortDialog() { _showPortDialog.value = false }

    fun savePort(port: Int): Boolean {
        if (port !in 1024..65535) return false
        viewModelScope.launch {
            updateSettings(_settings.value.copy(webServerPort = port))
        }
        return true
    }

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

    private suspend fun loadSubscriptions(): List<Subscription> {
        val store = settingsRepository.getStore()
        val json = store.getString("subscriptions", "[]")
        return JsonConfig.json.decodeFromString(json)
    }

    private suspend fun saveSubscriptions() {
        val store = settingsRepository.getStore()
        val json = JsonConfig.json.encodeToString(_subscriptions.value)
        store.putString("subscriptions", json)
    }

    private suspend fun loadSubscriptionUris(): List<String> {
        val store = settingsRepository.getStore()
        val json = store.getString("subscription_uris", "[]")
        return JsonConfig.json.decodeFromString(json)
    }

    private suspend fun saveSubscriptionUris(uris: List<String>) {
        val store = settingsRepository.getStore()
        val json = JsonConfig.json.encodeToString(uris)
        store.putString("subscription_uris", json)
    }

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