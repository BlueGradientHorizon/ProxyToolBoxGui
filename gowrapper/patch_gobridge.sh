sed -i '/fun nativeRunLatencyTests(/i \    fun nativeRunSpeedTests(\n        provider: String,\n        mode: String,\n        targetBytes: Long,\n        speedRounds: Int,\n        roundTimeout: Int,\n        testByBatches: Boolean,\n        batchSize: Int,\n        targetTagsJson: String,\n        callback: JniSpeedTestCallbackWrapper,\n    ): String\n' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/GoBridge.kt

cat << 'INNER_EOF' >> /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/GoBridge.kt

interface GoSpeedTestCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, speed: Double, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}
INNER_EOF

# Add runSpeedTests to GoBridge
sed -i '/fun stopTests()/i \    fun runSpeedTests(\n        provider: String,\n        mode: String,\n        targetBytes: Long,\n        settings: AppSettings,\n        targetTagsJson: String,\n        callback: GoSpeedTestCallback,\n    ): String {\n        val wrapper = JniSpeedTestCallbackWrapper(callback)\n        val responseJson = GoBridgeNative.nativeRunSpeedTests(\n            provider,\n            mode,\n            targetBytes,\n            settings.speedTestRounds,\n            settings.roundTimeout,\n            settings.testByBatches,\n            settings.batchSize,\n            targetTagsJson,\n            wrapper,\n        )\n        val response = parseNativeResponse(responseJson)\n        if (!response.error.isNullOrEmpty()) throw Exception(response.error)\n        return response.data\n    }\n' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/GoBridge.kt
