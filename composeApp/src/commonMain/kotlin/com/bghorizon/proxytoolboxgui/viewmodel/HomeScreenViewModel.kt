package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.*
import com.bghorizon.proxytoolboxgui.data.db.ConfigTestResultUpdate
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
        onTestCompleted: () -> Unit = {}
    ) {
        if (_uiState.value.workers.isEmpty() || testJob?.isActive == true) return

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
                _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    appStatus = AppStatus.TESTING,
                    testProgress = it.testProgress.copy(isRunning = true)
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
                    _uiState.update { it.copy(appStatus = AppStatus.IDLE) }
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
                            }
                        )
                    )
                }

                val resultConfigs = module.testManager.runLatencyTests(
                    settings = currentSettings,
                    configs = setup.configs,
                    onEvent = { event ->
                        if (job?.isActive != true) return@runLatencyTests
                        handleTestEvent(event, currentSettings)
                    }
                )

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

                _uiState.update {
                    it.copy(appStatus = if (resultConfigs.isNotEmpty()) AppStatus.COMPLETED else AppStatus.IDLE)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(appStatus = AppStatus.ERROR) }
                e.printStackTrace()
            } finally {
                withContext(NonCancellable) {
                    timerJob?.cancel()
                    _uiState.update {
                        it.copy(
                            testProgress = it.testProgress.copy(
                                isRunning = false,
                                isRoundActive = false
                            )
                        )
                    }
                    onTestCompleted()
                }
            }
        }
    }

    private fun handleTestEvent(event: TestEvent, settings: AppSettings) {
        when (event) {
            is TestEvent.ParseFailed -> {
                //event.errors.forEach { (tag, error) -> println("$tag $error") }
                viewModelScope.launch(Dispatchers.IO) {
                    module.subscriptionRepository.resetParseErrorData()
                    val batch = event.errors.keys.mapNotNull { tag ->
                        module.testManager.extractIds(tag)
                    }
                    module.subscriptionRepository.markConfigsParseErrBatch(batch)
                }
            }

            is TestEvent.ValidateFailed -> {
                //event.errors.forEach { (tag, error) -> println("$tag $error") }
                viewModelScope.launch(Dispatchers.IO) {
                    module.subscriptionRepository.resetValidErrorData()
                    val batch = event.errors.keys.mapNotNull { tag ->
                        module.testManager.extractIds(tag)
                    }
                    module.subscriptionRepository.markConfigsValidErrBatch(batch)
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
                        updatedProgresses[idx] = updatedProgresses[idx].copy(
                            total = event.total,
                            running = event.total
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
                    val msg = getString(Res.string.msg_test_error, event.message)
                    module.platform.showToast(msg)
                }
                stopTest(AppStatus.ERROR)
            }
        }
    }

    fun stopTest(newAppStatus: AppStatus = AppStatus.IDLE) {
        testJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            module.testManager.stopTests()
            _uiState.update { it.copy(appStatus = newAppStatus) }
        }
    }

    fun setWorkers(workers: List<WorkerInfo>) {
        _uiState.update { it.copy(workers = workers) }
    }

    private suspend fun getWorkingConfigsString(): String {
        val settings = module.settingsRepository.settings.value
        var configs = module.subscriptionRepository.getWorkingConfigs()

        if (settings.sortProfilesByDelay) {
            configs = configs.sortedWith(compareBy<ProxyConfig> { it.delay }.thenBy { it.tag })
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
            val dmy = "${day}${month}${now.year}"

            val hour = now.hour.toString().padStart(2, '0')
            val minute = now.minute.toString().padStart(2, '0')
            val second = now.second.toString().padStart(2, '0')
            val hms = "${hour}${minute}${second}"
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
    val testProgress: TestProgress = TestProgress(),
    val appStatus: AppStatus = AppStatus.IDLE,
    val workers: List<WorkerInfo> = emptyList()
)
