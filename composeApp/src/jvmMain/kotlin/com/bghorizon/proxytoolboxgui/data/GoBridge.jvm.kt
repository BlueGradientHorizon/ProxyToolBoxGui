package com.bghorizon.proxytoolboxgui.data

internal actual object GoBridgeNative {
    @JvmStatic
    actual external fun nativeDiscoverWorkers(libraryPath: String): ByteArray

    @JvmStatic
    actual external fun nativeInitializeRunner(workerPath: String): ByteArray

    @JvmStatic
    actual external fun nativeParseConfigs(configs: ByteArray): ByteArray

    @JvmStatic
    actual external fun nativeValidateConfigs(): ByteArray

    @JvmStatic
    actual external fun nativeRunLatencyTests(
        testUrl: String,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniTestCallbackWrapper,
    ): ByteArray

    @JvmStatic
    actual external fun nativeStopTests()
}