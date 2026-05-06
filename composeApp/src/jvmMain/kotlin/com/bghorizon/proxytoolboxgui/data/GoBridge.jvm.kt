package com.bghorizon.proxytoolboxgui.data

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString

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

    actual fun runLatencyTests(
        workerPath: String,
        settings: AppSettings,
        callback: GoTestCallback,
        connUris: List<ProxyConfig>,
        performDedup: Boolean
    ): List<ProxyConfig> {
        val connUrisJson = JsonConfig.json.encodeToString(connUris)
        val settingsObj = object {
            val performDedup = performDedup
            val latencyRounds = settings.latencyRounds
            val roundTimeout = settings.roundTimeout
            val testByBatches = settings.testByBatches
            val batchSize = settings.batchSize
        }
        val settingsJson = JsonConfig.json.encodeToString(settingsObj)

        val process = ProcessBuilder(
            findWrapperBinary(),
            "test",
            workerPath,
            connUrisJson,
            settingsJson
        )
            .redirectErrorStream(true)
            .start()

        val reader = process.inputStream.bufferedReader()
        var resultJson = "[]"
        reader.forEachLine { line ->
            when {
                line.startsWith("PARSE_FAILED:") -> {
                    val json = line.removePrefix("PARSE_FAILED:").trim()
                    val tags = parseTagsJson(json)
                    callback.onParseFailed(tags)
                }
                line.startsWith("VALIDATE_FAILED:") -> {
                    val json = line.removePrefix("VALIDATE_FAILED:").trim()
                    val tags = parseTagsJson(json)
                    callback.onValidateFailed(tags)
                }
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
                    resultJson = line.removePrefix("RESULT:").trim()
                }
            }
        }
        process.waitFor()
        return JsonConfig.json.decodeFromString<List<ProxyConfig>>(resultJson)
    }

    private fun parseTagsJson(json: String): List<String> {
        return try {
            JsonConfig.json.decodeFromString<List<String>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
