package com.bghorizon.proxytoolboxgui.data

import jnr.ffi.LibraryLoader
import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.annotations.Delegate
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface CbParseFailed { @Delegate fun invoke(tagsJson: Pointer?) }
interface CbValidateFailed { @Delegate fun invoke(tagsJson: Pointer?) }
interface CbRoundStarted { @Delegate fun invoke(batchNum: Int, roundNum: Int, total: Int) }
interface CbProgress { @Delegate fun invoke(tag: Pointer?, delay: Long, failed: Int) }
interface CbRoundEnded { @Delegate fun invoke(batchNum: Int, roundNum: Int) }

interface GoLibrary {
    fun DiscoverWorkers(libraryPath: String): Pointer
    fun RunLatencyTests(
        workerPath: String,
        connUrisJson: String,
        performDedup: Int,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Int,
        batchSize: Int,
        cbParseFailed: CbParseFailed,
        cbValidateFailed: CbValidateFailed,
        cbRoundStarted: CbRoundStarted,
        cbProgress: CbProgress,
        cbRoundEnded: CbRoundEnded
    ): Pointer
    fun FreeString(ptr: Pointer)
}

object NativeLoader {
    val lib: GoLibrary
    val runtime: Runtime

    init {
        val loader = LibraryLoader.create(GoLibrary::class.java)
        lib = loader.load("wrapper")
        runtime = Runtime.getRuntime(lib)
    }
}

actual object GoBridge {
    actual fun discoverWorkers(libraryPath: String): String {
        val ptr = NativeLoader.lib.DiscoverWorkers(libraryPath)
        val result = ptr.getString(0) ?: "[]"
        NativeLoader.lib.FreeString(ptr)
        return result
    }

    actual fun runLatencyTests(
        workerPath: String,
        settings: AppSettings,
        callback: GoTestCallback,
        connUris: List<ProxyConfig>,
        performDedup: Boolean
    ): List<ProxyConfig> {
        val connUrisJson = JsonConfig.json.encodeToString(connUris)
        
        val ptr = NativeLoader.lib.RunLatencyTests(
            workerPath,
            connUrisJson,
            if (performDedup) 1 else 0,
            settings.latencyRounds,
            settings.roundTimeout,
            if (settings.testByBatches) 1 else 0,
            settings.batchSize,
            object : CbParseFailed {
                override fun invoke(tagsJson: Pointer?) {
                    val json = tagsJson?.getString(0) ?: ""
                    val tags = parseTagsJson(json)
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onParseFailed(tags)
                    }
                }
            },
            object : CbValidateFailed {
                override fun invoke(tagsJson: Pointer?) {
                    val json = tagsJson?.getString(0) ?: ""
                    val tags = parseTagsJson(json)
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onValidateFailed(tags)
                    }
                }
            },
            object : CbRoundStarted {
                override fun invoke(batchNum: Int, roundNum: Int, total: Int) {
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onRoundStarted(batchNum.toLong(), roundNum.toLong(), total.toLong())
                    }
                }
            },
            object : CbProgress {
                override fun invoke(tag: Pointer?, delay: Long, failed: Int) {
                    val tagStr = tag?.getString(0) ?: ""
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onProgress(tagStr, delay, failed != 0)
                    }
                }
            },
            object : CbRoundEnded {
                override fun invoke(batchNum: Int, roundNum: Int) {
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onRoundEnded(batchNum.toLong(), roundNum.toLong())
                    }
                }
            }
        )

        val resultJson = ptr.getString(0) ?: "[]"
        NativeLoader.lib.FreeString(ptr)
        
        return try {
            JsonConfig.json.decodeFromString<List<ProxyConfig>>(resultJson)
        } catch (e: Exception) {
            emptyList()
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