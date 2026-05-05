package com.bghorizon.proxytoolboxgui.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WorkerInfo(
    val name: String,
    val version: String,
    val path: String
)

@Serializable
data class Subscription(
    val id: String,
    val note: String,
    val url: String,
    val total: Int = 0,
    val working: Int = 0,
    val updatedAt: Long = 0L
)

@Serializable
data class ConfigStats(
    val found: Int = 0,
    val duplicated: Int = 0,
    val parseErr: Int = 0,
    val validErr: Int = 0,
    val working: Int = 0
)

@Serializable
data class BatchProgress(
    val batchNum: Int = 0,
    val roundNum: Int = 0,
    val total: Int = 0,
    val running: Int = 0,
    val failed: Int = 0,
    val succeeded: Int = 0
)

@Serializable
data class TestProgress(
    val phase: Int = 0,
    val currentBatch: Int = 0,
    val totalBatches: Int = 0,
    val currentRound: Int = 0,
    val totalRounds: Int = 0,
    val batchProgresses: List<BatchProgress> = emptyList(),
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isRoundActive: Boolean = false
)

@Serializable
data class DownloadProgress(
    val total: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val isRunning: Boolean = false
)

@Serializable
data class ProxyConfig(
    val tag: String,
    val type: String,
    val server: String,
    val port: Int,
    val connURI: String
)

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val selectedWorker: String = "",
    val downloadTimeout: Int = 10,
    val performDedup: Boolean = true,
    val latencyRounds: Int = 3,
    val roundTimeout: Int = 10,
    val testByBatches: Boolean = true,
    val batchSize: Int = 5000,
    val autoStartWebServer: Boolean = true,
    val webServerPort: Int = 35240,
    val webServerLocalhost: Boolean = true
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class AppStatus {
    IDLE, DOWNLOADING, PARSING, VALIDATING, TESTING, COMPLETED, ERROR
}

object JsonConfig {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}
