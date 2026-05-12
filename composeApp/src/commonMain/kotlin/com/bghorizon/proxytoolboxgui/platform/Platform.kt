package com.bghorizon.proxytoolboxgui.platform

interface Platform {
    val name: String
    val isDynamicColorSupported: Boolean
    fun getAppDataDir(): String
    fun getWorkerLibraryPath(): String
    fun copyToClipboard(text: String, label: String)
    fun getClipboardText(): String?
    suspend fun exportToFile(text: String, filename: String): String?
    fun showToast(message: String, duration: Int = 0)
}

expect fun getPlatform(): Platform