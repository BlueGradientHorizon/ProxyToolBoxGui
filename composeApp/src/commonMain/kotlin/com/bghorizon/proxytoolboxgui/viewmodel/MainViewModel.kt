package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.*
import com.bghorizon.proxytoolboxgui.platform.Platform
import com.bghorizon.proxytoolboxgui.utils.ConfigUtils
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MainUiState(
    val currentScreen: Screen = Screen.Main,
    val testProgress: TestProgress = TestProgress(),
    val downloadProgress: DownloadProgress = DownloadProgress(),
    val subscriptions: List<Subscription> = emptyList(),
    val workers: List<WorkerInfo> = emptyList(),
    val appStatus: AppStatus = AppStatus.IDLE,
    val webServerRunning: Boolean = false,
    val workingConfigs: List<ProxyConfig> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val showAddSubscription: Boolean = false,
    val editingSubscription: Subscription? = null,
    val showDeleteConfirmation: Subscription? = null,
    val showThemeDialog: Boolean = false,
    val showWorkerDialog: Boolean = false,
    val showDownloadTimeoutDialog: Boolean = false,
    val showLatencyRoundsDialog: Boolean = false,
    val showRoundTimeoutDialog: Boolean = false,
    val showBatchSizeDialog: Boolean = false,
    val showPortDialog: Boolean = false
)

