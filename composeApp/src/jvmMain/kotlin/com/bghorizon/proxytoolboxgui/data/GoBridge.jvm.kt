package com.bghorizon.proxytoolboxgui.data

internal actual object GoBridgeNative {
    @JvmStatic
    actual external fun nativeDiscoverWorkers(b: ByteArray): ByteArray

    @JvmStatic
    actual external fun nativeInitializeRunner(b: ByteArray): ByteArray

    @JvmStatic
    actual external fun nativeParseConfigs(b: ByteArray): ByteArray

    @JvmStatic
    actual external fun nativeValidateConfigs(): ByteArray

    @JvmStatic
    actual external fun nativeRunLatencyTests(b: ByteArray, c: JniLatencyTestCallbackWrapper): ByteArray

    @JvmStatic
    actual external fun nativeDiscoverSpeedTestPresets(): ByteArray

    @JvmStatic
    actual external fun nativeRunSpeedTests(b: ByteArray, c: JniSpeedTestCallbackWrapper): ByteArray

    @JvmStatic
    actual external fun nativeStopTests()
}