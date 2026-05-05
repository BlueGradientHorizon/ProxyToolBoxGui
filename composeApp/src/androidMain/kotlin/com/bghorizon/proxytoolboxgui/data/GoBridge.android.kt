package com.bghorizon.proxytoolboxgui.data

import kotlinx.serialization.json.Json
import wrapper.Wrapper

actual object GoBridge {
    private val wrapper = Wrapper()

    actual fun discoverWorkers(libraryPath: String): String {
        return wrapper.discoverWorkers(libraryPath)
    }

    actual fun parseConfigs(configStrings: List<String>, performDedup: Boolean): GoParseResult {
        val result = wrapper.parseConfigs(configStrings.toTypedArray(), performDedup)
        val parsed = JsonConfig.json.decodeFromString<ParseResult>(result)
        return GoParseResult(parsed.configsJson, parsed.duplicatedCount, parsed.parseErrorCount)
    }

    actual fun validateConfigs(workerPath: String, configsJson: String): GoValidateResult {
        val result = wrapper.validateConfigs(workerPath, configsJson)
        val parsed = JsonConfig.json.decodeFromString<ValidateResult>(result)
        return GoValidateResult(parsed.configsJson, parsed.validationErrorCount)
    }

    actual fun runLatencyTests(
        workerPath: String,
        configsJson: String,
        settings: AppSettings,
        callback: GoTestCallback
    ): String {
        val settingsJson = JsonConfig.json.encodeToString(
            LatencyTestSettings(
                performDedup = settings.performDedup,
                latencyRounds = settings.latencyRounds,
                roundTimeout = settings.roundTimeout,
                testByBatches = settings.testByBatches,
                batchSize = settings.batchSize
            )
        )
        return wrapper.runLatencyTests(workerPath, configsJson, settingsJson, object : wrapper.TestCallback {
            override fun onRoundStarted(batchNum: Int, roundNum: Int, total: Int) {
                callback.onRoundStarted(batchNum, roundNum, total)
            }

            override fun onProgress(tag: String, delay: Long, failed: Boolean) {
                callback.onProgress(tag, delay, failed)
            }

            override fun onRoundEnded(batchNum: Int, roundNum: Int) {
                callback.onRoundEnded(batchNum, roundNum)
            }
        })
    }

    @kotlinx.serialization.Serializable
    private data class ParseResult(
        val configsJson: String,
        val duplicatedCount: Int,
        val parseErrorCount: Int
    )

    @kotlinx.serialization.Serializable
    private data class ValidateResult(
        val configsJson: String,
        val validationErrorCount: Int
    )

    @kotlinx.serialization.Serializable
    private data class LatencyTestSettings(
        val performDedup: Boolean,
        val latencyRounds: Int,
        val roundTimeout: Int,
        val testByBatches: Boolean,
        val batchSize: Int
    )
}