class MainViewModel(
    private val platform: Platform,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null
    private var timerJob: Job? = null
    private var downloadJob: Job? = null
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            try {
                settingsRepository.loadSettings()
                val loadedSubs = loadSubscriptions()
                val configs = loadWorkingConfigs()
                
                _uiState.update { it.copy(subscriptions = loadedSubs, workingConfigs = configs) }
                
                if (configs.isNotEmpty()) {
                    _uiState.update { it.copy(appStatus = AppStatus.COMPLETED) }
                    
                    // If working configs exist but subscriptions have 0 working count, re-sync them
                    if (loadedSubs.any { it.working > 0 }.not()) {
                        val workingCounts = mutableMapOf<String, Int>()
                        configs.forEach { cfg ->
                            extractSubId(cfg.tag)?.let { id ->
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
                val subs = _uiState.value.subscriptions.toMutableList()
                val configs = mutableListOf<ProxyConfig>()
                val seenUris = mutableSetOf<String>()

                // Reset performance statistics for each subscription before starting the test
                subs.forEachIndexed { i, sub ->
                    subs[i] = sub.copy(duplicated = 0, parseErr = 0, validErr = 0, working = 0)
                }
                _uiState.update { it.copy(subscriptions = subs) }

                // Iterate through all subscriptions to collect their proxy URIs
                for (i in subs.indices) {
                    val sub = subs[i]
                    val uris = loadSubscriptionUris(sub.id).filter { it.isNotBlank() }
                    
                    val uniqueUris = if (currentSettings.performDedup) {
                        ConfigUtils.naiveDeduplicate(uris, seenUris)
                    } else {
                        uris
                    }

                    if (currentSettings.performDedup) {
                        subs[i] = subs[i].copy(duplicated = uris.size - uniqueUris.size)
                    }

                    // Create a unique tag for each proxy config to track its source subscription
                    for ((uriIndex, uri) in uniqueUris.withIndex()) {
                        val tag = "sub-${sub.id}-${uriIndex}"
                        configs.add(ProxyConfig(tag = tag, connURI = uri))
                    }
                }
                _uiState.update { it.copy(subscriptions = subs) }

                val totalBatches = if (currentSettings.testByBatches && currentSettings.batchSize > 0) {
                    (configs.size + currentSettings.batchSize - 1) / currentSettings.batchSize
                } else {
                    1
                }
                val totalRounds = currentSettings.latencyRounds
                val roundTimeout = currentSettings.roundTimeout
                val totalSeconds = totalBatches * totalRounds * roundTimeout

                val initialProgresses = (1..totalBatches).flatMap { b ->
                    (1..totalRounds).map { r ->
                        BatchProgress(batchNum = b, roundNum = r)
                    }
                }
                _uiState.update { it.copy(
                    testProgress = it.testProgress.copy(
                        batchProgresses = initialProgresses,
                        totalBatches = totalBatches,
                        totalRounds = totalRounds,
                        totalSeconds = totalSeconds,
                        elapsedSeconds = 0,
                        currentBatch = 0,
                        currentRound = 0
                    )
                ) }

                val subIdToIndex = subs.mapIndexed { i, sub -> sub.id to i }.toMap()

                val workerPath = currentSettings.selectedWorker
                val resultConfigs = GoBridge.runLatencyTests(
                    workerPath = workerPath,
                    settings = currentSettings,
                    callback = object : GoTestCallback {
                        override fun onParseFailed(tags: List<String>) {
                            if (job?.isActive != true) return
                            _uiState.update { state ->
                                val current = state.subscriptions.toMutableList()
                                // Update parse error counts for subscriptions based on reported failed tags
                                tags.forEach { tag ->
                                    val subId = extractSubId(tag)
                                    val idx = subIdToIndex[subId]
                                    if (idx != null) {
                                        current[idx] = current[idx].copy(parseErr = current[idx].parseErr + 1)
                                    }
                                }
                                state.copy(subscriptions = current)
                            }
                        }

                        override fun onValidateFailed(tags: List<String>) {
                            if (job?.isActive != true) return
                            _uiState.update { state ->
                                val current = state.subscriptions.toMutableList()
                                // Update validation error counts for subscriptions based on reported failed tags
                                tags.forEach { tag ->
                                    val subId = extractSubId(tag)
                                    val idx = subIdToIndex[subId]
                                    if (idx != null) {
                                        current[idx] = current[idx].copy(validErr = current[idx].validErr + 1)
                                    }
                                }
                                state.copy(subscriptions = current)
                            }
                        }

                        override fun onRoundStarted(batch: Long, round: Long, total: Long) {
                            if (job?.isActive != true) return
                            val latencyRounds = currentSettings.latencyRounds
                            val roundTimeout = currentSettings.roundTimeout
                            val currentRoundAbsolute = (batch.toInt() - 1) * latencyRounds + round.toInt()

                            _uiState.update { state ->
                                val current = state.testProgress
                                val updatedProgresses = current.batchProgresses.toMutableList()
                                val idx = updatedProgresses.indexOfFirst { it.batchNum == batch.toInt() && it.roundNum == round.toInt() }
                                if (idx >= 0) {
                                    val totalForThisRound = if (round.toInt() == 1) {
                                        if (!currentSettings.testByBatches) {
                                            configs.size
                                        } else if (batch.toInt() < totalBatches) {
                                            currentSettings.batchSize
                                        } else {
                                            val rem = configs.size % currentSettings.batchSize
                                            if (rem == 0 && configs.size > 0) currentSettings.batchSize else rem
                                        }
                                    } else {
                                        updatedProgresses.find { 
                                            it.batchNum == batch.toInt() && it.roundNum == round.toInt() - 1 
                                        }?.succeeded ?: 0
                                    }

                                    updatedProgresses[idx] = updatedProgresses[idx].copy(
                                        total = totalForThisRound,
                                        running = totalForThisRound
                                    )
                                }

                                state.copy(
                                    testProgress = current.copy(
                                        currentBatch = batch.toInt(),
                                        currentRound = round.toInt(),
                                        elapsedSeconds = (currentRoundAbsolute - 1) * roundTimeout,
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

                        override fun onProgress(tag: String, delay: Long, failed: Boolean) {
                            if (job?.isActive != true) return
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
                                        failed = if (failed) bp.failed + 1 else bp.failed,
                                        succeeded = if (!failed) bp.succeeded + 1 else bp.succeeded
                                    )
                                }

                                state.copy(testProgress = current.copy(batchProgresses = updatedProgresses))
                            }
                        }

                        override fun onRoundEnded(batch: Long, round: Long) {
                            if (job?.isActive != true) return
                            timerJob?.cancel()
                            _uiState.update { it.copy(testProgress = it.testProgress.copy(isRoundActive = false)) }
                        }
                    },
                    connUris = configs
                )

                if (job?.isActive != true) return@launch

                val subsFinal = _uiState.value.subscriptions.toMutableList()
                val workingCounts = mutableMapOf<String, Int>()

                // Aggregate working configuration counts for each subscription from the test results
                resultConfigs.forEach { cfg ->
                    val subId = extractSubId(cfg.tag)
                    if (subId != null) {
                        workingCounts[subId] = (workingCounts[subId] ?: 0) + 1
                    }
                }

                // Update the final list of subscriptions with their respective counts of working configurations
                subsFinal.indices.forEach { i ->
                    val id = subsFinal[i].id
                    subsFinal[i] = subsFinal[i].copy(working = workingCounts[id] ?: 0)
                }

                _uiState.update { it.copy(
                    subscriptions = subsFinal,
                    workingConfigs = resultConfigs,
                    appStatus = AppStatus.COMPLETED
                ) }
                saveSubscriptions()
                saveWorkingConfigs()
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

    private fun extractSubId(tag: String): String? {
        if (!tag.startsWith("sub-")) return null
        val content = tag.removePrefix("sub-")
        val lastDash = content.lastIndexOf('-')
        return if (lastDash > 0) content.substring(0, lastDash) else null
    }

    fun stopTest() {
        testJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            GoBridge.stopTests()
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
                    saveSubscriptionUris(sub.id, lines)
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
            saveSubscriptions()
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
                stopWebServer()

                val settings = _uiState.value.settings
                val port = settings.webServerPort
                val localhostOnly = settings.webServerLocalhost
                val host = if (localhostOnly) "127.0.0.1" else "0.0.0.0"

                server = embeddedServer(CIO, port = port, host = host) {
                    routing {
                        get("/") {
                            val uris = _uiState.value.workingConfigs.joinToString("\n") { it.connURI }
                            call.respondText(uris, contentType = io.ktor.http.ContentType.Text.Plain)
                        }
                    }
                }.start(wait = false)

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
                server?.stop(1000, 2000)
                server = null
                _uiState.update { it.copy(webServerRunning = false) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun showAddSubscription() {
        _uiState.update { it.copy(editingSubscription = null, showAddSubscription = true) }
    }

    fun showEditSubscription(sub: Subscription) {
        _uiState.update { it.copy(editingSubscription = sub, showAddSubscription = true) }
    }

    fun hideAddSubscription() {
        _uiState.update { it.copy(showAddSubscription = false, editingSubscription = null) }
    }

    fun saveSubscription(note: String, url: String) {
        val existing = _uiState.value.editingSubscription
        val newId = generateUUID()
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
        viewModelScope.launch { saveSubscriptions() }
        hideAddSubscription()
    }

    fun showDeleteSubscription(sub: Subscription) {
        _uiState.update { it.copy(showDeleteConfirmation = sub) }
    }

    fun hideDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = null) }
    }

    fun confirmDeleteSubscription() {
        _uiState.value.showDeleteConfirmation?.let { sub ->
            _uiState.update { state -> state.copy(subscriptions = state.subscriptions.filter { it.id != sub.id }) }
            viewModelScope.launch {
                val store = settingsRepository.getStore()
                store.putString(subUriKey(sub.id), "[]")
                saveSubscriptions()
            }
        }
        hideDeleteConfirmation()
    }

    fun showThemeDialog() { _uiState.update { it.copy(showThemeDialog = true) } }
    fun hideThemeDialog() { _uiState.update { it.copy(showThemeDialog = false) } }

    fun showWorkerDialog() { _uiState.update { it.copy(showWorkerDialog = true) } }
    fun hideWorkerDialog() { _uiState.update { it.copy(showWorkerDialog = false) } }

    fun showDownloadTimeoutDialog() { _uiState.update { it.copy(showDownloadTimeoutDialog = true) } }
    fun hideDownloadTimeoutDialog() { _uiState.update { it.copy(showDownloadTimeoutDialog = false) } }

    fun showLatencyRoundsDialog() { _uiState.update { it.copy(showLatencyRoundsDialog = true) } }
    fun hideLatencyRoundsDialog() { _uiState.update { it.copy(showLatencyRoundsDialog = false) } }

    fun showRoundTimeoutDialog() { _uiState.update { it.copy(showRoundTimeoutDialog = true) } }
    fun hideRoundTimeoutDialog() { _uiState.update { it.copy(showRoundTimeoutDialog = false) } }

    fun showBatchSizeDialog() { _uiState.update { it.copy(showBatchSizeDialog = true) } }
    fun hideBatchSizeDialog() { _uiState.update { it.copy(showBatchSizeDialog = false) } }

    fun showPortDialog() { _uiState.update { it.copy(showPortDialog = true) } }
    fun hidePortDialog() { _uiState.update { it.copy(showPortDialog = false) } }

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

    private suspend fun loadSubscriptions(): List<Subscription> {
        val store = settingsRepository.getStore()
        val json = store.getString("subscriptions", "[]")
        return JsonConfig.json.decodeFromString(json)
    }

    private suspend fun saveSubscriptions() {
        val store = settingsRepository.getStore()
        val json = JsonConfig.json.encodeToString(_uiState.value.subscriptions)
        store.putString("subscriptions", json)
    }

    private suspend fun loadWorkingConfigs(): List<ProxyConfig> {
        return try {
            val store = settingsRepository.getStore()
            val json = store.getString("working_configs", "[]")
            JsonConfig.json.decodeFromString(json)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun saveWorkingConfigs() {
        val store = settingsRepository.getStore()
        val json = JsonConfig.json.encodeToString(_uiState.value.workingConfigs)
        store.putString("working_configs", json)
    }

    private fun subUriKey(subId: String) = "sub_uris_$subId"

    private suspend fun loadSubscriptionUris(subId: String): List<String> {
        val store = settingsRepository.getStore()
        val json = store.getString(subUriKey(subId), "[]")
        return JsonConfig.json.decodeFromString(json)
    }

    private suspend fun saveSubscriptionUris(subId: String, uris: List<String>) {
        val store = settingsRepository.getStore()
        val json = JsonConfig.json.encodeToString(uris)
        store.putString(subUriKey(subId), json)
    }

    private fun generateUUID(): String {
        val hexChars = "0123456789abcdef"
        val random = kotlin.random.Random.Default
        fun randomHex(length: Int) = buildString {
            repeat(length) { append(hexChars[random.nextInt(16)]) }
        }
        return "${randomHex(8)}-${randomHex(4)}-4${randomHex(3)}-${(8 + random.nextInt(4)).toString(16)}${randomHex(3)}-${randomHex(12)}"
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
        timerJob?.cancel()
        downloadJob?.cancel()
        stopWebServer()
    }
}

enum class Screen {
    Main, Subscriptions, Settings
}
