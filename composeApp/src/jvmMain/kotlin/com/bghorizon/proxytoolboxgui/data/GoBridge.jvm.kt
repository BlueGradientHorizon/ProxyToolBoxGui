package com.bghorizon.proxytoolboxgui.data

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object NativeLoader {
    private var isLoaded = false
    lateinit var tempDir: String

    fun init() {
        if (isLoaded) return
        val tempFileDir = Files.createTempDirectory("proxytoolbox_native").toFile()
        tempFileDir.deleteOnExit()
        tempDir = tempFileDir.absolutePath

        val osName = System.getProperty("os.name").lowercase()
        val isWin = osName.contains("win")
        val isMac = osName.contains("mac")
        val extLib = when {
            isWin -> ".dll"; isMac -> ".dylib"; else -> ".so"
        }
        val libWrapper = "libwrapper$extLib"

        val filesToExtract = mutableListOf(libWrapper)

        // Extract all files from resources to tempDir
        val resourcesDir = File("src/jvmMain/resources")
        if (resourcesDir.exists()) {
            resourcesDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    filesToExtract.add(file.name)
                }
            }
        }

        for (fileName in filesToExtract.distinct()) {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(fileName)
            if (stream != null) {
                val dest = File(tempFileDir, fileName)
                stream.use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                dest.setExecutable(true)
            }
        }

        System.load(File(tempFileDir, libWrapper).absolutePath)
        isLoaded = true
    }
}

actual object GoBridge {
    init {
        NativeLoader.init()
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
        val responseJson = nativeDiscoverWorkers(libraryPath)
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