package com.bghorizon.proxytoolboxgui.data

import java.io.File
import java.nio.file.Files

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
    private external fun nativeInitializeRunner(
        workerPath: String,
        callback: JniErrorCallbackWrapper,
    )

    @JvmStatic
    private external fun nativeParseConfigs(
        connUrisJson: String,
        callback: JniParseCallbackWrapper,
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
        return nativeDiscoverWorkers(libraryPath)
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
