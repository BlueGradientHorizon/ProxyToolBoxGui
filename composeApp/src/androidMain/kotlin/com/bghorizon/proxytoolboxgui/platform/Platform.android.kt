package com.bghorizon.proxytoolboxgui.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import com.bghorizon.proxytoolboxgui.ProxyToolBoxApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    override fun getAppDataDir(): String {
        return context.filesDir.absolutePath
    }

    override fun getWorkerLibraryPath(): String {
        return context.applicationInfo.nativeLibraryDir
    }

    override fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Proxy configs", text))
    }

    override fun exportToFile(text: String, filename: String): Boolean {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(dir, filename)
            file.writeText(text)
            Toast.makeText(context, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    override fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun downloadSubscription(url: String, timeoutSeconds: Int): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutSeconds * 1000
        connection.readTimeout = timeoutSeconds * 1000
        connection.requestMethod = "GET"
        return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform(ProxyToolBoxApplication.appContext)