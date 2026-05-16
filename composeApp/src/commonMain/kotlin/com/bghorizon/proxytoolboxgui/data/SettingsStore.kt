package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.data.db.AppSettingsEntity
import com.bghorizon.proxytoolboxgui.data.db.SettingsDao
import com.bghorizon.proxytoolboxgui.platform.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val dao: SettingsDao) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    suspend fun loadSettings(platform: Platform) {
        val entity = dao.getSettings()
        val loadedSettings = if (entity == null) {
            // Initial defaults based on platform
            val defaults = AppSettings(dynamicColor = platform.isDynamicColorSupported)
            dao.saveSettings(defaults.toEntity())
            defaults
        } else {
            val model = entity.toModel()
            // Sanitize persisted settings against current platform capabilities
            if (model.dynamicColor && !platform.isDynamicColorSupported) {
                val sanitized = model.copy(dynamicColor = false)
                dao.saveSettings(sanitized.toEntity())
                sanitized
            } else {
                model
            }
        }
        _settings.value = loadedSettings
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
    dynamicColor = dynamicColor,
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
    parallelSubscriptionDownloads = parallelSubscriptionDownloads,
    sortProfilesByDelay = sortProfilesByDelay,
)

private fun AppSettingsEntity.toModel() = AppSettings(
    theme = ThemeMode.entries.getOrElse(theme) { ThemeMode.SYSTEM },
    dynamicColor = dynamicColor,
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
    parallelSubscriptionDownloads = parallelSubscriptionDownloads,
    sortProfilesByDelay = sortProfilesByDelay,
)
