package com.bghorizon.proxytoolboxgui.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private class JniCallbackWrapper(val delegate: GoTestCallback) {
    fun onParseFailedJson(json: String) {
        val tags = parseTagsJson(json)
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onParseFailed(tags)
        }
    }

    fun onValidateFailedJson(json: String) {
        val tags = parseTagsJson(json)
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onValidateFailed(tags)
        }
    }

    fun onRoundStarted(batch: Long, round: Long, total: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onRoundStarted(batch, round, total)
        }
    }

    fun onProgress(tag: String, delay: Long, failed: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onProgress(tag, delay, failed)
        }
    }

    fun onRoundEnded(batch: Long, round: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onRoundEnded(batch, round)
        }
    }

    fun onError(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onError(message)
        }
    }

    private fun parseTagsJson(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            JsonConfig.json.decodeFromString<List<String>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

actual object GoBridge {
    init {
        System.loadLibrary("wrapper")
    }

    @JvmStatic
    private external fun nativeDiscoverWorkers(libraryPath: String): String

    @JvmStatic
    private external fun nativeRunLatencyTests(
        workerPath: String,
        testUrl: String,
        connUrisJson: String,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniCallbackWrapper
    ): String

    @JvmStatic
    private external fun nativeStopTests()

    actual fun discoverWorkers(libraryPath: String): String {
        return nativeDiscoverWorkers(libraryPath)
    }

    actual fun runLatencyTests(
        workerPath: String,
        testUrl: String,
        settings: AppSettings,
        callback: GoTestCallback,
        connUris: List<ProxyConfig>
    ): List<ProxyConfig> {
        val connUrisJson = JsonConfig.json.encodeToString(connUris)
        val wrapper = JniCallbackWrapper(callback)

        val resultJson = nativeRunLatencyTests(
            workerPath,
            testUrl,
            connUrisJson,
            settings.latencyRounds,
            settings.roundTimeout,
            settings.testByBatches,
            settings.batchSize,
            wrapper
        )

        return try {
            JsonConfig.json.decodeFromString<List<ProxyConfig>>(resultJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual fun stopTests() {
        nativeStopTests()
    }
}
