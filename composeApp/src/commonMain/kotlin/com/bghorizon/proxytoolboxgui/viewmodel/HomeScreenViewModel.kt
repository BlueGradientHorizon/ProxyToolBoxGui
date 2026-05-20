package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.*
import com.bghorizon.proxytoolboxgui.data.db.ConfigLatencyTestResultUpdate
import com.bghorizon.proxytoolboxgui.data.db.ConfigSpeedTestResultUpdate
import com.bghorizon.proxytoolboxgui.di.AppModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.jetbrains.compose.resources.getString
import proxytoolboxgui.composeapp.generated.resources.*

class HomeScreenViewModel(private val module: AppModule) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeScreenUiState())
    val uiState = _uiState.asStateFlow()

    private var testJob: Job? = null
    private var timerJob: Job? = null

    fun startTest(
        appStatus: AppStatus,
        subscriptions: List<Subscription>,
        onTestCompleted: () -> Unit = {},
    ) {
        if (module.workerRepository.workers.value.isEmpty() || testJob?.isActive == true) return

        if (appStatus == AppStatus.UPDATING_SUBS) {
            viewModelScope.launch {
                module.platform.showToast(getString(Res.string.msg_cannot_test_while_updating))
            }
            return
        }

        testJob = viewModelScope.launch(Dispatchers.IO) {
            val job = coroutineContext[Job]
            val currentSettings = module.settingsRepository.settings.value

            if (currentSettings.selectedWorker.isBlank()) {
                val msg = getString(Res.string.msg_select_worker)
                withContext(Dispatchers.Main) {
                    module.platform.showToast(msg)
                }
                module.appStatusManager.updateStatus(AppStatus.ERROR)
                return@launch
            }

            module.appStatusManager.updateStatus(AppStatus.PARSING)
            
            try {
                val setup = module.testManager.prepareTest(currentSettings, subscriptions)
                setup.updatedSubscriptions.forEach { sub ->
                    module.subscriptionRepository.saveSub(sub)
                }

                if (setup.configs.isEmpty()) {
                    val msg = getString(Res.string.msg_no_configs_to_test)
                    withContext(Dispatchers.Main) {
                        module.platform.showToast(msg)
                    }
                    module.appStatusManager.updateStatus(AppStatus.IDLE)
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        latencyTestProgress = LatencyTestProgress(
                            totalBatches = setup.totalBatches,
                            totalRounds = setup.totalRounds,
                            totalSeconds = setup.totalSeconds,
                            elapsedSeconds = 0,
                            currentBatch = 0,
                            currentRound = 0,
                            batchProgresses = (1..setup.totalBatches).flatMap { b ->
                                (1..setup.totalRounds).map { r ->
                                    BatchProgress(batchNum = b, roundNum = r)
                                }
                            },
                            isRunning = true,
                            isRoundActive = false
                        ),
                        speedTestProgress = SpeedTestProgress()
                    )
                }

                startTimer()

                val resultConfigs = module.testManager.runLatencyTests(
                    settings = currentSettings,
                    configs = setup.configs
                ) { event ->
                    if (job?.isActive != true) return@runLatencyTests
                    handleLatencyTestEvent(event, currentSettings)
                }

                if (job?.isActive != true) return@launch

                module.subscriptionRepository.resetWorkingData()

                // Save latency configs
                val latencyUpdates = resultConfigs.mapNotNull { cfg ->
                    module.testManager.extractIds(cfg.tag)?.let { (subId, configId) ->
                        ConfigLatencyTestResultUpdate(
                            subId = subId,
                            configId = configId,
                            working = true,
                            fixedUri = cfg.connURI,
                            delay = cfg.delay
                        )
                    }
                }
                module.subscriptionRepository.updateConfigLatencyTestResultsBatch(latencyUpdates)

                _uiState.update {
                    it.copy(latencyTestProgress = it.latencyTestProgress.copy(isRunning = false))
                }

                // If Speed test enabled, proceed to stage two
                if (currentSettings.performSpeedTest && resultConfigs.isNotEmpty()) {
                    module.appStatusManager.updateStatus(AppStatus.SPEED_TESTING)
                    
                    var speedConfigs = resultConfigs
                    if (currentSettings.sortByLatencyDelay) {
                        speedConfigs = speedConfigs.sortedBy { it.delay }
                    }
                    
                    val speedTotalBatches = if (currentSettings.testByBatches && (currentSettings.batchSize > 0)) {
                        (speedConfigs.size + currentSettings.batchSize - 1) / currentSettings.batchSize
                    } else 1
                    
                    val speedTotalSeconds = speedTotalBatches * currentSettings.speedTestRounds * currentSettings.roundTimeout
                    
                    _uiState.update {
                        it.copy(
                            speedTestProgress = SpeedTestProgress(
                                totalBatches = speedTotalBatches,
                                totalRounds = currentSettings.speedTestRounds,
                                totalSeconds = speedTotalSeconds,
                                elapsedSeconds = 0,
                                currentBatch = 0,
                                currentRound = 0,
                                batchProgresses = (1..speedTotalBatches).flatMap { b ->
                                    (1..currentSettings.speedTestRounds).map { r ->
                                        BatchProgress(batchNum = b, roundNum = r)
                                    }
                                },
                                isRunning = true,
                                isRoundActive = false
                            )
                        )
                    }

                    val speedWorkingConfigs = module.testManager.runSpeedTests(
                        settings = currentSettings,
                        configs = speedConfigs
                    ) { event ->
                        if (job?.isActive != true) return@runSpeedTests
                        handleSpeedTestEvent(event, currentSettings)
                    }
                    
                    if (job?.isActive != true) return@launch
                    
                    val speedUpdates = speedWorkingConfigs.mapNotNull { cfg ->
                        module.testManager.extractIds(cfg.tag)?.let { (subId, configId) ->
                            ConfigSpeedTestResultUpdate(
                                subId = subId,
                                configId = configId,
                                workingSpeed = true,
                                speed = cfg.speed
                            )
                        }
                    }
                    module.subscriptionRepository.updateConfigSpeedTestResultsBatch(speedUpdates)
                    
                    module.appStatusManager.updateStatus(
                        if (speedWorkingConfigs.isNotEmpty()) AppStatus.COMPLETED else AppStatus.IDLE
                    )
                } else {
                    module.appStatusManager.updateStatus(
                        if (resultConfigs.isNotEmpty()) AppStatus.COMPLETED else AppStatus.IDLE
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    module.appStatusManager.updateStatus(AppStatus.STOPPED)
                } else {
                    module.appStatusManager.updateStatus(AppStatus.ERROR)
                    e.printStackTrace()
                }
            } finally {
                withContext(NonCancellable) {
                    timerJob?.cancel()
                    _uiState.update {
                        it.copy(
                            latencyTestProgress = it.latencyTestProgress.copy(isRunning = false, isRoundActive = false),
                            speedTestProgress = it.speedTestProgress.copy(isRunning = false, isRoundActive = false)
                        )
                    }
                    onTestCompleted()
                }
            }
        }
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { state ->
                    if (state.latencyTestProgress.isRunning) {
                        state.copy(latencyTestProgress = state.latencyTestProgress.copy(elapsedSeconds = state.latencyTestProgress.elapsedSeconds + 1))
                    } else if (state.speedTestProgress.isRunning) {
                        state.copy(speedTestProgress = state.speedTestProgress.copy(elapsedSeconds = state.speedTestProgress.elapsedSeconds + 1))
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun handleLatencyTestEvent(event: LatencyTestEvent, settings: AppSettings) {
        when (event) {
            is LatencyTestEvent.ParseFailed -> {
                module.appStatusManager.updateStatus(AppStatus.PARSING)
                viewModelScope.launch(Dispatchers.IO) {
                    module.subscriptionRepository.resetParseErrorData()
                    val batch = event.errors.keys.mapNotNull { tag ->
                        module.testManager.extractIds(tag)
                    }
                    module.subscriptionRepository.markConfigsParseErrBatch(batch)
                }
            }

            is LatencyTestEvent.ValidateFailed -> {
                module.appStatusManager.updateStatus(AppStatus.VALIDATING)
                viewModelScope.launch(Dispatchers.IO) {
                    module.subscriptionRepository.resetValidErrorData()
                    val batch = event.errors.keys.mapNotNull { tag ->
                        module.testManager.extractIds(tag)
                    }
                    module.subscriptionRepository.markConfigsValidErrBatch(batch)
                }
            }

            is LatencyTestEvent.RoundStarted -> {
                val currentRoundAbsolute = (event.batch - 1) * settings.latencyRounds + event.round
                module.appStatusManager.updateStatus(AppStatus.TESTING)
                _uiState.update { state ->
                    val current = state.latencyTestProgress
                    val updatedProgresses = current.batchProgresses.toMutableList()
                    val idx =
                        updatedProgresses.indexOfFirst { (it.batchNum == event.batch && it.roundNum == event.round) }
                    if (idx >= 0) {
                        updatedProgresses[idx] = updatedProgresses[idx].copy(
                            total = event.total,
                            running = event.total
                        )
                    }

                    state.copy(
                        latencyTestProgress = current.copy(
                            currentBatch = event.batch,
                            currentRound = event.round,
                            elapsedSeconds = (currentRoundAbsolute - 1) * settings.roundTimeout,
                            isRunning = true,
                            isRoundActive = true,
                            batchProgresses = updatedProgresses
                        )
                    )
                }
            }

            is LatencyTestEvent.Progress -> {
                _uiState.update { state ->
                    val current = state.latencyTestProgress
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

                    state.copy(latencyTestProgress = current.copy(batchProgresses = updatedProgresses))
                }
            }

            is LatencyTestEvent.RoundEnded -> {
                _uiState.update { it.copy(latencyTestProgress = it.latencyTestProgress.copy(isRoundActive = false)) }
            }

            is LatencyTestEvent.Error -> {
                viewModelScope.launch {
                    val msg = getString(Res.string.msg_test_error, event.message)
                    module.platform.showToast(msg)
                }
                stopTest(AppStatus.ERROR, event.message)
            }
        }
    }

    private fun handleSpeedTestEvent(event: SpeedTestEvent, settings: AppSettings) {
        when (event) {
            is SpeedTestEvent.RoundStarted -> {
                val currentRoundAbsolute = (event.batch - 1) * settings.speedTestRounds + event.round
                module.appStatusManager.updateStatus(AppStatus.SPEED_TESTING)
                _uiState.update { state ->
                    val current = state.speedTestProgress
                    val updatedProgresses = current.batchProgresses.toMutableList()
                    val idx =
                        updatedProgresses.indexOfFirst { (it.batchNum == event.batch && it.roundNum == event.round) }
                    if (idx >= 0) {
                        updatedProgresses[idx] = updatedProgresses[idx].copy(
                            total = event.total,
                            running = event.total
                        )
                    }

                    state.copy(
                        speedTestProgress = current.copy(
                            currentBatch = event.batch,
                            currentRound = event.round,
                            elapsedSeconds = (currentRoundAbsolute - 1) * settings.roundTimeout,
                            isRunning = true,
                            isRoundActive = true,
                            batchProgresses = updatedProgresses
                        )
                    )
                }
            }

            is SpeedTestEvent.Progress -> {
                _uiState.update { state ->
                    val current = state.speedTestProgress
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

                    state.copy(speedTestProgress = current.copy(batchProgresses = updatedProgresses))
                }
            }

            is SpeedTestEvent.RoundEnded -> {
                _uiState.update { it.copy(speedTestProgress = it.speedTestProgress.copy(isRoundActive = false)) }
            }

            is SpeedTestEvent.Error -> {
                viewModelScope.launch {
                    val msg = getString(Res.string.msg_test_error, event.message)
                    module.platform.showToast(msg)
                }
                stopTest(AppStatus.ERROR, event.message)
            }
        }
    }

    fun stopTest(newAppStatus: AppStatus = AppStatus.STOPPED, description: String? = null) {
        testJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            module.testManager.stopTests()
            module.appStatusManager.updateStatus(newAppStatus, description)
        }
    }

    private suspend fun getWorkingConfigsString(): String {
        val settings = module.settingsRepository.settings.value
        var configs = module.subscriptionRepository.getWorkingConfigs(settings.performSpeedTest)

        if (settings.sortProfilesByDelay && !settings.performSpeedTest) {
            configs = configs.sortedWith(compareBy<ProxyConfig> { it.delay }.thenBy { it.tag })
        } else if (settings.performSpeedTest) {
            configs = configs.sortedWith(compareByDescending<ProxyConfig> { it.speed }.thenBy { it.tag })
        }

        return configs.joinToString("\n") { it.connURI }
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

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
        timerJob?.cancel()
    }
}

data class HomeScreenUiState(
    val latencyTestProgress: LatencyTestProgress = LatencyTestProgress(),
    val speedTestProgress: SpeedTestProgress = SpeedTestProgress()
)
