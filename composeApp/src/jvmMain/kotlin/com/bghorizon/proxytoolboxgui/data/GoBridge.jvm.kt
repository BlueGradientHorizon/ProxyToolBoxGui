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
        return nativeDiscoverWorkers(libraryPath)
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
