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

class MainViewModel(
    private val platform: Platform,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Main)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

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
    private var timerJob: Job? = null
    private var downloadJob: Job? = null
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
        viewModelScope.launch {
            try {
                settingsRepository.loadSettings()
                val loadedSubs = loadSubscriptions()
                val configs = loadWorkingConfigs()
                
                _subscriptions.value = loadedSubs
                _workingConfigs.value = configs
                
                if (configs.isNotEmpty()) {
                    _appStatus.value = AppStatus.COMPLETED
                    
                    // If working configs exist but subscriptions have 0 working count, re-sync them
                    if (loadedSubs.any { it.working > 0 }.not()) {
                        val workingCounts = mutableMapOf<String, Int>()
                        configs.forEach { cfg ->
                            extractSubId(cfg.tag)?.let { id ->
                                workingCounts[id] = (workingCounts[id] ?: 0) + 1
                            }
                        }
                        if (workingCounts.isNotEmpty()) {
                            _subscriptions.value = loadedSubs.map { 
                                it.copy(working = workingCounts[it.id] ?: 0)
                            }
                        }
                    }
                }
                discoverWorkers()
            } catch (e: Exception) {
                e.printStackTrace()
                _appStatus.value = AppStatus.ERROR
            }
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
            val job = coroutineContext[Job]
            val currentSettings = _settings.value
            _appStatus.value = AppStatus.TESTING
            _testProgress.update { it.copy(isRunning = true) }

            try {
                val subs = _subscriptions.value.toMutableList()
                val configs = mutableListOf<ProxyConfig>()
                val seenUris = mutableSetOf<String>()

                // Reset performance statistics for each subscription before starting the test
                subs.forEachIndexed { i, sub ->
                    subs[i] = sub.copy(duplicated = 0, parseErr = 0, validErr = 0, working = 0)
                }
                _subscriptions.value = subs

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
                _subscriptions.value = subs

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
                _testProgress.update { it.copy(
                    batchProgresses = initialProgresses,
                    totalBatches = totalBatches,
                    totalRounds = totalRounds,
                    totalSeconds = totalSeconds,
                    elapsedSeconds = 0,
                    currentBatch = 0,
                    currentRound = 0
                ) }

                val subIdToIndex = subs.mapIndexed { i, sub -> sub.id to i }.toMap()

                val workerPath = currentSettings.selectedWorker
                val resultConfigs = GoBridge.runLatencyTests(
                    workerPath = workerPath,
                    settings = currentSettings,
                    callback = object : GoTestCallback {
                        override fun onParseFailed(tags: List<String>) {
                            if (job?.isActive != true) return
                            val current = _subscriptions.value.toMutableList()
                            // Update parse error counts for subscriptions based on reported failed tags
                            tags.forEach { tag ->
                                val subId = extractSubId(tag)
                                val idx = subIdToIndex[subId]
                                if (idx != null) {
                                    current[idx] = current[idx].copy(parseErr = current[idx].parseErr + 1)
                                }
                            }
                            _subscriptions.value = current
                        }

                        override fun onValidateFailed(tags: List<String>) {
                            if (job?.isActive != true) return
                            val current = _subscriptions.value.toMutableList()
                            // Update validation error counts for subscriptions based on reported failed tags
                            tags.forEach { tag ->
                                val subId = extractSubId(tag)
                                val idx = subIdToIndex[subId]
                                if (idx != null) {
                                    current[idx] = current[idx].copy(validErr = current[idx].validErr + 1)
                                }
                            }
                            _subscriptions.value = current
                        }

                        override fun onRoundStarted(batch: Long, round: Long, total: Long) {
                            if (job?.isActive != true) return
                            val latencyRounds = currentSettings.latencyRounds
                            val roundTimeout = currentSettings.roundTimeout
                            val currentRoundAbsolute = (batch.toInt() - 1) * latencyRounds + round.toInt()

                            _testProgress.update { current ->
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

                                current.copy(
                                    currentBatch = batch.toInt(),
                                    currentRound = round.toInt(),
                                    elapsedSeconds = (currentRoundAbsolute - 1) * roundTimeout,
                                    isRunning = true,
                                    isRoundActive = true,
                                    batchProgresses = updatedProgresses
                                )
                            }

                            timerJob?.cancel()
                            timerJob = viewModelScope.launch {
                                while (isActive) {
                                    delay(1000)
                                    _testProgress.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                                }
                            }
                        }

                        override fun onProgress(tag: String, delay: Long, failed: Boolean) {
                            if (job?.isActive != true) return
                            _testProgress.update { current ->
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

                                current.copy(batchProgresses = updatedProgresses)
                            }
                        }

                        override fun onRoundEnded(batch: Long, round: Long) {
                            if (job?.isActive != true) return
                            timerJob?.cancel()
                            _testProgress.update { it.copy(isRoundActive = false) }
                        }
                    },
                    connUris = configs
                )

                if (job?.isActive != true) return@launch

                val subsFinal = _subscriptions.value.toMutableList()
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

                _subscriptions.value = subsFinal
                _workingConfigs.value = resultConfigs
                _appStatus.value = AppStatus.COMPLETED
                saveSubscriptions()
                saveWorkingConfigs()
            } catch (e: Exception) {
                _appStatus.value = AppStatus.ERROR
                e.printStackTrace()
            } finally {
                timerJob?.cancel()
                _testProgress.update { it.copy(isRunning = false, isRoundActive = false) }
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
            _appStatus.value = AppStatus.IDLE
        }
    }

    fun updateSubscriptions() {
        if (downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _appStatus.value = AppStatus.DOWNLOADING
            val subs = _subscriptions.value.toMutableList()
            _downloadProgress.value = DownloadProgress(
                total = subs.size,
                isRunning = true
            )

            var succeeded = 0
            var failed = 0

            for (i in subs.indices) {
                val sub = subs[i]
                try {
                    val content = SubscriptionDownloader.download(sub.url, _settings.value.downloadTimeout)
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
                _downloadProgress.value = _downloadProgress.value.copy(
                    succeeded = succeeded,
                    failed = failed
                )
            }

            _subscriptions.value = subs
            _downloadProgress.value = _downloadProgress.value.copy(isRunning = false)
            _appStatus.value = AppStatus.IDLE
            saveSubscriptions()
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
            viewModelScope.launch {
                val store = settingsRepository.getStore()
                store.putString(subUriKey(sub.id), "[]")
                saveSubscriptions()
            }
        }
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
        val json = JsonConfig.json.encodeToString(_workingConfigs.value)
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