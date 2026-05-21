package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import proxytoolboxgui.composeapp.generated.resources.*

class RuntimeSettingsManager(
    private val settingsRepository: SettingsRepository,
    private val appStatusManager: AppStatusManager,
    private val platform: Platform,
) {
    private val _workers = MutableStateFlow<List<WorkerInfo>>(emptyList())
    val workers = _workers.asStateFlow()

    private val _speedTestPresets = MutableStateFlow<Map<String, String>>(emptyMap())
    val speedTestPresets = _speedTestPresets.asStateFlow()

    suspend fun discoverWorkers() = withContext(Dispatchers.IO) {
        try {
            val libraryPath = platform.getWorkerLibraryPath()
            val workers = GoBridge.discoverWorkers(libraryPath)

            val hasWorkers = workers.isNotEmpty()
            val currentStatus = appStatusManager.statusInfo.value.status

            val newStatus = if (!hasWorkers) {
                AppStatus.ERROR
            } else if (currentStatus == AppStatus.ERROR) {
                AppStatus.IDLE
            } else {
                currentStatus
            }

            val description = if (!hasWorkers) getString(Res.string.no_workers_found) else null
            appStatusManager.updateStatus(newStatus, description)

            _workers.value = workers

            val currentSettings = settingsRepository.settings.value
            val savedName = currentSettings.selectedWorkerName
            val savedPath = currentSettings.selectedWorker

            val matchedWorker = workers.find { it.path == savedPath }
                ?: workers.find { it.name == savedName }
                ?: if (workers.isNotEmpty()) workers[0] else null

            if (matchedWorker != null && ((matchedWorker.path != savedPath) || savedName.isBlank())) {
                settingsRepository.updateSelectedWorker(
                    matchedWorker.name,
                    matchedWorker.path
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            appStatusManager.updateStatus(AppStatus.ERROR, e.message)
        }
    }

    suspend fun discoverSpeedtestPresets() = withContext(Dispatchers.IO) {
        try {
            val presets = GoBridge.discoverSpeedTestPresets()
            _speedTestPresets.value = presets

            val currentSettings = settingsRepository.settings.value
            val savedProvider = currentSettings.speedTestProviderId

            val matchedProvider = if (presets.containsKey(savedProvider)) {
                savedProvider
            } else if (presets.isNotEmpty()) {
                presets.keys.first()
            } else {
                null
            }

            if (matchedProvider != null && matchedProvider != savedProvider) {
                settingsRepository.updateSelectedSpeedTestProvider(matchedProvider)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            appStatusManager.updateStatus(AppStatus.ERROR, e.message)
        }
    }
}
