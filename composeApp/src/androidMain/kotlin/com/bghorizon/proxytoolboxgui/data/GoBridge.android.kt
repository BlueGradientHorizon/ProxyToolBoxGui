package com.bghorizon.proxytoolboxgui.data

import wrapper.Wrapper
import wrapper.TestCallback
import wrapper.LatencyTestSettings
import wrapper.ProxyConfig as ProxyConfigWrapper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object GoBridge {
    actual fun discoverWorkers(libraryPath: String): String {
        return Wrapper.discoverWorkers(libraryPath)
    }

//    actual fun parseConnUris(connUris: List<String>, performDedup: Boolean): ConnUrisParsingResult {
//        val result = Wrapper.parseConnUris(connUris.toTypedArray(), performDedup)
//        return ConnUrisParsingResult(
//            configsJson = result.configsJson,
//            duplicatedCount = result.duplicatedCount.toInt(),
//            parseErrorCount = result.parseErrorCount.toInt()
//        )
//    }
//
//    actual fun validateConfigs(workerPath: String, configsJson: String): ConfigsValidationResult {
//        val result = Wrapper.validateConfigs(workerPath, configsJson)
//        return ConfigsValidationResult(
//            configsJson = result.configsJson,
//            validationErrorCount = result.validationErrorCount.toInt()
//        )
//    }

    actual fun runLatencyTests(
        workerPath: String,
        settings: AppSettings,
        callback: GoTestCallback,
        connUris: List<ProxyConfig>,
        performDedup: Boolean
    ): List<ProxyConfig> {
        val s = LatencyTestSettings()
        s.latencyRounds = settings.latencyRounds.toLong()
        s.roundTimeout = settings.roundTimeout.toLong()
        s.testByBatches = settings.testByBatches
        s.batchSize = settings.batchSize.toLong()
        val connUrisJson = JsonConfig.json.encodeToString(connUris)
        val result = Wrapper.runLatencyTests(workerPath, connUrisJson, performDedup, s, object : TestCallback {
            override fun onParseFailed(p0: String?) {
//                TODO("Not yet implemented")
            }

            override fun onValidateFailed(p0: String?) {
//                TODO("Not yet implemented")
            }

            override fun onRoundStarted(batchNum: Long, roundNum: Long, total: Long) {
                callback.onRoundStarted(batchNum, roundNum, total)
            }

            override fun onProgress(tag: String, delay: Long, failed: Boolean) {
                callback.onProgress(tag, delay, failed)
            }

            override fun onRoundEnded(batchNum: Long, roundNum: Long) {
                callback.onRoundEnded(batchNum, roundNum)
            }
        })

        val wrapped = JsonConfig.json.decodeFromString<List<ProxyConfigWrapper>>(result)
        return wrapped.map {
            ProxyConfig(
                tag = it.tag,
                connURI = it.connURI,
            )
        }
    }
}