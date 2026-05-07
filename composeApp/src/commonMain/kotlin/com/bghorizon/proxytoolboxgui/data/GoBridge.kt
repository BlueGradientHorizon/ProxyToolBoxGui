package com.bghorizon.proxytoolboxgui.data

expect object GoBridge {
    fun discoverWorkers(libraryPath: String): String
    fun runLatencyTests(
        workerPath: String,
        settings: AppSettings,
        callback: GoTestCallback,
        connUris: List<ProxyConfig>,
        performDedup: Boolean
    ): List<ProxyConfig>

    fun stopTests()
}

interface GoTestCallback {
    fun onParseFailed(tags: List<String>)
    fun onValidateFailed(tags: List<String>)
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}
