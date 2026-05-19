package com.bghorizon.proxytoolboxgui.data

import android.util.Log
import com.bghorizon.proxytoolboxgui.AppContext

actual object NativeLoader {
    private const val TAG = "GoBridge"

    actual fun init() {
        Log.d(TAG, "Loading wrapper library...")
        try {
            val libDir = AppContext.context.applicationInfo.nativeLibraryDir
            val libName = System.mapLibraryName("wrapper")
            val absolutePath = java.io.File(libDir, libName).absolutePath
            Log.d(TAG, "Absolute path to library: $absolutePath")

            System.loadLibrary("wrapper")
            Log.d(TAG, "wrapper library loaded successfully from $absolutePath")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load wrapper library", e)
        }
    }
}
