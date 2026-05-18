package com.bghorizon.proxytoolboxgui.data

import android.util.Log
import com.bghorizon.proxytoolboxgui.AppContext

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
    private external fun nativeRunLatencyTests(
        workerPath: String,
        testUrl: String,
        connUrisJson: String,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniCallbackWrapper,
    ): String

    @JvmStatic
    private external fun nativeStopTests()

    actual fun discoverWorkers(libraryPath: String): String {
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
        val result = nativeDiscoverWorkers(libraryPath)
        Log.d(TAG, "discoverWorkers: result length=${result.length}")
        return result
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    actual fun stopTests() {
        nativeStopTests()
    }
}
