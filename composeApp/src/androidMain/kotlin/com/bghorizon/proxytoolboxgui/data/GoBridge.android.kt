package com.bghorizon.proxytoolboxgui.data

import wrapper.Wrapper
import wrapper.TestCallback
import wrapper.LatencyTestSettings

actual object GoBridge {
    actual fun discoverWorkers(libraryPath: String): String {
        return Wrapper.discoverWorkers(libraryPath)
    }

    actual fun parseConfigs(configStrings: List<String>, performDedup: Boolean): GoParseResult {
        val result = Wrapper.parseConfigs(configStrings.toTypedArray(), performDedup)
        return GoParseResult(
            configsJson = result.configsJson,
            duplicatedCount = result.duplicatedCount.toInt(),
            parseErrorCount = result.parseErrorCount.toInt()
        )
    }

    actual fun validateConfigs(workerPath: String, configsJson: String): GoValidateResult {
        val result = Wrapper.validateConfigs(workerPath, configsJson)
        return GoValidateResult(
            configsJson = result.configsJson,
            validationErrorCount = result.validationErrorCount.toInt()
        )
    }

    actual fun runLatencyTests(
        workerPath: String,
        configsJson: String,
        settings: AppSettings,
        callback: GoTestCallback
    ): String {
        val s = LatencyTestSettings()
        s.performDedup = settings.performDedup
        s.latencyRounds = settings.latencyRounds.toLong()
        s.roundTimeout = settings.roundTimeout.toLong()
        s.testByBatches = settings.testByBatches
        s.batchSize = settings.batchSize.toLong()
        return Wrapper.runLatencyTests(workerPath, configsJson, s, object : TestCallback {
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
    }
}