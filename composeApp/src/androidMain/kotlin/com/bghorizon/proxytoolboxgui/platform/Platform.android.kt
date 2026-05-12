package com.bghorizon.proxytoolboxgui.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.bghorizon.proxytoolboxgui.ProxyToolBoxApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val isDynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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

    override suspend fun exportToFile(text: String, filename: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val activity = ProxyToolBoxApplication.currentActivity as? ComponentActivity
            if (activity == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val registry = activity.activityResultRegistry
            val key = "export_to_file_${System.currentTimeMillis()}"

            var launcher: ActivityResultLauncher<String>? = null
            launcher = registry.register(key, ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                if (uri != null) {
                    try {
                        activity.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(text.toByteArray())
                        }
                        val displayName = getDisplayName(activity, uri) ?: uri.toString()
                        continuation.resume(displayName)
                    } catch (e: Exception) {
                        continuation.resume(null)
                    }
                } else {
                    continuation.resume(null)
                }
                launcher?.unregister()
            }

            try {
                launcher.launch(filename)
            } catch (e: Exception) {
                launcher.unregister()
                continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                launcher.unregister()
            }
        }
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    override fun showToast(message: String, duration: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, duration).show()
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform(ProxyToolBoxApplication.appContext)
