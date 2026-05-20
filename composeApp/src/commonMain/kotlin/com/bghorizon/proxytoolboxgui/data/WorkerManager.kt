package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import proxytoolboxgui.composeapp.generated.resources.*

class WorkerManager(
    private val workerRepository: WorkerRepository,
    private val settingsRepository: SettingsRepository,
    private val appStatusManager: AppStatusManager,
    private val platform: Platform
) {
    suspend fun discover() = withContext(Dispatchers.IO) {
        try {
            val presets = GoBridge.discoverSpeedTestPresets()
            workerRepository.setSpeedTestPresets(presets)

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
            
            workerRepository.setWorkers(workers)

            val currentSettings = settingsRepository.settings.value
            val savedName = currentSettings.selectedWorkerName
            val savedPath = currentSettings.selectedWorker

            val matchedWorker = workers.find { it.path == savedPath }
                ?: workers.find { it.name == savedName }
                ?: if (workers.isNotEmpty()) workers[0] else null

            if (matchedWorker != null && (matchedWorker.path != savedPath || savedName.isBlank())) {
                settingsRepository.updateSelectedWorker(
                    matchedWorker.name,
                    matchedWorker.path
                )
            }
            
            val validProviderId = presets.find { it.id == currentSettings.speedTestProvider }?.id
                ?: if (presets.isNotEmpty()) presets[0].id else currentSettings.speedTestProvider
            if (validProviderId != currentSettings.speedTestProvider) {
                settingsRepository.saveSettings(currentSettings.copy(speedTestProvider = validProviderId))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            appStatusManager.updateStatus(AppStatus.ERROR, e.message)
        }
    }
}
