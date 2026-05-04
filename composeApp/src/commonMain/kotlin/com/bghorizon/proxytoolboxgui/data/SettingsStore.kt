package com.bghorizon.proxytoolboxgui.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

expect class SettingsStore {
    suspend fun getString(key: String, default: String = ""): String
    suspend fun putString(key: String, value: String)
    suspend fun getInt(key: String, default: Int = 0): Int
    suspend fun putInt(key: String, value: Int)
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean
    suspend fun putBoolean(key: String, value: Boolean)
    suspend fun getLong(key: String, default: Long = 0L): Long
    suspend fun putLong(key: String, value: Long)
}

class SettingsRepository(private val store: SettingsStore) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    suspend fun loadSettings() {
        val themeOrdinal = store.getInt("theme", 2)
        _settings.value = AppSettings(
            theme = ThemeMode.entries.getOrElse(themeOrdinal) { ThemeMode.SYSTEM },
            selectedWorker = store.getString("selected_worker", ""),
            downloadTimeout = store.getInt("download_timeout", 10),
            performDedup = store.getBoolean("perform_dedup", true),
            latencyRounds = store.getInt("latency_rounds", 3),
            roundTimeout = store.getInt("round_timeout", 10),
            testByBatches = store.getBoolean("test_by_batches", true),
            batchSize = store.getInt("batch_size", 5000),
            autoStartWebServer = store.getBoolean("auto_start_web_server", true),
            webServerPort = store.getInt("web_server_port", 35240),
            webServerLocalhost = store.getBoolean("web_server_localhost", true)
        )
    }

    suspend fun saveSettings(settings: AppSettings) {
        store.putInt("theme", settings.theme.ordinal)
        store.putString("selected_worker", settings.selectedWorker)
        store.putInt("download_timeout", settings.downloadTimeout)
        store.putBoolean("perform_dedup", settings.performDedup)
        store.putInt("latency_rounds", settings.latencyRounds)
        store.putInt("round_timeout", settings.roundTimeout)
        store.putBoolean("test_by_batches", settings.testByBatches)
        store.putInt("batch_size", settings.batchSize)
        store.putBoolean("auto_start_web_server", settings.autoStartWebServer)
        store.putInt("web_server_port", settings.webServerPort)
        store.putBoolean("web_server_localhost", settings.webServerLocalhost)
        _settings.value = settings
    }

    suspend fun updateTheme(theme: ThemeMode) {
        saveSettings(_settings.value.copy(theme = theme))
    }

    suspend fun updateSelectedWorker(worker: String) {
        saveSettings(_settings.value.copy(selectedWorker = worker))
    }
}