package com.bghorizon.proxytoolboxgui.platform

interface Platform {
    val name: String
    fun getAppDataDir(): String
    fun getWorkerLibraryPath(): String
    fun copyToClipboard(text: String)
    fun exportToFile(text: String, filename: String): Boolean
    fun showToast(message: String)
}

expect fun getPlatform(): Platform