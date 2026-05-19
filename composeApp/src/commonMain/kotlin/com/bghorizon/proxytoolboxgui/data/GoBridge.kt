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
    fun nativeInitializeRunner(workerPath: String): String
    fun nativeParseConfigs(connUrisJson: String): String
    fun nativeValidateConfigs(): String
    fun nativeRunLatencyTests(
        testUrl: String,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniTestCallbackWrapper,
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
        callback: GoTestCallback,
    ): List<ProxyConfig> {
        val wrapper = JniTestCallbackWrapper(callback)

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

    fun stopTests() {
        GoBridgeNative.nativeStopTests()
    }
}

interface GoTestCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}
