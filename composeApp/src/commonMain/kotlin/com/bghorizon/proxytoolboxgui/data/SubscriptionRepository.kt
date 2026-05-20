package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDao
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDataEntity
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionEntity
import com.bghorizon.proxytoolboxgui.data.db.ConfigLatencyTestResultUpdate
import com.bghorizon.proxytoolboxgui.data.db.ConfigSpeedTestResultUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class SubscriptionRepository(private val dao: SubscriptionDao) {

    val subscriptions: Flow<List<Subscription>> = dao.getSubscriptionsWithStatsFlow()
        .map { list ->
            list.map { stats ->
                Subscription(
                    id = stats.id,
                    note = stats.note,
                    url = stats.url,
                    total = stats.total,
                    working = stats.working,
                    workingSpeed = stats.workingSpeed,
                    updatedAt = stats.updatedAt,
                    duplicated = stats.duplicated,
                    parseErr = stats.parseErr,
                    validErr = stats.validErr,
                )
            }
        }
        .flowOn(Dispatchers.Default)

    suspend fun saveSub(subscription: Subscription) {
        dao.upsertSubscription(subscription.toEntity())
    }

    suspend fun saveSubs(subscriptions: List<Subscription>) {
        dao.upsertSubscriptions(subscriptions.map { it.toEntity() })
    }

    suspend fun deleteSub(id: String) {
        dao.deleteSubscription(id)
    }

    suspend fun deleteSubs(ids: List<String>) {
        dao.deleteSubscriptions(ids)
    }

    suspend fun getWorkingConfigs(requireSpeedTestPass: Boolean = false): List<ProxyConfig> {
        val allConfigs = dao.getAllConfigs()
        return if (requireSpeedTestPass) {
            allConfigs.filter { it.workingSpeed }.map { data ->
                ProxyConfig(
                    tag = "sub-${data.subId}-${data.configId}",
                    connURI = data.fixedConnURI ?: data.connURI,
                    delay = data.delay,
                    speed = data.speed
                )
            }
        } else {
            allConfigs.filter { it.working }.map { data ->
                ProxyConfig(
                    tag = "sub-${data.subId}-${data.configId}",
                    connURI = data.fixedConnURI ?: data.connURI,
                    delay = data.delay,
                    speed = data.speed
                )
            }
        }
    }

    suspend fun getConfigsUris(subId: String): List<String> {
        return dao.getConfigs(subId).map { it.connURI }
    }

    suspend fun setConfigsUris(subId: String, uris: List<String>) {
        val entities = uris.mapIndexed { index, uri ->
            SubscriptionDataEntity(subId = subId, configId = index, connURI = uri)
        }
        dao.setConfigsUris(subId, entities)
    }

    suspend fun markConfigsParseErrBatch(ids: List<Pair<String, Int>>) {
        dao.markConfigsParseErrBatch(ids)
    }

    suspend fun markConfigsValidErrBatch(ids: List<Pair<String, Int>>) {
        dao.markConfigsValidErrBatch(ids)
    }

    suspend fun updateConfigLatencyTestResultsBatch(results: List<ConfigLatencyTestResultUpdate>) {
        dao.updateConfigLatencyTestResultsBatch(results)
    }

    suspend fun updateConfigSpeedTestResultsBatch(results: List<ConfigSpeedTestResultUpdate>) {
        dao.updateConfigSpeedTestResultsBatch(results)
    }

    suspend fun resetParseErrorData() {
        dao.resetParseErrorData()
    }

    suspend fun resetValidErrorData() {
        dao.resetValidErrorData()
    }

    suspend fun resetWorkingData() {
        dao.resetWorkingData()
    }
}

private fun Subscription.toEntity() = SubscriptionEntity(
    id = id,
    note = note,
    url = url,
    updatedAt = updatedAt,
    duplicated = duplicated
)
