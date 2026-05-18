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
    private external fun nativeInitializeRunner(
        workerPath: String,
        callback: JniErrorCallbackWrapper
    )

    @JvmStatic
    private external fun nativeParseConfigs(
        connUrisJson: String,
        callback: JniParseCallbackWrapper
    )

    @JvmStatic
    private external fun nativeValidateConfigs(
        callback: JniValidateCallbackWrapper,
    )

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

    actual fun initializeRunner(workerPath: String, callback: GoErrorCallback) {
        nativeInitializeRunner(workerPath, JniErrorCallbackWrapper(callback))
    }

    actual fun parseConfigs(connUrisJson: String, callback: GoParseCallback) {
        nativeParseConfigs(connUrisJson, JniParseCallbackWrapper(callback))
    }

    actual fun validateConfigs(callback: GoValidateCallback) {
        nativeValidateConfigs(JniValidateCallbackWrapper(callback))
    }

    actual fun runLatencyTests(
        testUrl: String,
        settings: AppSettings,
        callback: GoTestCallback
    ): List<ProxyConfig> {
        val wrapper = JniTestCallbackWrapper(callback)

        val resultJson = nativeRunLatencyTests(
            testUrl,
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
