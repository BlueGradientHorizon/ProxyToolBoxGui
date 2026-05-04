package com.bghorizon.proxytoolboxgui.platform

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
        val jarPath = File(System.getProperty("java.class.path").split(File.pathSeparator)[0])
        return if (jarPath.isFile) jarPath.parentFile.absolutePath else System.getProperty("user.dir")
    }

    override fun copyToClipboard(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }

    override fun exportToFile(text: String, filename: String): Boolean {
        val chooser = JFileChooser()
        chooser.selectedFile = File(filename)
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.writeText(text)
            return true
        }
        return false
    }

    override fun showToast(message: String) {
        println("Toast: $message")
    }
}

actual fun getPlatform(): Platform = JVMPlatform()