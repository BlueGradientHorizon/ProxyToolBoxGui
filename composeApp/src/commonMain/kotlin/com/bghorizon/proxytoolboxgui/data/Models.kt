package com.bghorizon.proxytoolboxgui.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

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
    val workingSpeed: Int = 0,
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

interface BaseTestProgress {
    val phase: Int
    val currentBatch: Int
    val totalBatches: Int
    val currentRound: Int
    val totalRounds: Int
    val batchProgresses: List<BatchProgress>
    val elapsedSeconds: Int
    val totalSeconds: Int
    val isRunning: Boolean
    val isRoundActive: Boolean
}

@Serializable
data class LatencyTestProgress(
    override val phase: Int = 0,
    override val currentBatch: Int = 0,
    override val totalBatches: Int = 0,
    override val currentRound: Int = 0,
    override val totalRounds: Int = 0,
    override val batchProgresses: List<BatchProgress> = emptyList(),
    override val elapsedSeconds: Int = 0,
    override val totalSeconds: Int = 0,
    override val isRunning: Boolean = false,
    override val isRoundActive: Boolean = false
) : BaseTestProgress

@Serializable
data class SpeedTestProgress(
    override val phase: Int = 0,
    override val currentBatch: Int = 0,
    override val totalBatches: Int = 0,
    override val currentRound: Int = 0,
    override val totalRounds: Int = 0,
    override val batchProgresses: List<BatchProgress> = emptyList(),
    override val elapsedSeconds: Int = 0,
    override val totalSeconds: Int = 0,
    override val isRunning: Boolean = false,
    override val isRoundActive: Boolean = false
) : BaseTestProgress

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
    val speed: Double = 0.0
)

@Serializable
data class SpeedTestPreset(
    val id: String,
    val name: String
)

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val selectedWorker: String = "",
    val selectedWorkerName: String = "",
    val downloadTimeout: Int = 10,
    val performDedup: Boolean = true,
    val latencyRounds: Int = 3,
    val roundTimeout: Int = 10,
    val testByBatches: Boolean = true,
    val batchSize: Int = 5000,
    val autoStartWebServer: Boolean = true,
    val webServerPort: Int = 35240,
    val webServerLocalhost: Boolean = true,
    val testUrl: String = "https://www.google.com/generate_204",
    val parallelSubscriptionDownloads: Int = 5,
    val sortProfilesByDelay: Boolean = false,
    
    val performSpeedTest: Boolean = true,
    val speedTestRounds: Int = 1,
    val speedTestProvider: String = "cloudflare",
    val speedTestMode: String = "download",
    val speedTestTargetBytes: Int = 1048576,
    val sortByLatencyDelay: Boolean = false
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class AppStatus {
    IDLE, UPDATING_SUBS, PARSING, VALIDATING, TESTING, SPEED_TESTING, COMPLETED, STOPPED, ERROR
}

object JsonConfig {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
}
