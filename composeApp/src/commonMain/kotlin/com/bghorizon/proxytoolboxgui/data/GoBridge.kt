package com.bghorizon.proxytoolboxgui.data

expect object GoBridge {
    fun discoverWorkers(libraryPath: String): String
    fun parseConfigs(configStrings: List<String>, performDedup: Boolean): GoParseResult
    fun validateConfigs(workerPath: String, configsJson: String): GoValidateResult
    fun runLatencyTests(
        workerPath: String,
        configsJson: String,
        settings: AppSettings,
        callback: GoTestCallback
    ): String
}

interface GoTestCallback {
    fun onRoundStarted(batch: Int, round: Int, total: Int)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Int, round: Int)
}

data class GoParseResult(
    val configsJson: String,
    val duplicatedCount: Int,
    val parseErrorCount: Int
)

data class GoValidateResult(
    val configsJson: String,
    val validationErrorCount: Int
)
