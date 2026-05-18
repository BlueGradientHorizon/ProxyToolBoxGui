package com.bghorizon.proxytoolboxgui.data

expect object GoBridge {
    fun discoverWorkers(libraryPath: String): String

    fun initializeRunner(workerPath: String, callback: GoErrorCallback)

    fun parseConfigs(connUrisJson: String, callback: GoParseCallback)

    fun validateConfigs(callback: GoValidateCallback)

    fun runLatencyTests(
        testUrl: String,
        settings: AppSettings,
        callback: GoTestCallback
    ): List<ProxyConfig>

    fun stopTests()
}

interface GoErrorCallback {
    fun onError(message: String)
}

interface GoParseCallback : GoErrorCallback {
    fun onParseFailed(errors: Map<String, String>)
}

interface GoValidateCallback : GoErrorCallback {
    fun onValidateFailed(errors: Map<String, String>)
}

interface GoTestCallback : GoErrorCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}
