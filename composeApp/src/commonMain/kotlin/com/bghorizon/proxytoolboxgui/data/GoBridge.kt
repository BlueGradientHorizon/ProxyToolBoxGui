package com.bghorizon.proxytoolboxgui.data

expect object GoBridge {
    fun discoverWorkers(libraryPath: String): List<WorkerInfo>

    fun initializeRunner(workerPath: String)

    fun parseConfigs(connUrisJson: String): Map<String, String>

    fun validateConfigs(): Map<String, String>

    fun runLatencyTests(
        testUrl: String,
        settings: AppSettings,
        callback: GoTestCallback
    ): List<ProxyConfig>

    fun stopTests()
}

interface GoTestCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}