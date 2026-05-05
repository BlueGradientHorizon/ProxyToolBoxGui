package com.bghorizon.proxytoolboxgui.data

import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

actual object GoBridge {
    private fun findWrapperBinary(): String {
        val jarPath = File(System.getProperty("java.class.path").split(File.pathSeparator)[0])
        val binDir = if (jarPath.isFile) jarPath.parentFile else File(System.getProperty("user.dir"))
        val wrapper = File(binDir, "wrapper")
        if (wrapper.exists()) return wrapper.absolutePath
        val wrapperExe = File(binDir, "wrapper.exe")
        if (wrapperExe.exists()) return wrapperExe.absolutePath
        return "wrapper"
    }

    actual fun discoverWorkers(libraryPath: String): String {
        val process = ProcessBuilder(findWrapperBinary(), "discover", libraryPath)
            .redirectErrorStream(true)
            .start()
        process.waitFor(30, TimeUnit.SECONDS)
        return process.inputStream.bufferedReader().readText()
    }

    actual fun parseConfigs(configStrings: List<String>, performDedup: Boolean): GoParseResult {
        val input = JsonConfig.json.encodeToString(configStrings)
        val process = ProcessBuilder(
            findWrapperBinary(),
            "parse",
            input,
            performDedup.toString()
        )
            .redirectErrorStream(true)
            .start()
        process.waitFor(30, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        val result = JsonConfig.json.decodeFromString<ParseResult>(output)
        return GoParseResult(result.configsJson, result.duplicatedCount, result.parseErrorCount)
    }

    actual fun validateConfigs(workerPath: String, configsJson: String): GoValidateResult {
        val process = ProcessBuilder(
            findWrapperBinary(),
            "validate",
            workerPath,
            configsJson
        )
            .redirectErrorStream(true)
            .start()
        process.waitFor(60, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        val result = JsonConfig.json.decodeFromString<ValidateResult>(output)
        return GoValidateResult(result.configsJson, result.validationErrorCount)
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
        val process = ProcessBuilder(
            findWrapperBinary(),
            "test",
            workerPath,
            configsJson,
            settingsJson
        )
            .redirectErrorStream(true)
            .start()

        val reader = process.inputStream.bufferedReader()
        var result = "[]"
        reader.forEachLine { line ->
            when {
                line.startsWith("ROUND_STARTED:") -> {
                    val parts = line.removePrefix("ROUND_STARTED:").split(",")
                    callback.onRoundStarted(parts[0].toLong(), parts[1].toLong(), parts[2].toLong())
                }
                line.startsWith("PROGRESS:") -> {
                    val parts = line.removePrefix("PROGRESS:").split(",")
                    callback.onProgress(parts[0], parts[1].toLong(), parts[2].toBoolean())
                }
                line.startsWith("ROUND_ENDED:") -> {
                    val parts = line.removePrefix("ROUND_ENDED:").split(",")
                    callback.onRoundEnded(parts[0].toLong(), parts[1].toLong())
                }
                line.startsWith("RESULT:") -> {
                    result = line.removePrefix("RESULT:")
                }
            }
        }
        process.waitFor()
        return result
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