package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.*
import com.bghorizon.proxytoolboxgui.data.db.ConfigTestResultUpdate
import com.bghorizon.proxytoolboxgui.di.AppModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
        onTestCompleted: (Boolean) -> Unit = {},
    ) {
        if ((module.runtimeSettingsManager.workers.value.isEmpty()) || (testJob?.isActive == true)) return

        if (appStatus == AppStatus.UPDATING_SUBS) {
            viewModelScope.launch {
                module.platform.showToast(getString(Res.string.msg_cannot_test_while_updating))
            }
            return
        }

        testJob = viewModelScope.launch(Dispatchers.IO) {
            val job = coroutineContext[Job]
            val currentSettings = module.settingsRepository.settings.value
            var success = false

            if (currentSettings.selectedWorker.isBlank()) {
                val msg = getString(Res.string.msg_select_worker)
                withContext(Dispatchers.Main) {
                    module.platform.showToast(msg)
                }
                module.appStatusManager.updateStatus(AppStatus.ERROR)
                return@launch
            }

            module.appStatusManager.updateStatus(AppStatus.PARSING)
            _uiState.update {
                it.copy(
                    testProgress = it.testProgress.copy(isRunning = true),
                )
            }

            try {
                val setup = module.testManager.prepareTest(currentSettings, subscriptions)

                // Persist the calculated 'duplicated' counts and reset baseline errors in DB
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
                        testProgress = it.testProgress.copy(
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
                            speedBatchProgresses = if (currentSettings.performSpeedTests) {
                                (1..currentSettings.speedTestRounds).map { r ->
                                    BatchProgress(batchNum = 1, roundNum = r)
                                }
                            } else emptyList(),
                        )
                    )
                }

                module.testManager.initializeRunner(
                    workerPath = currentSettings.selectedWorker,
                    lowMemMode = currentSettings.lowMemMode
                )

                module.appStatusManager.updateStatus(AppStatus.PARSING)
                val parseErrors = module.testManager.parseConfigs(setup.configs)
                if (parseErrors.isNotEmpty()) {
                    module.subscriptionRepository.resetParseErrorData()
                    val batch = parseErrors.keys.mapNotNull { tag ->
                        module.testManager.extractIds(tag)
                    }
                    module.subscriptionRepository.markConfigsParseErrBatch(batch)
                }

                module.appStatusManager.updateStatus(AppStatus.VALIDATING)
                val validErrors = module.testManager.validateConfigs()
                if (validErrors.isNotEmpty()) {
                    module.subscriptionRepository.resetValidErrorData()
                    val batch = validErrors.keys.mapNotNull { tag ->
                        module.testManager.extractIds(tag)
                    }
                    module.subscriptionRepository.markConfigsValidErrBatch(batch)
                }

                val resultConfigs = module.testManager.runLatencyTests(
                    settings = currentSettings
                ) { event ->
                    if (job?.isActive != true) return@runLatencyTests
                    handleTestEvent(event, currentSettings)
                }

                if (job?.isActive != true) return@launch

                module.subscriptionRepository.resetWorkingData()

                // Save working configs
                val updates = resultConfigs.mapNotNull { cfg ->
                    module.testManager.extractIds(cfg.tag)?.let { (subId, configId) ->
                        ConfigTestResultUpdate(
                            subId = subId,
                            configId = configId,
                            working = true,
                            fixedUri = cfg.connURI,
                            delay = cfg.delay
                        )
                    }
                }
                module.subscriptionRepository.updateConfigTestResultsBatch(updates)

                if (currentSettings.performSpeedTests && resultConfigs.isNotEmpty()) {
                    val workingTags = resultConfigs.map { it.tag }
                    val speedResults = module.testManager.runSpeedTests(
                        workingTags = workingTags,
                        settings = currentSettings
                    ) { event ->
                        if (!job.isActive) return@runSpeedTests
                        handleSpeedTestEvent(event, currentSettings, setup)
                    }

                    if (job.isActive) {
                        val speedUpdates = speedResults.mapNotNull { res ->
                            module.testManager.extractIds(res.tag)?.let { (subId, configId) ->
                                ConfigTestResultUpdate(
                                    subId = subId,
                                    configId = configId,
                                    speed = res.speed.toLong()
                                )
                            }
                        }
                        module.subscriptionRepository.updateConfigTestResultsBatch(speedUpdates)
                    }
                }

                module.appStatusManager.updateStatus(
                    if (resultConfigs.isNotEmpty()) AppStatus.COMPLETED else AppStatus.IDLE
                )
                success = true
            } catch (e: Exception) {
                if (e is CancellationException) {
                    module.appStatusManager.updateStatus(AppStatus.STOPPED)
                } else {
                    val msg = getString(Res.string.msg_test_error, e.message ?: "Unknown error")
                    withContext(Dispatchers.Main) {
                        module.platform.showToast(msg)
                    }
                    module.appStatusManager.updateStatus(AppStatus.ERROR, e.message)
                    e.printStackTrace()
                }
            } finally {
                withContext(NonCancellable) {
                    timerJob?.cancel()
                    module.testManager.stopTests()
                    _uiState.update {
                        it.copy(
                            testProgress = it.testProgress.copy(
                                isRunning = false,
                                isRoundActive = false
                            )
                        )
                    }
                    onTestCompleted(success)
                }
            }
        }
    }

    private fun handleTestEvent(event: LatencyTestEvent, settings: AppSettings) {
        when (event) {
            is LatencyTestEvent.RoundStarted -> {
                val currentRoundAbsolute =
                    ((event.batch - 1) * settings.latencyRounds) + event.round
                module.appStatusManager.updateStatus(AppStatus.TESTING)
                _uiState.update { state ->
                    val current = state.testProgress
                    val updatedProgresses = current.batchProgresses.toMutableList()
                    val idx =
                        updatedProgresses.indexOfFirst { (it.batchNum == event.batch && it.roundNum == event.round) }
                    if (idx >= 0) {
                        updatedProgresses[idx] = updatedProgresses[idx].copy(
                            total = event.total,
                            running = event.total,
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

            is LatencyTestEvent.Progress -> {
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

            is LatencyTestEvent.RoundEnded -> {
                timerJob?.cancel()
                _uiState.update { it.copy(testProgress = it.testProgress.copy(isRoundActive = false)) }
            }
        }
    }

    private fun handleSpeedTestEvent(
        event: SpeedTestEvent,
        settings: AppSettings,
        setup: TestSetup
    ) {
        when (event) {
            is SpeedTestEvent.RoundStarted -> {
                val currentRoundAbsolute =
                    (settings.latencyRounds * setup.totalBatches) + event.round
                module.appStatusManager.updateStatus(AppStatus.TESTING)
                _uiState.update { state ->
                    val current = state.testProgress
                    val updatedProgresses = current.speedBatchProgresses.toMutableList()
                    val idx =
                        updatedProgresses.indexOfFirst { it.batchNum == event.batch && it.roundNum == event.round }
                    if (idx >= 0) {
                        updatedProgresses[idx] = updatedProgresses[idx].copy(
                            total = event.total,
                            running = event.total,
                        )
                    }

                    state.copy(
                        testProgress = current.copy(
                            currentBatch = event.batch,
                            currentRound = event.round,
                            elapsedSeconds = (currentRoundAbsolute - 1) * settings.roundTimeout,
                            isRunning = true,
                            isRoundActive = true,
                            speedBatchProgresses = updatedProgresses,
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

            is SpeedTestEvent.Progress -> {
                _uiState.update { state ->
                    val current = state.testProgress
                    val updatedProgresses = current.speedBatchProgresses.toMutableList()
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

                    state.copy(testProgress = current.copy(speedBatchProgresses = updatedProgresses))
                }
            }

            is SpeedTestEvent.RoundEnded -> {
                timerJob?.cancel()
                _uiState.update { it.copy(testProgress = it.testProgress.copy(isRoundActive = false)) }
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

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
        timerJob?.cancel()
    }
}

data class HomeScreenUiState(
    val testProgress: TestProgress = TestProgress()
)
