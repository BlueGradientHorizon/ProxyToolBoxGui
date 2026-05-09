package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.*
import com.bghorizon.proxytoolboxgui.platform.Platform
import com.bghorizon.proxytoolboxgui.utils.ConfigUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(
    private val platform: Platform,
    private val settingsRepository: SettingsRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val testManager: ProxyTestManager,
    private val webServer: ProxyWebServer
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null
    private var timerJob: Job? = null
    private var downloadJob: Job? = null

    // Accumulators for test results during execution
    private val currentTestParseErrors = mutableSetOf<String>()
    private val currentTestValidErrors = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            try {
                settingsRepository.loadSettings()
                refreshSubscriptionsAndConfigs()
                discoverWorkers()
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
            }
        }
    }

    private suspend fun refreshSubscriptionsAndConfigs() {
        val loadedSubs = subscriptionRepository.loadSubscriptions()
        val configs = subscriptionRepository.loadWorkingConfigs()
        _uiState.update { it.copy(subscriptions = loadedSubs, workingConfigs = configs) }
        if (configs.isNotEmpty()) {
            _uiState.update { it.copy(appStatus = AppStatus.COMPLETED) }
        }
    }

    fun navigateTo(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun navigateBack() {
        _uiState.update { it.copy(currentScreen = Screen.Main) }
    }

    fun discoverWorkers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val libraryPath = platform.getWorkerLibraryPath()
                val json = GoBridge.discoverWorkers(libraryPath)
                val workers = JsonConfig.json.decodeFromString<List<WorkerInfo>>(json)
                _uiState.update { it.copy(workers = workers) }

                val currentSettings = _uiState.value.settings
                val savedName = currentSettings.selectedWorkerName
                val savedPath = currentSettings.selectedWorker

                // Find matching worker by path first, then by name
                val matchedWorker = workers.find { it.path == savedPath }
                    ?: workers.find { it.name == savedName }
                    ?: if (workers.isNotEmpty()) workers[0] else null

                if (matchedWorker != null && (matchedWorker.path != savedPath || savedName.isBlank())) {
                    selectWorker(matchedWorker)
                }

                if (workers.isEmpty()) {
                    _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
            }
        }
    }

    fun selectWorker(worker: WorkerInfo) {
        viewModelScope.launch {
            settingsRepository.updateSelectedWorker(worker.name, worker.path)
        }
    }

    fun startTest() {
        if (_uiState.value.workers.isEmpty() || testJob?.isActive == true) return

        testJob = viewModelScope.launch(Dispatchers.IO) {
            val job = coroutineContext[Job]
            val currentSettings = _uiState.value.settings

            if (currentSettings.selectedWorker.isBlank()) {
                withContext(Dispatchers.Main) {
                    platform.showToast("Please select a worker in settings")
                }
                _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
                return@launch
            }

            currentTestParseErrors.clear()
            currentTestValidErrors.clear()

            _uiState.update {
                it.copy(
                    appStatus = AppStatus.TESTING,
                    testProgress = it.testProgress.copy(isRunning = true)
                )
            }

            try {
                val setup = testManager.prepareTest(currentSettings, _uiState.value.subscriptions)
                if (setup.configs.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        platform.showToast("No configs found to test")
                    }
                    _uiState.update { it.copy(appStatus = AppStatus.IDLE) }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        subscriptions = setup.updatedSubscriptions,
                        testProgress = it.testProgress.copy(
                            totalBatches = setup.totalBatches,
                            totalRounds = setup.totalRounds,
                            totalSeconds = setup.totalSeconds,
                            elapsedSeconds = 0,
                            currentBatch = 0,
                            currentRound = 0,
                            batchProgresses = (1..setup.totalBatches).flatMap { b ->
                                (1..setup.totalRounds).map { r ->
                                    BatchProgress(
                                        batchNum = b,
                                        roundNum = r
                                    )
                                }
                            }
                        )
                    )
                }

                val resultConfigs = testManager.runLatencyTests(
                    settings = currentSettings,
                    configs = setup.configs,
                    onEvent = { event ->
                        if (job?.isActive != true) return@runLatencyTests
                        handleTestEvent(event, setup.configs, currentSettings)
                    }
                )

                if (job?.isActive != true) return@launch

                // Successfully ended - now reset and save results
                subscriptionRepository.resetTestData()

                // Save errors
                currentTestParseErrors.forEach { tag ->
                    testManager.extractIds(tag)?.let { (subId, configId) ->
                        subscriptionRepository.markParseErr(subId, configId)
                    }
                }
                currentTestValidErrors.forEach { tag ->
                    testManager.extractIds(tag)?.let { (subId, configId) ->
                        subscriptionRepository.markValidErr(subId, configId)
                    }
                }

                // Save working configs
                resultConfigs.forEach { cfg ->
                    testManager.extractIds(cfg.tag)?.let { (subId, configId) ->
                        subscriptionRepository.updateTestResult(subId, configId, true, cfg.connURI)
                    }
                }

                refreshSubscriptionsAndConfigs()
            } catch (e: Exception) {
                _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
                e.printStackTrace()
            } finally {
                timerJob?.cancel()
                _uiState.update {
                    it.copy(
                        testProgress = it.testProgress.copy(
                            isRunning = false,
                            isRoundActive = false
                        )
                    )
                }
                if (currentSettings.autoStartWebServer) {
                    startWebServer()
                }
            }
        }
    }

    private fun handleTestEvent(
        event: TestEvent,
        configs: List<ProxyConfig>,
        settings: AppSettings
    ) {
        when (event) {
            is TestEvent.ParseFailed -> {
                currentTestParseErrors.addAll(event.tags)
                _uiState.update { state ->
                    val current = state.subscriptions.toMutableList()
                    event.tags.forEach { tag ->
                        testManager.extractIds(tag)?.let { (subId, _) ->
                            val idx = current.indexOfFirst { it.id == subId }
                            if (idx >= 0) {
                                current[idx] =
                                    current[idx].copy(parseErr = current[idx].parseErr + 1)
                            }
                        }
                    }
                    state.copy(subscriptions = current)
                }
            }

            is TestEvent.ValidateFailed -> {
                currentTestValidErrors.addAll(event.tags)
                _uiState.update { state ->
                    val current = state.subscriptions.toMutableList()
                    event.tags.forEach { tag ->
                        testManager.extractIds(tag)?.let { (subId, _) ->
                            val idx = current.indexOfFirst { it.id == subId }
                            if (idx >= 0) {
                                current[idx] =
                                    current[idx].copy(validErr = current[idx].validErr + 1)
                            }
                        }
                    }
                    state.copy(subscriptions = current)
                }
            }

            is TestEvent.RoundStarted -> {
                val currentRoundAbsolute = (event.batch - 1) * settings.latencyRounds + event.round
                _uiState.update { state ->
                    val current = state.testProgress
                    val updatedProgresses = current.batchProgresses.toMutableList()
                    val idx =
                        updatedProgresses.indexOfFirst { it.batchNum == event.batch && it.roundNum == event.round }
                    if (idx >= 0) {
                        val totalForThisRound = if (event.round == 1) {
                            if (!settings.testByBatches) {
                                configs.size
                            } else if (event.batch < current.totalBatches) {
                                settings.batchSize
                            } else {
                                val rem = configs.size % settings.batchSize
                                if (rem == 0 && configs.size > 0) settings.batchSize else rem
                            }
                        } else {
                            updatedProgresses.find {
                                it.batchNum == event.batch && it.roundNum == event.round - 1
                            }?.succeeded ?: 0
                        }
                        updatedProgresses[idx] = updatedProgresses[idx].copy(
                            total = totalForThisRound,
                            running = totalForThisRound
                        )
                    }

                    state.copy(
                        testProgress = current.copy(
                            currentBatch = event.batch,
                            currentRound = event.round,
                            elapsedSeconds = (currentRoundAbsolute - 1) * settings.roundTimeout,
                            isRunning = true,
                            isRoundActive = true,
                            batchProgresses = updatedProgresses
                        )
                    )
                }

                timerJob?.cancel()
                timerJob = viewModelScope.launch {
                    while (isActive) {
                        delay(1000)
                        _uiState.update { it.copy(testProgress = it.testProgress.copy(elapsedSeconds = it.testProgress.elapsedSeconds + 1)) }
                    }
                }
            }

            is TestEvent.Progress -> {
                _uiState.update { state ->
                    val current = state.testProgress
                    val updatedProgresses = current.batchProgresses.toMutableList()
                    val batchIndex = updatedProgresses.indexOfFirst {
                        it.batchNum == current.currentBatch && it.roundNum == current.currentRound
                    }

                    if (batchIndex >= 0) {
                        val bp = updatedProgresses[batchIndex]
                        updatedProgresses[batchIndex] = bp.copy(
                            running = bp.running - 1,
                            failed = if (event.failed) bp.failed + 1 else bp.failed,
                            succeeded = if (!event.failed) bp.succeeded + 1 else bp.succeeded
                        )
                    }

                    state.copy(testProgress = current.copy(batchProgresses = updatedProgresses))
                }
            }

            is TestEvent.RoundEnded -> {
                timerJob?.cancel()
                _uiState.update { it.copy(testProgress = it.testProgress.copy(isRoundActive = false)) }
            }

            is TestEvent.Error -> {
                viewModelScope.launch {
                    platform.showToast("Test Error: ${event.message}")
                }
                stopTest(AppStatus.ERROR)
            }
        }
    }

    fun stopTest(newAppStatus: AppStatus = AppStatus.IDLE) {
        testJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            testManager.stopTests()
            _uiState.update { it.copy(appStatus = newAppStatus) }
        }
    }

    fun updateSubscriptions() {
        if (downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(appStatus = AppStatus.DOWNLOADING) }
            val subs = _uiState.value.subscriptions
            _uiState.update {
                it.copy(
                    downloadProgress = DownloadProgress(
                        total = subs.size,
                        isRunning = true
                    )
                )
            }

            var succeeded = 0
            var failed = 0

            for (sub in subs) {
                try {
                    val content = SubscriptionDownloader.download(
                        sub.url,
                        _uiState.value.settings.downloadTimeout
                    )
                    val lines = content.lines().filter { it.isNotBlank() }

                    // 1. Save metadata
                    val updatedSub = sub.copy(updatedAt = System.currentTimeMillis())
                    subscriptionRepository.saveSubscription(updatedSub)

                    // 2. Save child data (URIs)
                    subscriptionRepository.saveSubscriptionData(sub.id, lines)

                    succeeded++
                } catch (e: Exception) {
                    e.printStackTrace()
                    failed++
                }
                _uiState.update {
                    it.copy(
                        downloadProgress = it.downloadProgress.copy(
                            succeeded = succeeded,
                            failed = failed
                        )
                    )
                }
            }

            refreshSubscriptionsAndConfigs()
            _uiState.update { it.copy(appStatus = AppStatus.IDLE) }
            _uiState.update { it.copy(downloadProgress = it.downloadProgress.copy(isRunning = false)) }
        }
    }

    fun copyWorkingConfigs() {
        val uris = _uiState.value.workingConfigs.joinToString("\n") { it.connURI }
        platform.copyToClipboard(uris)
        platform.showToast("Copied to clipboard")
    }

    fun exportWorkingConfigs() {
        val uris = _uiState.value.workingConfigs.joinToString("\n") { it.connURI }
        platform.exportToFile(uris, "working_configs.txt")
    }

    fun toggleWebServer() {
        if (_uiState.value.webServerRunning) {
            stopWebServer()
        } else {
            startWebServer()
        }
    }

    private fun startWebServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = _uiState.value.settings
                val port = settings.webServerPort
                val host = if (settings.webServerLocalhost) "127.0.0.1" else "0.0.0.0"

                webServer.start(
                    port = port,
                    host = host,
                    getConfigUris = {
                        _uiState.value.workingConfigs.joinToString("\n") { it.connURI }
                    }
                )

                _uiState.update { it.copy(webServerRunning = true) }
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
                webServer.stop()
                _uiState.update { it.copy(webServerRunning = false) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun showAddSubscription() {
        updateDialog(ActiveDialog.AddSubscription)
    }

    fun showEditSubscription(sub: Subscription) {
        updateDialog(ActiveDialog.EditSubscription(sub))
    }

    fun hideDialog() {
        updateDialog(ActiveDialog.None)
    }

    fun saveSubscription(note: String, url: String) {
        val activeDialog = _uiState.value.activeDialog
        val existing =
            if (activeDialog is ActiveDialog.EditSubscription) activeDialog.subscription else null
        val newSub = if (existing != null) {
            existing.copy(note = note, url = url)
        } else {
            Subscription(
                id = ConfigUtils.generateUUID(),
                note = note,
                url = url
            )
        }

        viewModelScope.launch {
            subscriptionRepository.saveSubscription(newSub)
            refreshSubscriptionsAndConfigs()
        }
        hideDialog()
    }

    fun showDeleteSubscription(sub: Subscription) {
        updateDialog(ActiveDialog.DeleteConfirmation(sub))
    }

    fun confirmDeleteSubscription() {
        val activeDialog = _uiState.value.activeDialog
        if (activeDialog is ActiveDialog.DeleteConfirmation) {
            val sub = activeDialog.subscription
            viewModelScope.launch {
                subscriptionRepository.deleteSubscription(sub.id)
                refreshSubscriptionsAndConfigs()
            }
        }
        hideDialog()
    }

    fun updateDialog(dialog: ActiveDialog) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun savePort(port: Int): Boolean {
        if (port !in 1024..65535) return false
        viewModelScope.launch {
            updateSettings(_uiState.value.settings.copy(webServerPort = port))
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

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
        timerJob?.cancel()
        downloadJob?.cancel()
        stopWebServer()
    }
}
