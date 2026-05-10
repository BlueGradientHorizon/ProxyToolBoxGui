package com.bghorizon.proxytoolboxgui.data

expect object GoBridge {
    fun discoverWorkers(libraryPath: String): String
    fun runLatencyTests(
        workerPath: String,
        testUrl: String,
        settings: AppSettings,
        callback: GoTestCallback,
        connUris: List<ProxyConfig>
    ): List<ProxyConfig>

    fun stopTests()
}

interface GoTestCallback {
    fun onParseFailed(errors: Map<String, String>)
    fun onValidateFailed(errors: Map<String, String>)
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
    fun onError(message: String)
}
