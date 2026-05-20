package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.proto.*

internal expect object GoBridgeNative {
    fun nativeDiscoverWorkers(libraryPath: String): ByteArray
    fun nativeInitializeRunner(workerPath: String): ByteArray
    fun nativeParseConfigs(configs: ByteArray): ByteArray
    fun nativeValidateConfigs(): ByteArray
    fun nativeRunLatencyTests(
        testUrl: String,
        latencyRounds: Int,
        roundTimeout: Int,
        testByBatches: Boolean,
        batchSize: Int,
        callback: JniTestCallbackWrapper,
    ): ByteArray

    fun nativeStopTests()
}

object GoBridge {
    init {
        NativeLoader.init()
    }

    private fun checkResponse(response: PBNativeResponse) {
        if (response.error.isNotEmpty()) {
            throw Exception(response.error)
        }
    }

    fun discoverWorkers(libraryPath: String): List<WorkerInfo> {
        val bytes = GoBridgeNative.nativeDiscoverWorkers(libraryPath)
        val response = PBNativeResponse.parseFrom(bytes)
        checkResponse(response)

        val workersList = response.workers
        return workersList.workersList.map { proto ->
            WorkerInfo(
                name = proto.name,
                version = proto.version,
                path = proto.path
            )
        }
    }

    fun initializeRunner(workerPath: String) {
        val bytes = GoBridgeNative.nativeInitializeRunner(workerPath)
        val response = PBNativeResponse.parseFrom(bytes)
        checkResponse(response)
    }

    fun parseConfigs(inputConfigs: List<ProxyConfig>): Map<String, String> {
        val builder = PBProxyConfigList.newBuilder()
        inputConfigs.forEach { cfg ->
            builder.addConfigs(
                PBProxyConfig.newBuilder()
                    .setTag(cfg.tag)
                    .setConnUri(cfg.connURI)
                    .setDelay(cfg.delay)
                    .build()
            )
        }
        val protoInput = builder.build()

        val bytes = GoBridgeNative.nativeParseConfigs(protoInput.toByteArray())
        val response = PBNativeResponse.parseFrom(bytes)
        checkResponse(response)

        return response.stringMap.itemsMap
    }

    fun validateConfigs(): Map<String, String> {
        val bytes = GoBridgeNative.nativeValidateConfigs()
        val response = PBNativeResponse.parseFrom(bytes)
        checkResponse(response)

        return response.stringMap.itemsMap
    }

    fun runLatencyTests(
        testUrl: String,
        settings: AppSettings,
        callback: GoTestCallback,
    ): List<ProxyConfig> {
        val wrapper = JniTestCallbackWrapper(callback)

        val bytes = GoBridgeNative.nativeRunLatencyTests(
            testUrl,
            settings.latencyRounds,
            settings.roundTimeout,
            settings.testByBatches,
            settings.batchSize,
            wrapper,
        )

        val response = PBNativeResponse.parseFrom(bytes)
        checkResponse(response)

        val configsList = response.configs
        return configsList.configsList.map { proto ->
            ProxyConfig(
                tag = proto.tag,
                connURI = proto.connUri,
                delay = proto.delay
            )
        }
    }

    fun stopTests() {
        GoBridgeNative.nativeStopTests()
    }
}

interface GoTestCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}
