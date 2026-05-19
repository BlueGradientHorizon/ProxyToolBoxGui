package com.bghorizon.proxytoolboxgui.data

import java.io.File
import java.nio.file.Files

actual object NativeLoader {
    private var isLoaded = false
    lateinit var tempDir: String

    actual fun init() {
        if (isLoaded) return
        val tempFileDir = Files.createTempDirectory("proxytoolbox_native").toFile()
        tempFileDir.deleteOnExit()
        tempDir = tempFileDir.absolutePath

        val osName = System.getProperty("os.name").lowercase()
        val isWin = osName.contains("win")
        val isMac = osName.contains("mac")
        val extLib = when {
            isWin -> ".dll"
            isMac -> ".dylib"
            else -> ".so"
        }
        val libWrapper = "libwrapper$extLib"

        val filesToExtract = mutableListOf(libWrapper)

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
