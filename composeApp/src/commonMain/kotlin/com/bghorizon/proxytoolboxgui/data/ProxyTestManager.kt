package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.utils.ConfigUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyTestManager(
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend fun prepareTest(
        settings: AppSettings,
        subscriptions: List<Subscription>
    ): TestSetup = withContext(Dispatchers.IO) {
        val subs = subscriptions.map {
            it.copy(
                duplicated = 0,
                parseErr = 0,
                validErr = 0
            )
        }.toMutableList()
        val configs = mutableListOf<ProxyConfig>()
        val seenUris = mutableSetOf<String>()

        for (i in subs.indices) {
            val sub = subs[i]
            val uris =
                subscriptionRepository.getConfigsUris(sub.id).filter { it.isNotBlank() }
            val uniqueUris = if (settings.performDedup) {
                ConfigUtils.naiveDeduplicate(uris, seenUris)
            } else {
                uris
            }
            if (settings.performDedup) {
                subs[i] = subs[i].copy(duplicated = uris.size - uniqueUris.size)
            }
            for ((uriIndex, uri) in uniqueUris.withIndex()) {
                val tag = "sub-${sub.id}-$uriIndex"
                configs.add(ProxyConfig(tag = tag, connURI = uri))
            }
        }

        val totalBatches = if (settings.testByBatches && (settings.batchSize > 0)) {
            (configs.size + settings.batchSize - 1) / settings.batchSize
        } else {
            1
        }
        val totalRounds = settings.latencyRounds
        val roundTimeout = settings.roundTimeout
        val totalSeconds = totalBatches * totalRounds * roundTimeout

        TestSetup(configs, subs, totalBatches, totalRounds, totalSeconds)
    }

    suspend fun runLatencyTests(
        settings: AppSettings,
        configs: List<ProxyConfig>,
        onEvent: (LatencyTestEvent) -> Unit
    ): List<ProxyConfig> = withContext(Dispatchers.IO) {
        try {
            GoBridge.initializeRunner(settings.selectedWorker)
        } catch (e: Exception) {
            onEvent(LatencyTestEvent.Error(e.message ?: "Unknown error"))
            return@withContext emptyList()
        }

        val connUrisJson = JsonConfig.json.encodeToString(configs)
        try {
            val errors = GoBridge.parseConfigs(connUrisJson)
            if (errors.isNotEmpty()) {
                onEvent(LatencyTestEvent.ParseFailed(errors))
            }
        } catch (e: Exception) {
            onEvent(LatencyTestEvent.Error(e.message ?: "Unknown error"))
            return@withContext emptyList()
        }

        try {
            val errors = GoBridge.validateConfigs()
            if (errors.isNotEmpty()) {
                onEvent(LatencyTestEvent.ValidateFailed(errors))
            }
        } catch (e: Exception) {
            onEvent(LatencyTestEvent.Error(e.message ?: "Unknown error"))
            return@withContext emptyList()
        }

        try {
            GoBridge.runLatencyTests(
                testUrl = settings.testUrl,
                settings = settings,
                callback = object : GoLatencyTestCallback {
                    override fun onRoundStarted(batch: Long, round: Long, total: Long) {
                        onEvent(LatencyTestEvent.RoundStarted(batch.toInt(), round.toInt(), total.toInt()))
                    }

                    override fun onProgress(tag: String, delay: Long, failed: Boolean) {
                        onEvent(LatencyTestEvent.Progress(tag, delay, failed))
                    }

                    override fun onRoundEnded(batch: Long, round: Long) {
                        onEvent(LatencyTestEvent.RoundEnded(batch.toInt(), round.toInt()))
                    }
                }
            )
        } catch (e: Exception) {
            onEvent(LatencyTestEvent.Error(e.message ?: "Unknown error"))
            emptyList()
        }
    }

    suspend fun runSpeedTests(
        settings: AppSettings,
        configs: List<ProxyConfig>,
        onEvent: (SpeedTestEvent) -> Unit
    ): List<ProxyConfig> = withContext(Dispatchers.IO) {
        try {
            GoBridge.runSpeedTests(
                configs = configs,
                settings = settings,
                callback = object : GoSpeedTestCallback {
                    override fun onRoundStarted(batch: Long, round: Long, total: Long) {
                        onEvent(SpeedTestEvent.RoundStarted(batch.toInt(), round.toInt(), total.toInt()))
                    }

                    override fun onProgress(tag: String, speed: Double, failed: Boolean) {
                        onEvent(SpeedTestEvent.Progress(tag, speed, failed))
                    }

                    override fun onRoundEnded(batch: Long, round: Long) {
                        onEvent(SpeedTestEvent.RoundEnded(batch.toInt(), round.toInt()))
                    }
                }
            )
        } catch (e: Exception) {
            onEvent(SpeedTestEvent.Error(e.message ?: "Unknown error"))
            emptyList()
        }
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

data class TestSetup(
    val configs: List<ProxyConfig>,
    val updatedSubscriptions: List<Subscription>,
    val totalBatches: Int,
    val totalRounds: Int,
    val totalSeconds: Int
)

sealed class LatencyTestEvent {
    data class ParseFailed(val errors: Map<String, String>) : LatencyTestEvent()
    data class ValidateFailed(val errors: Map<String, String>) : LatencyTestEvent()
    data class RoundStarted(val batch: Int, val round: Int, val total: Int) : LatencyTestEvent()
    data class Progress(val tag: String, val delay: Long, val failed: Boolean) : LatencyTestEvent()
    data class RoundEnded(val batch: Int, val round: Int) : LatencyTestEvent()
    data class Error(val message: String) : LatencyTestEvent()
}

sealed class SpeedTestEvent {
    data class RoundStarted(val batch: Int, val round: Int, val total: Int) : SpeedTestEvent()
    data class Progress(val tag: String, val speed: Double, val failed: Boolean) : SpeedTestEvent()
    data class RoundEnded(val batch: Int, val round: Int) : SpeedTestEvent()
    data class Error(val message: String) : SpeedTestEvent()
}
