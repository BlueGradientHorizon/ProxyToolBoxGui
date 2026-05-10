package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDao
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDataEntity
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionEntity
import com.bghorizon.proxytoolboxgui.data.db.ConfigTestResultUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SubscriptionRepository(private val dao: SubscriptionDao) {

    val subscriptions: Flow<List<Subscription>> = combine(
        dao.getAllSubsFlow(),
        dao.getAllConfigsFlow()
    ) { entities, allConfigs ->
        val dataBySubId = allConfigs.groupBy { it.subId }
        entities.map { entity ->
            val subData = dataBySubId[entity.id] ?: emptyList()
            Subscription(
                id = entity.id,
                note = entity.note,
                url = entity.url,
                total = subData.size,
                working = subData.count { it.working },
                updatedAt = entity.updatedAt,
                duplicated = entity.duplicated,
                parseErr = subData.count { it.parseErr },
                validErr = subData.count { it.validErr }
            )
        }
    }

    suspend fun getAllSubs(): List<Subscription> {
        val entities = dao.getAllSubs()
        val allConfigs = dao.getAllConfigs()

        val dataBySubId = allConfigs.groupBy { it.subId }

        return entities.map { entity ->
            val subData = dataBySubId[entity.id] ?: emptyList()
            Subscription(
                id = entity.id,
                note = entity.note,
                url = entity.url,
                total = subData.size,
                working = subData.count { it.working },
                updatedAt = entity.updatedAt,
                duplicated = entity.duplicated,
                parseErr = subData.count { it.parseErr },
                validErr = subData.count { it.validErr }
            )
        }
    }

    suspend fun saveSub(subscription: Subscription) {
        dao.upsertSubscription(subscription.toEntity())
    }

    suspend fun deleteSub(id: String) {
        dao.deleteSubscription(id)
    }

    suspend fun getWorkingConfigs(): List<ProxyConfig> {
        return dao.getAllConfigs().filter { it.working }.map { data ->
            ProxyConfig(
                tag = "sub-${data.subId}-${data.configId}",
                connURI = data.fixedConnURI ?: data.connURI
            )
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

    suspend fun markConfigParseErr(subId: String, configId: Int) {
        dao.markConfigParseErr(subId, configId)
    }

    suspend fun markConfigsParseErrBatch(ids: List<Pair<String, Int>>) {
        dao.markConfigsParseErrBatch(ids)
    }

    suspend fun markConfigValidErr(subId: String, configId: Int) {
        dao.markConfigValidErr(subId, configId)
    }

    suspend fun markConfigsValidErrBatch(ids: List<Pair<String, Int>>) {
        dao.markConfigsValidErrBatch(ids)
    }

    suspend fun updateConfigTestResult(
        subId: String,
        configId: Int,
        working: Boolean,
        fixedUri: String?
    ) {
        dao.updateConfigTestResult(subId, configId, working, fixedUri)
    }

    suspend fun updateConfigTestResultsBatch(results: List<ConfigTestResultUpdate>) {
        dao.updateConfigTestResultsBatch(results)
    }

    suspend fun resetAllTestData() {
        dao.resetAllTestData()
    }

    suspend fun resetErrorData() {
        dao.resetErrorData()
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
