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

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            try {
                settingsRepository.loadSettings()
                val loadedSubs = subscriptionRepository.loadSubscriptions()
                val configs = subscriptionRepository.loadWorkingConfigs()
                
                _uiState.update { it.copy(subscriptions = loadedSubs, workingConfigs = configs) }
                
                if (configs.isNotEmpty()) {
                    _uiState.update { it.copy(appStatus = AppStatus.COMPLETED) }
                    
                    // If working configs exist but subscriptions have 0 working count, re-sync them
                    if (loadedSubs.any { it.working > 0 }.not()) {
                        val workingCounts = mutableMapOf<String, Int>()
                        configs.forEach { cfg ->
                            testManager.extractSubId(cfg.tag)?.let { id ->
                                workingCounts[id] = (workingCounts[id] ?: 0) + 1
                            }
                        }
                        if (workingCounts.isNotEmpty()) {
                            _uiState.update { state ->
                                state.copy(subscriptions = state.subscriptions.map { 
                                    it.copy(working = workingCounts[it.id] ?: 0)
                                })
                            }
                        }
                    }
                }
                discoverWorkers()
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
            }
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
                if (workers.isEmpty()) {
                    _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
            }
        }
    }

    fun selectWorker(path: String) {
        viewModelScope.launch {
            settingsRepository.updateSelectedWorker(path)
        }
    }

    fun startTest() {
        if (_uiState.value.workers.isEmpty() || testJob?.isActive == true) return

        testJob = viewModelScope.launch(Dispatchers.IO) {
            val job = coroutineContext[Job]
            val currentSettings = _uiState.value.settings
            _uiState.update { it.copy(
                appStatus = AppStatus.TESTING,
                testProgress = it.testProgress.copy(isRunning = true)
            ) }

            try {
                val setup = testManager.prepareTest(currentSettings, _uiState.value.subscriptions)
                _uiState.update { it.copy(
                    subscriptions = setup.updatedSubscriptions,
                    testProgress = it.testProgress.copy(
                        totalBatches = setup.totalBatches,
                        totalRounds = setup.totalRounds,
                        totalSeconds = setup.totalSeconds,
                        elapsedSeconds = 0,
                        currentBatch = 0,
                        currentRound = 0,
                        batchProgresses = (1..setup.totalBatches).flatMap { b ->
                            (1..setup.totalRounds).map { r -> BatchProgress(batchNum = b, roundNum = r) }
                        }
                    )
                ) }

                val subIdToIndex = setup.updatedSubscriptions.mapIndexed { i, sub -> sub.id to i }.toMap()

                val resultConfigs = testManager.runLatencyTests(
                    settings = currentSettings,
                    configs = setup.configs,
                    onEvent = { event ->
                        if (job?.isActive != true) return@runLatencyTests
                        handleTestEvent(event, subIdToIndex, setup.configs, currentSettings)
                    }
                )

                if (job?.isActive != true) return@launch

                val subsFinal = _uiState.value.subscriptions.toMutableList()
                val workingCounts = mutableMapOf<String, Int>()

                resultConfigs.forEach { cfg ->
                    testManager.extractSubId(cfg.tag)?.let { subId ->
                        workingCounts[subId] = (workingCounts[subId] ?: 0) + 1
                    }
                }

                subsFinal.indices.forEach { i ->
                    val id = subsFinal[i].id
                    subsFinal[i] = subsFinal[i].copy(working = workingCounts[id] ?: 0)
                }

                _uiState.update { it.copy(
                    subscriptions = subsFinal,
                    workingConfigs = resultConfigs,
                    appStatus = AppStatus.COMPLETED
                ) }
                subscriptionRepository.saveSubscriptions(_uiState.value.subscriptions)
                subscriptionRepository.saveWorkingConfigs(_uiState.value.workingConfigs)
            } catch (e: Exception) {
                _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
                e.printStackTrace()
            } finally {
                timerJob?.cancel()
                _uiState.update { it.copy(testProgress = it.testProgress.copy(isRunning = false, isRoundActive = false)) }
                if (currentSettings.autoStartWebServer) {
                    startWebServer()
                }
            }
        }
    }

    private fun handleTestEvent(
        event: TestEvent,
        subIdToIndex: Map<String, Int>,
        configs: List<ProxyConfig>,
        settings: AppSettings
    ) {
        when (event) {
            is TestEvent.ParseFailed -> {
                _uiState.update { state ->
                    val current = state.subscriptions.toMutableList()
                    event.tags.forEach { tag ->
                        testManager.extractSubId(tag)?.let { subId ->
                            subIdToIndex[subId]?.let { idx ->
                                current[idx] = current[idx].copy(parseErr = current[idx].parseErr + 1)
                            }
                        }
                    }
                    state.copy(subscriptions = current)
                }
            }
            is TestEvent.ValidateFailed -> {
                _uiState.update { state ->
                    val current = state.subscriptions.toMutableList()
                    event.tags.forEach { tag ->
                        testManager.extractSubId(tag)?.let { subId ->
                            subIdToIndex[subId]?.let { idx ->
                                current[idx] = current[idx].copy(validErr = current[idx].validErr + 1)
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
                    val idx = updatedProgresses.indexOfFirst { it.batchNum == event.batch && it.roundNum == event.round }
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
                        updatedProgresses[idx] = updatedProgresses[idx].copy(total = totalForThisRound, running = totalForThisRound)
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
        }
    }

    fun stopTest() {
        testJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            testManager.stopTests()
            _uiState.update { it.copy(appStatus = AppStatus.IDLE) }
        }
    }

    fun updateSubscriptions() {
        if (downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(appStatus = AppStatus.DOWNLOADING) }
            val subs = _uiState.value.subscriptions.toMutableList()
            _uiState.update { it.copy(downloadProgress = DownloadProgress(
                total = subs.size,
                isRunning = true
            )) }

            var succeeded = 0
            var failed = 0

            for (i in subs.indices) {
                val sub = subs[i]
                try {
                    val content = SubscriptionDownloader.download(sub.url, _uiState.value.settings.downloadTimeout)
                    val lines = content.lines().filter { it.isNotBlank() }
                    subscriptionRepository.saveSubscriptionUris(sub.id, lines)
                    subs[i] = sub.copy(
                        total = lines.size,
                        duplicated = 0,
                        parseErr = 0,
                        validErr = 0,
                        working = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                    succeeded++
                } catch (e: Exception) {
                    failed++
                }
                _uiState.update { it.copy(downloadProgress = it.downloadProgress.copy(
                    succeeded = succeeded,
                    failed = failed
                )) }
            }

            _uiState.update { it.copy(subscriptions = subs, appStatus = AppStatus.IDLE) }
            _uiState.update { it.copy(downloadProgress = it.downloadProgress.copy(isRunning = false)) }
            subscriptionRepository.saveSubscriptions(_uiState.value.subscriptions)
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
        val existing = if (activeDialog is ActiveDialog.EditSubscription) activeDialog.subscription else null
        val newId = ConfigUtils.generateUUID()
        val newSub = if (existing != null) {
            existing.copy(note = note, url = url)
        } else {
            Subscription(
                id = newId,
                note = note,
                url = url
            )
        }

        _uiState.update { state ->
            state.copy(subscriptions = if (existing != null) {
                state.subscriptions.map { if (it.id == existing.id) newSub else it }
            } else {
                state.subscriptions + newSub
            })
        }
        viewModelScope.launch { subscriptionRepository.saveSubscriptions(_uiState.value.subscriptions) }
        hideDialog()
    }

    fun showDeleteSubscription(sub: Subscription) {
        updateDialog(ActiveDialog.DeleteConfirmation(sub))
    }

    fun confirmDeleteSubscription() {
        val activeDialog = _uiState.value.activeDialog
        if (activeDialog is ActiveDialog.DeleteConfirmation) {
            val sub = activeDialog.subscription
            _uiState.update { state -> state.copy(subscriptions = state.subscriptions.filter { it.id != sub.id }) }
            viewModelScope.launch {
                subscriptionRepository.deleteSubscriptionUris(sub.id)
                subscriptionRepository.saveSubscriptions(_uiState.value.subscriptions)
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
