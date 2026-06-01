package com.bghorizon.proxytoolboxgui.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyTestManager(
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend fun prepareTest(
        settings: AppSettings,
        subscriptions: List<Subscription>,
    ): TestSetup = withContext(Dispatchers.IO) {
        val subs = subscriptions.map {
            it.copy(
                parseErr = 0,
                validErr = 0
            )
        }
        val configs = mutableListOf<ProxyConfig>()

        for (sub in subs) {
            val allConfigs =
                subscriptionRepository.getConfigs(sub.id).filter { it.connURI.isNotBlank() }

            for (cfg in allConfigs) {
                val tag = "sub-${sub.id}-${cfg.configId}"
                configs.add(ProxyConfig(tag = tag, connURI = cfg.connURI))
            }
        }

        val totalBatches = if (settings.testByBatches && (settings.batchSize > 0)) {
            ((configs.size + settings.batchSize) - 1) / settings.batchSize
        } else {
            1
        }
        val totalRounds = settings.latencyRounds
        val roundTimeout = settings.roundTimeout
        var totalSeconds = totalBatches * totalRounds * roundTimeout

        if (settings.performSpeedTests) {
            // Speed tests run in 1 batch for all working profiles
            val speedTestRounds = settings.speedTestRounds
            totalSeconds += 1 * speedTestRounds * roundTimeout
        }

        TestSetup(configs, subs, totalBatches, totalRounds, totalSeconds)
    }

    suspend fun initializeRunner(workerPath: String, lowMemMode: Boolean) =
        withContext(Dispatchers.IO) {
            GoBridge.initializeRunner(workerPath, lowMemMode)
        }

    suspend fun parseConfigs(configs: List<ProxyConfig>): Map<String, String> =
        withContext(Dispatchers.IO) {
            GoBridge.parseConfigs(configs)
        }

    suspend fun validateConfigs(): Map<String, String> = withContext(Dispatchers.IO) {
        GoBridge.validateConfigs()
    }

    suspend fun runLatencyTests(
        settings: AppSettings,
        onEvent: (LatencyTestEvent) -> Unit
    ): List<ProxyConfig> = withContext(Dispatchers.IO) {
        GoBridge.runLatencyTests(
            testUrl = settings.testUrl,
            settings = settings,
            callback = object : GoLatencyTestCallback {
                override fun onRoundStarted(batch: Long, round: Long, total: Long) {
                    onEvent(
                        LatencyTestEvent.RoundStarted(
                            batch.toInt(),
                            round.toInt(),
                            total.toInt()
                        )
                    )
                }

                override fun onProgress(tag: String, delay: Long, failed: Boolean) {
                    onEvent(LatencyTestEvent.Progress(tag, delay, failed))
                }

                override fun onRoundEnded(batch: Long, round: Long) {
                    onEvent(LatencyTestEvent.RoundEnded(batch.toInt(), round.toInt()))
                }
            }
        )
    }

    suspend fun runSpeedTests(
        workingTags: List<String>,
        settings: AppSettings,
        onEvent: (SpeedTestEvent) -> Unit
    ): List<SpeedTestResult> = withContext(Dispatchers.IO) {
        GoBridge.runSpeedTests(
            workingTags = workingTags,
            settings = settings,
            callback = object : GoSpeedTestCallback {
                override fun onRoundStarted(batch: Long, round: Long, total: Long) {
                    onEvent(
                        SpeedTestEvent.RoundStarted(
                            batch.toInt(),
                            round.toInt(),
                            total.toInt()
                        )
                    )
                }

                override fun onProgress(tag: String, speed: Double, failed: Boolean) {
                    onEvent(SpeedTestEvent.Progress(tag, speed, failed))
                }

                override fun onRoundEnded(batch: Long, round: Long) {
                    onEvent(SpeedTestEvent.RoundEnded(batch.toInt(), round.toInt()))
                }
            }
        )
    }

    fun stopTests() {
        GoBridge.stopTests()
    }

    fun extractIds(tag: String): Pair<String, Int>? {
        if (!tag.startsWith("sub-")) return null
        val parts = tag.removePrefix("sub-").split('-')
        if (parts.size < 2) return null
        val subId = parts.subList(0, parts.size - 1).joinToString("-")
        val configId = parts.last().toIntOrNull() ?: return null
        return subId to configId
    }
}

sealed class LatencyTestEvent {
    data class RoundStarted(val batch: Int, val round: Int, val total: Int) : LatencyTestEvent()
    data class Progress(val tag: String, val delay: Long, val failed: Boolean) : LatencyTestEvent()
    data class RoundEnded(val batch: Int, val round: Int) : LatencyTestEvent()
}

sealed class SpeedTestEvent {
    data class RoundStarted(val batch: Int, val round: Int, val total: Int) : SpeedTestEvent()
    data class Progress(val tag: String, val speed: Double, val failed: Boolean) : SpeedTestEvent()
    data class RoundEnded(val batch: Int, val round: Int) : SpeedTestEvent()
}

data class TestSetup(
    val configs: List<ProxyConfig>,
    val updatedSubscriptions: List<Subscription>,
    val totalBatches: Int,
    val totalRounds: Int,
    val totalSeconds: Int
)