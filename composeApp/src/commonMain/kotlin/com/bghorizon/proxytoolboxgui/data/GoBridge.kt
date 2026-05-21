package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.proto.*

internal expect object GoBridgeNative {
    fun nativeDiscoverWorkers(b: ByteArray): ByteArray
    fun nativeInitializeRunner(b: ByteArray): ByteArray
    fun nativeParseConfigs(b: ByteArray): ByteArray
    fun nativeValidateConfigs(): ByteArray
    fun nativeRunLatencyTests(b: ByteArray, c: JniLatencyTestCallbackWrapper): ByteArray
    fun nativeDiscoverSpeedTestPresets(): ByteArray
    fun nativeRunSpeedTests(b: ByteArray, c: JniSpeedTestCallbackWrapper): ByteArray

    fun nativeStopTests()
}

object GoBridge {
    init {
        NativeLoader.init()
    }

    fun discoverWorkers(libraryPath: String): List<WorkerInfo> {
        val request = PBDiscoverWorkersRequest.newBuilder()
            .setLibraryPath(libraryPath)
            .build()
        val b = GoBridgeNative.nativeDiscoverWorkers(request.toByteArray())
        val r = PBDiscoverWorkersResponse.parseFrom(b)
        if (r.hasError()) throw Exception(r.error)

        return r.workers.workersList.map { proto ->
            WorkerInfo(
                name = proto.name,
                version = proto.version,
                path = proto.path,
            )
        }
    }

    fun initializeRunner(workerPath: String, lowMemMode: Boolean) {
        val request = PBInitializeRunnerRequest.newBuilder()
            .setWorkerPath(workerPath)
            .setLowMemMode(lowMemMode)
            .build()
        val b = GoBridgeNative.nativeInitializeRunner(request.toByteArray())
        val r = PBInitializeRunnerResponse.parseFrom(b)
        if (r.hasError()) throw Exception(r.error)
    }

    fun parseConfigs(inputConfigs: List<ProxyConfig>): Map<String, String> {
        val builder = PBProxyConfigList.newBuilder()
        inputConfigs.forEach { cfg ->
            builder.addConfigs(
                PBProxyConfig.newBuilder()
                    .setTag(cfg.tag)
                    .setConnUri(cfg.connURI)
                    .setDelay(cfg.delay)
                    .build(),
            )
        }
        val protoInput = builder.build()

        val b = GoBridgeNative.nativeParseConfigs(protoInput.toByteArray())
        val r = PBParseConfigsResponse.parseFrom(b)
        if (r.hasError()) throw Exception(r.error)

        return r.parseErrors.itemsMap
    }

    fun validateConfigs(): Map<String, String> {
        val b = GoBridgeNative.nativeValidateConfigs()
        val r = PBValidateConfigsResponse.parseFrom(b)
        if (r.hasError()) throw Exception(r.error)

        return r.validateErrors.itemsMap
    }

    fun runLatencyTests(
        testUrl: String,
        settings: AppSettings,
        callback: GoLatencyTestCallback,
    ): List<ProxyConfig> {
        val wrapper = JniLatencyTestCallbackWrapper(callback)

        val request = PBRunLatencyTestsRequest.newBuilder()
            .setTestUrl(testUrl)
            .setLatencyRounds(settings.latencyRounds)
            .setRoundTimeout(settings.roundTimeout)
            .setTestByBatches(settings.testByBatches)
            .setBatchSize(settings.batchSize)
            .build()

        val b = GoBridgeNative.nativeRunLatencyTests(
            request.toByteArray(),
            wrapper,
        )

        val r = PBRunLatencyTestsResponse.parseFrom(b)
        if (r.hasError()) throw Exception(r.error)

        return r.configs.configsList.map { proto ->
            ProxyConfig(
                tag = proto.tag,
                connURI = proto.connUri,
                delay = proto.delay
            )
        }
    }

    fun discoverSpeedTestPresets(): Map<String, String> {
        val b = GoBridgeNative.nativeDiscoverSpeedTestPresets()
        val r = PBDiscoverSpeedTestPresetsResponse.parseFrom(b)
        if (r.hasError()) throw Exception(r.error)

        return r.presets.itemsMap
    }

    fun runSpeedTests(
        workingTags: List<String>,
        settings: AppSettings,
        callback: GoSpeedTestCallback,
    ): List<SpeedTestResult> {
        val wrapper = JniSpeedTestCallbackWrapper(callback)

        val request = PBRunSpeedTestsRequest.newBuilder()
            .addAllTags(workingTags)
            .setProviderId(settings.speedTestProviderId)
            .setMode(if (settings.speedTestMode == "upload") PBSpeedTestMode.UPLOAD else PBSpeedTestMode.DOWNLOAD)
            .setRounds(settings.speedTestRounds)
            .setTimeout(settings.roundTimeout)
            .setTargetBytes(settings.speedTestTargetBytes)
            .build()

        val b = GoBridgeNative.nativeRunSpeedTests(
            request.toByteArray(),
            wrapper,
        )

        val r = PBRunSpeedTestsResponse.parseFrom(b)
        if (r.hasError()) throw Exception(r.error)

        return r.resultsList.map { proto ->
            SpeedTestResult(
                tag = proto.tag,
                speed = proto.speed,
                error = if (proto.hasError()) proto.error else null
            )
        }
    }

    fun stopTests() {
        GoBridgeNative.nativeStopTests()
    }
}

interface GoLatencyTestCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, delay: Long, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}

interface GoSpeedTestCallback {
    fun onRoundStarted(batch: Long, round: Long, total: Long)
    fun onProgress(tag: String, speed: Double, failed: Boolean)
    fun onRoundEnded(batch: Long, round: Long)
}
