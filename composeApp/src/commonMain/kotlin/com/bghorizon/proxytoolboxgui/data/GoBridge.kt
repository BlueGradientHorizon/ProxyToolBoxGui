package com.bghorizon.proxytoolboxgui.data
expect object GoBridge {
    fun discoverWorkers(libraryPath: String): String
//    fun parseConnUris(connUris: List<String>, performDedup: Boolean): ConnUrisParsingResult
//    fun validateConfigs(workerPath: String, configsJson: String): ConfigsValidationResult
    fun runLatencyTests(
    workerPath: String,
    settings: AppSettings,
    callback: GoTestCallback,
    connUris: List<ProxyConfig>,
    performDedup: Boolean
    ): List<ProxyConfig>
}

interface GoTestCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}

//data class ConnUrisParsingResult(
//    val configsJson: String,
//    val duplicatedCount: Int,
//    val parseErrorCount: Int
//)
//
//data class ConfigsValidationResult(
//    val configsJson: String,
//    val validationErrorCount: Int
//)
