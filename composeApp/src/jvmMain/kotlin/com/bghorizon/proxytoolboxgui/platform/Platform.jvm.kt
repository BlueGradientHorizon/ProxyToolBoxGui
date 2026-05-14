package com.bghorizon.proxytoolboxgui.platform

import com.bghorizon.proxytoolboxgui.data.NativeLoader
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JVMPlatform : Platform {
    override val name: String = "JVM ${System.getProperty("java.version")}"
    override val isDynamicColorSupported: Boolean = false
    override val isQrScannerSupported: Boolean = false

    override suspend fun pickImageAndScanQr(): String? = withContext(Dispatchers.IO) {
        val chooser = JFileChooser()
        chooser.fileFilter = FileNameExtensionFilter("Images", "jpg", "jpeg", "png", "bmp", "gif")
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            try {
                val bufferedImage = ImageIO.read(file) ?: return@withContext null
                val source = BufferedImageLuminanceSource(bufferedImage)
                val bitmap = BinaryBitmap(HybridBinarizer(source))
                val result = MultiFormatReader().decode(bitmap)
                return@withContext result.text
            } catch (e: NotFoundException) {
                return@withContext null
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
        null
    }

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

    override fun getClipboardText(): String? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null)
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                contents.getTransferData(DataFlavor.stringFlavor) as String
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun exportToFile(text: String, filename: String): String? {
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