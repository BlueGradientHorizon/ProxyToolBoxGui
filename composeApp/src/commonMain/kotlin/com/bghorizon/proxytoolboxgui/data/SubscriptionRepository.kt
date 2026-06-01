package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDao
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDataEntity
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionEntity
import com.bghorizon.proxytoolboxgui.data.db.ConfigTestResultUpdate
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
                    speedPassed = stats.speedPassed,
                    updatedAt = stats.updatedAt,
                    duplicated = stats.duplicated,
                    parseErr = stats.parseErr,
                    validErr = stats.validErr,
                    includeInTest = stats.includeInTest,
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

    suspend fun getAllSubs(): List<Subscription> {
        return dao.getAllSubs().map { entity ->
            Subscription(
                id = entity.id,
                note = entity.note,
                url = entity.url,
                updatedAt = entity.updatedAt,
                duplicated = entity.duplicated,
                includeInTest = entity.includeInTest
            )
        }
    }

    suspend fun deleteSub(id: String) {
        dao.deleteSubscription(id)
    }

    suspend fun deleteSubs(ids: List<String>) {
        dao.deleteSubscriptions(ids)
    }

    suspend fun getWorkingConfigs(): List<ProxyConfig> {
        return dao.getAllConfigs().filter { it.working }.map { data ->
            ProxyConfig(
                tag = "sub-${data.subId}-${data.configId}",
                connURI = data.fixedConnURI ?: data.connURI,
                delay = data.delay,
                speed = data.speed
            )
        }
    }

    suspend fun getConfigs(subId: String): List<SubscriptionDataEntity> {
        return dao.getConfigs(subId)
    }

    suspend fun setConfigs(subId: String, configs: List<SubscriptionDataEntity>) {
        dao.setConfigsUris(subId, configs)
    }

    suspend fun setConfigsUris(subId: String, uris: List<String>) {
        val entities = uris.mapIndexed { index, uri ->
            SubscriptionDataEntity(
                subId = subId,
                configId = index,
                connURI = uri,
                parseErr = false,
                validErr = false,
                fixedConnURI = null,
                working = false,
                delay = -1,
                speed = -1
            )
        }
        dao.setConfigsUris(subId, entities)
    }

    suspend fun markConfigsParseErrBatch(ids: List<Pair<String, Int>>) {
        dao.markConfigsParseErrBatch(ids)
    }

    suspend fun markConfigsValidErrBatch(ids: List<Pair<String, Int>>) {
        dao.markConfigsValidErrBatch(ids)
    }

    suspend fun updateConfigTestResultsBatch(results: List<ConfigTestResultUpdate>) {
        dao.updateConfigTestResultsBatch(results)
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

    suspend fun resetDuplicatedData() {
        dao.resetDuplicatedData()
    }
}

private fun Subscription.toEntity() = SubscriptionEntity(
    id = id,
    note = note,
    url = url,
    updatedAt = updatedAt,
    duplicated = duplicated,
    includeInTest = includeInTest,
)
