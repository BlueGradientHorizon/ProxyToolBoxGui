sed -i '/actual external fun nativeRunLatencyTests(/i \    @JvmStatic\n    actual external fun nativeRunSpeedTests(\n        provider: String,\n        mode: String,\n        targetBytes: Long,\n        speedRounds: Int,\n        roundTimeout: Int,\n        testByBatches: Boolean,\n        batchSize: Int,\n        targetTagsJson: String,\n        callback: JniSpeedTestCallbackWrapper,\n    ): String\n' /home/engine/project/composeApp/src/jvmMain/kotlin/com/bghorizon/proxytoolboxgui/data/GoBridge.jvm.kt

sed -i '/actual external fun nativeRunLatencyTests(/i \    actual external fun nativeRunSpeedTests(\n        provider: String,\n        mode: String,\n        targetBytes: Long,\n        speedRounds: Int,\n        roundTimeout: Int,\n        testByBatches: Boolean,\n        batchSize: Int,\n        targetTagsJson: String,\n        callback: JniSpeedTestCallbackWrapper,\n    ): String\n' /home/engine/project/composeApp/src/androidMain/kotlin/com/bghorizon/proxytoolboxgui/data/GoBridge.android.kt

cat << 'INNER_EOF' >> /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/GoBridge.kt

class JniSpeedTestCallbackWrapper(private val callback: GoSpeedTestCallback) {
    fun onRoundStarted(batch: Long, round: Long, total: Long) {
        callback.onRoundStarted(batch, round, total)
    }

    fun onProgress(tag: String, speed: Double, failed: Boolean) {
        callback.onProgress(tag, speed, failed)
    }

    fun onRoundEnded(batch: Long, round: Long) {
        callback.onRoundEnded(batch, round)
    }
}
INNER_EOF
