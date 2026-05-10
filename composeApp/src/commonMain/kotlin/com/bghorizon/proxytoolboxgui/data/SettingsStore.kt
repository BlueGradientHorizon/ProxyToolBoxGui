package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.data.db.AppSettingsEntity
import com.bghorizon.proxytoolboxgui.data.db.SettingsDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val dao: SettingsDao) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    suspend fun loadSettings() {
        val entity = dao.getSettings()
        if (entity == null) {
            // Save defaults first
            dao.saveSettings(AppSettings().toEntity())
            // Then re-read from database
            val reReadEntity = dao.getSettings()
            _settings.value = reReadEntity?.toModel() ?: AppSettings()
        } else {
            _settings.value = entity.toModel()
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        dao.saveSettings(settings.toEntity())
        _settings.value = settings
    }

    suspend fun updateTheme(theme: ThemeMode) {
        saveSettings(_settings.value.copy(theme = theme))
    }

    suspend fun updateSelectedWorker(name: String, path: String) {
        saveSettings(_settings.value.copy(selectedWorkerName = name, selectedWorker = path))
    }
}

private fun AppSettings.toEntity() = AppSettingsEntity(
    theme = theme.ordinal,
    selectedWorker = selectedWorker,
    selectedWorkerName = selectedWorkerName,
    downloadTimeout = downloadTimeout,
    performDedup = performDedup,
    latencyRounds = latencyRounds,
    roundTimeout = roundTimeout,
    testByBatches = testByBatches,
    batchSize = batchSize,
    autoStartWebServer = autoStartWebServer,
    webServerPort = webServerPort,
    webServerLocalhost = webServerLocalhost,
    testUrl = testUrl,
    parallelSubscriptionDownloads = parallelSubscriptionDownloads
)

private fun AppSettingsEntity.toModel() = AppSettings(
    theme = ThemeMode.entries.getOrElse(theme) { ThemeMode.SYSTEM },
    selectedWorker = selectedWorker,
    selectedWorkerName = selectedWorkerName,
    downloadTimeout = downloadTimeout,
    performDedup = performDedup,
    latencyRounds = latencyRounds,
    roundTimeout = roundTimeout,
    testByBatches = testByBatches,
    batchSize = batchSize,
    autoStartWebServer = autoStartWebServer,
    webServerPort = webServerPort,
    webServerLocalhost = webServerLocalhost,
    testUrl = testUrl,
    parallelSubscriptionDownloads = parallelSubscriptionDownloads
)
