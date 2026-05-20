package com.bghorizon.proxytoolboxgui.data

internal actual object GoBridgeNative {
    @JvmStatic
    actual external fun nativeDiscoverWorkers(libraryPath: String): String

    @JvmStatic
    actual external fun nativeDiscoverSpeedTestPresets(): String

    @JvmStatic
    actual external fun nativeInitializeRunner(workerPath: String): String

    @JvmStatic
    actual external fun nativeParseConfigs(connUrisJson: String): String

    @JvmStatic
    actual external fun nativeValidateConfigs(): String

    @JvmStatic
    actual external fun nativeRunLatencyTests(
        testUrl: String,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniLatencyTestCallbackWrapper,
    ): String

    @JvmStatic
    actual external fun nativeRunSpeedTests(
        connUrisJson: String,
        providerId: String,
        mode: String,
        targetBytes: Long,
        rounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniSpeedTestCallbackWrapper,
    ): String

    @JvmStatic
    actual external fun nativeStopTests()
}
