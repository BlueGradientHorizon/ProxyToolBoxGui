package com.bghorizon.proxytoolboxgui.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.bghorizon.proxytoolboxgui.ProxyToolBoxApplication
import java.io.File

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    override fun getAppDataDir(): String {
        return context.filesDir.absolutePath
    }

    override fun getWorkerLibraryPath(): String {
        return context.applicationInfo.nativeLibraryDir
    }

    override fun copyToClipboard(text: String, label: String) {
        Handler(Looper.getMainLooper()).post {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    override fun exportToFile(text: String, filename: String): String? {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(dir, filename)
            file.writeText(text)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    override fun showToast(message: String, duration: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, duration).show()
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform(ProxyToolBoxApplication.appContext)