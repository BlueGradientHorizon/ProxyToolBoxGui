package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.utils.ConfigUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyTestManager(
    private val subscriptionRepository: SubscriptionRepository
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
                val tag = "sub-${sub.id}-${uriIndex}"
                configs.add(ProxyConfig(tag = tag, connURI = uri))
            }
        }

        val totalBatches = if (settings.testByBatches && settings.batchSize > 0) {
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
        onEvent: (TestEvent) -> Unit
    ): List<ProxyConfig> = withContext(Dispatchers.IO) {
        GoBridge.runLatencyTests(
            workerPath = settings.selectedWorker,
            testUrl = settings.testUrl,
            settings = settings,
            callback = object : GoTestCallback {
                override fun onParseFailed(errors: Map<String, String>) {
                    onEvent(TestEvent.ParseFailed(errors))
                }

                override fun onValidateFailed(errors: Map<String, String>) {
                    onEvent(TestEvent.ValidateFailed(errors))
                }

                override fun onRoundStarted(batch: Long, round: Long, total: Long) {
                    onEvent(TestEvent.RoundStarted(batch.toInt(), round.toInt(), total.toInt()))
                }

                override fun onProgress(tag: String, delay: Long, failed: Boolean) {
                    onEvent(TestEvent.Progress(tag, delay, failed))
                }

                override fun onRoundEnded(batch: Long, round: Long) {
                    onEvent(TestEvent.RoundEnded(batch.toInt(), round.toInt()))
                }

                override fun onError(message: String) {
                    onEvent(TestEvent.Error(message))
                }
            },
            connUris = configs
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

data class TestSetup(
    val configs: List<ProxyConfig>,
    val updatedSubscriptions: List<Subscription>,
    val totalBatches: Int,
    val totalRounds: Int,
    val totalSeconds: Int
)

sealed class TestEvent {
    data class ParseFailed(val errors: Map<String, String>) : TestEvent()
    data class ValidateFailed(val errors: Map<String, String>) : TestEvent()
    data class RoundStarted(val batch: Int, val round: Int, val total: Int) : TestEvent()
    data class Progress(val tag: String, val delay: Long, val failed: Boolean) : TestEvent()
    data class RoundEnded(val batch: Int, val round: Int) : TestEvent()
    data class Error(val message: String) : TestEvent()
}
