package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDao
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDataEntity
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionEntity

class SubscriptionRepository(private val dao: SubscriptionDao) {

    suspend fun loadSubscriptions(): List<Subscription> {
        val entities = dao.getAllSubscriptions()
        val allData = dao.getAllData()
        
        val dataBySubId = allData.groupBy { it.subId }
        
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

    suspend fun saveSubscriptions(subscriptions: List<Subscription>) {
        dao.upsertSubscriptions(subscriptions.map { it.toEntity() })
    }

    suspend fun saveSubscription(subscription: Subscription) {
        dao.upsertSubscription(subscription.toEntity())
    }

    suspend fun deleteSubscription(id: String) {
        dao.deleteSubscription(id)
    }

    suspend fun loadWorkingConfigs(): List<ProxyConfig> {
        return dao.getAllData().filter { it.working }.map { data ->
            ProxyConfig(
                tag = "sub-${data.subId}-${data.configId}",
                connURI = data.fixedConnURI ?: data.connURI
            )
        }
    }

    suspend fun loadSubscriptionUris(subId: String): List<String> {
        return dao.getDataForSubscription(subId).map { it.connURI }
    }

    suspend fun saveSubscriptionData(subId: String, uris: List<String>) {
        val entities = uris.mapIndexed { index, uri ->
            SubscriptionDataEntity(subId = subId, configId = index, connURI = uri)
        }
        dao.deleteAndInsertData(subId, entities)
    }

    suspend fun deleteSubscriptionData(subId: String) {
        dao.deleteDataForSubscription(subId)
    }
    
    suspend fun markParseErr(subId: String, configId: Int) {
        dao.markParseErr(subId, configId)
    }

    suspend fun markValidErr(subId: String, configId: Int) {
        dao.markValidErr(subId, configId)
    }

    suspend fun updateTestResult(subId: String, configId: Int, working: Boolean, fixedUri: String?) {
        dao.updateTestResult(subId, configId, working, fixedUri)
    }

    suspend fun resetTestData() {
        dao.resetAllTestData()
    }
}

private fun Subscription.toEntity() = SubscriptionEntity(
    id = id,
    note = note,
    url = url,
    updatedAt = updatedAt,
    duplicated = duplicated
)
