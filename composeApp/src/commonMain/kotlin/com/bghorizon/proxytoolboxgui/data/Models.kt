package com.bghorizon.proxytoolboxgui.data

import kotlinx.serialization.Serializable

@Serializable
data class WorkerInfo(
    val name: String,
    val version: String,
    val path: String,
)

@Serializable
data class Subscription(
    val id: String,
    val note: String,
    val url: String,
    val total: Int = 0,
    val working: Int = 0,
    val speedPassed: Int = 0,
    val updatedAt: Long = 0L,
    val duplicated: Int = 0,
    val parseErr: Int = 0,
    val validErr: Int = 0
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
    val speedBatchProgresses: List<BatchProgress> = emptyList(),
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isRoundActive: Boolean = false
)

@Serializable
data class SubsUpdateProgress(
    val total: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val isRunning: Boolean = false
)

@Serializable
data class ProxyConfig(
    val tag: String,
    val connURI: String,
    val delay: Long = -1,
    val speed: Long = -1
)

@Serializable
data class SpeedTestResult(
    val tag: String,
    val speed: Double, // bytes/s
    val error: String? = null
)

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val selectedWorker: String = "", // runtime
    val selectedWorkerName: String = "", // runtime
    val downloadTimeout: Int = 10,
    val performDedup: Boolean = true,
    val latencyRounds: Int = 2,
    val roundTimeout: Int = 10,
    val testByBatches: Boolean = true,
    val batchSize: Int = 2500,
    val autoStartWebServer: Boolean = true,
    val webServerPort: Int = 35240,
    val webServerLocalhost: Boolean = true,
    val testUrl: String = "https://www.google.com/generate_204",
    val parallelSubscriptionDownloads: Int = 5,
    val lowMemMode: Boolean = false,
    val sortProfilesByDelay: Boolean = false,
    val performSpeedTests: Boolean = true,
    val speedTestRounds: Int = 1,
    val speedTestProviderId: String = "", // runtime
    val speedTestMode: String = "download",
    val speedTestTargetBytes: Long = 1024,
    val sortByLatencyDelay: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class AppLanguage {
    SYSTEM, ENGLISH, RUSSIAN
}

enum class AppStatus {
    IDLE, UPDATING_SUBS, PARSING, VALIDATING, TESTING, COMPLETED, STOPPED, ERROR
}
