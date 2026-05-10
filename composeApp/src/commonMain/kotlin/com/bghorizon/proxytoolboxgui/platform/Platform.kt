package com.bghorizon.proxytoolboxgui.platform

interface Platform {
    val name: String
    fun getAppDataDir(): String
    fun getWorkerLibraryPath(): String
    fun copyToClipboard(text: String, label: String)
    fun exportToFile(text: String, filename: String): String?
    fun showToast(message: String, duration: Int = 0)
}

expect fun getPlatform(): Platform