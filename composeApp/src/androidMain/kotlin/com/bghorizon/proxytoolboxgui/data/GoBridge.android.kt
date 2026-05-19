package com.bghorizon.proxytoolboxgui.data

import android.util.Log
import com.bghorizon.proxytoolboxgui.AppContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

actual object GoBridge {
    private const val TAG = "GoBridge"

    init {
        Log.d(TAG, "Loading wrapper library...")
        try {
            val libDir =
                AppContext.context.applicationInfo.nativeLibraryDir
            val libName = System.mapLibraryName("wrapper")
            val absolutePath = java.io.File(libDir, libName).absolutePath
            Log.d(TAG, "Absolute path to library: $absolutePath")

            System.loadLibrary("wrapper")
            Log.d(TAG, "wrapper library loaded successfully from $absolutePath")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load wrapper library", e)
        }
    }

    @JvmStatic
    private external fun nativeDiscoverWorkers(libraryPath: String): String

    @JvmStatic
    private external fun nativeInitializeRunner(workerPath: String): String

    @JvmStatic
    private external fun nativeParseConfigs(connUrisJson: String): String

    @JvmStatic
    private external fun nativeValidateConfigs(): String

    @JvmStatic
    private external fun nativeRunLatencyTests(
        testUrl: String,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniTestCallbackWrapper,
    ): String

    @JvmStatic
    private external fun nativeStopTests()

    @Serializable
    private data class NativeResponse(
        @SerialName("data") val data: String = "",
        @SerialName("error") val error: String? = null
    )

    private fun parseNativeResponse(json: String): NativeResponse {
        return JsonConfig.json.decodeFromString(json)
    }

    actual fun discoverWorkers(libraryPath: String): List<WorkerInfo> {
        Log.d(TAG, "discoverWorkers: libraryPath=$libraryPath")
        try {
            val dir = java.io.File(libraryPath)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()
                Log.d(TAG, "Files in libraryPath: ${files?.joinToString { it.name } ?: "null"}")
            } else {
                Log.w(TAG, "libraryPath does not exist or is not a directory")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files in libraryPath", e)
        }
        val responseJson = nativeDiscoverWorkers(libraryPath)
        Log.d(TAG, "discoverWorkers: result length=${responseJson.length}")
        val response = parseNativeResponse(responseJson)
        if (response.error != "") throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    actual fun initializeRunner(workerPath: String) {
        val responseJson = nativeInitializeRunner(workerPath)
        val response = parseNativeResponse(responseJson)
        if (response.error != "") throw Exception(response.error)
    }

    actual fun parseConfigs(connUrisJson: String): Map<String, String> {
        val responseJson = nativeParseConfigs(connUrisJson)
        val response = parseNativeResponse(responseJson)
        if (response.error != "") throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    actual fun validateConfigs(): Map<String, String> {
        val responseJson = nativeValidateConfigs()
        val response = parseNativeResponse(responseJson)
        if (response.error != "") throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    actual fun runLatencyTests(
        testUrl: String,
        settings: AppSettings,
        callback: GoTestCallback
    ): List<ProxyConfig> {
        val wrapper = JniTestCallbackWrapper(callback)

        val responseJson = nativeRunLatencyTests(
            testUrl,
            settings.latencyRounds,
            settings.roundTimeout,
            settings.testByBatches,
            settings.batchSize,
            wrapper
        )

        val response = parseNativeResponse(responseJson)
        if (response.error != "") throw Exception(response.error)
        return JsonConfig.json.decodeFromString(response.data)
    }

    actual fun stopTests() {
        nativeStopTests()
    }
}