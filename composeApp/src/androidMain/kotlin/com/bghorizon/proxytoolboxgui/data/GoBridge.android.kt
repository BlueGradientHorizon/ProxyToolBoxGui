package com.bghorizon.proxytoolboxgui.data

internal actual object GoBridgeNative {
    @JvmStatic
    actual external fun nativeDiscoverWorkers(libraryPath: String): String

    @JvmStatic
    actual external fun nativeInitializeRunner(workerPath: String): String

    @JvmStatic
    actual external fun nativeParseConfigs(connUrisJson: String): String

    @JvmStatic
    actual external fun nativeValidateConfigs(): String

    @JvmStatic
    actual external fun nativeRunSpeedTests(
        provider: String,
        mode: String,
        targetBytes: Long,
        speedRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        targetTagsJson: String,
        callback: JniSpeedTestCallbackWrapper,
    ): String

    actual external fun nativeRunLatencyTests(
        testUrl: String,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniTestCallbackWrapper,
    ): String

    @JvmStatic
    actual external fun nativeStopTests()
}