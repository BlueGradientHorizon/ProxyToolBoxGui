package com.bghorizon.proxytoolboxgui.platform

import com.bghorizon.proxytoolboxgui.data.NativeLoader
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser

class JVMPlatform : Platform {
    override val name: String = "JVM ${System.getProperty("java.version")}"

    override fun getAppDataDir(): String {
        val dir = File(System.getProperty("user.home"), ".proxytoolboxgui")
        dir.mkdirs()
        return dir.absolutePath
    }

    override fun getWorkerLibraryPath(): String {
        return NativeLoader.tempDir
    }

    override fun copyToClipboard(text: String, label: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }

    override fun exportToFile(text: String, filename: String): String? {
        val chooser = JFileChooser()
        chooser.selectedFile = File(filename)
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            file.writeText(text)
            return file.absolutePath
        }
        return null
    }

    override fun showToast(message: String, duration: Int) {
        println("Toast: $message (duration: $duration)")
    }
}

actual fun getPlatform(): Platform = JVMPlatform()