package com.bghorizon.proxytoolboxgui.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class NativeResponse(
    @SerialName("data") val data: String = "",
    @SerialName("error") val error: String? = null,
)

private fun parseNativeResponse(json: String): NativeResponse {
    return JsonConfig.json.decodeFromString(json)
}

internal expect object GoBridgeNative {
    fun nativeDiscoverWorkers(libraryPath: String): String
    fun nativeDiscoverSpeedTestPresets(): String
    fun nativeInitializeRunner(workerPath: String): String
    fun nativeParseConfigs(connUrisJson: String): String
    fun nativeValidateConfigs(): String
    fun nativeRunLatencyTests(
        testUrl: String,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniLatencyTestCallbackWrapper,
    ): String
    fun nativeRunSpeedTests(
        connUrisJson: String,
        providerId: String,
        mode: String,
        targetBytes: Long,
        rounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniSpeedTestCallbackWrapper,
    ): String
    fun nativeStopTests()
}

object GoBridge {
    init {
        NativeLoader.init()
    }

    fun discoverWorkers(libraryPath: String): List<WorkerInfo> {
        val responseJson = GoBridgeNative.nativeDiscoverWorkers(libraryPath)
        val response = parseNativeResponse(responseJson)
        if (!response.error.isNullOrEmpty()) throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    fun discoverSpeedTestPresets(): List<SpeedTestPreset> {
        val responseJson = GoBridgeNative.nativeDiscoverSpeedTestPresets()
        val response = parseNativeResponse(responseJson)
        if (!response.error.isNullOrEmpty()) throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    fun initializeRunner(workerPath: String) {
        val responseJson = GoBridgeNative.nativeInitializeRunner(workerPath)
        val response = parseNativeResponse(responseJson)
        if (!response.error.isNullOrEmpty()) throw Exception(response.error)
    }

    fun parseConfigs(connUrisJson: String): Map<String, String> {
        val responseJson = GoBridgeNative.nativeParseConfigs(connUrisJson)
        val response = parseNativeResponse(responseJson)
        if (!response.error.isNullOrEmpty()) throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    fun validateConfigs(): Map<String, String> {
        val responseJson = GoBridgeNative.nativeValidateConfigs()
        val response = parseNativeResponse(responseJson)
        if (!response.error.isNullOrEmpty()) throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    fun runLatencyTests(
        testUrl: String,
        settings: AppSettings,
        callback: GoLatencyTestCallback,
    ): List<ProxyConfig> {
        val wrapper = JniLatencyTestCallbackWrapper(callback)

        val responseJson = GoBridgeNative.nativeRunLatencyTests(
            testUrl,
            settings.latencyRounds,
            settings.roundTimeout,
            settings.testByBatches,
            settings.batchSize,
            wrapper,
        )

        val response = parseNativeResponse(responseJson)
        if (!response.error.isNullOrEmpty()) throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    fun runSpeedTests(
        configs: List<ProxyConfig>,
        settings: AppSettings,
        callback: GoSpeedTestCallback
    ): List<ProxyConfig> {
        val wrapper = JniSpeedTestCallbackWrapper(callback)
        val connUrisJson = JsonConfig.json.encodeToString(configs)
        
        val responseJson = GoBridgeNative.nativeRunSpeedTests(
            connUrisJson,
            settings.speedTestProvider,
            settings.speedTestMode,
            settings.speedTestTargetBytes.toLong(),
            settings.speedTestRounds,
            settings.roundTimeout,
            settings.testByBatches,
            settings.batchSize,
            wrapper
        )

        val response = parseNativeResponse(responseJson)
        if (!response.error.isNullOrEmpty()) throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    fun stopTests() {
        GoBridgeNative.nativeStopTests()
    }
}

interface GoLatencyTestCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}

interface GoSpeedTestCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, speed: Double, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}
