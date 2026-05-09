package com.bghorizon.proxytoolboxgui.data

import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDao
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionEntity
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionUriEntity
import com.bghorizon.proxytoolboxgui.data.db.WorkingConfigEntity

class SubscriptionRepository(private val dao: SubscriptionDao) {

    suspend fun loadSubscriptions(): List<Subscription> {
        return dao.getAllSubscriptions().map { it.toModel() }
    }

    suspend fun saveSubscriptions(subscriptions: List<Subscription>) {
        dao.deleteAllSubscriptions()
        dao.insertSubscriptions(subscriptions.map { it.toEntity() })
    }

    suspend fun loadWorkingConfigs(): List<ProxyConfig> {
        return dao.getWorkingConfigs().map { ProxyConfig(it.tag, it.connURI) }
    }

    suspend fun saveWorkingConfigs(configs: List<ProxyConfig>) {
        dao.deleteWorkingConfigs()
        dao.saveWorkingConfigs(configs.map { WorkingConfigEntity(tag = it.tag, connURI = it.connURI) })
    }

    suspend fun loadSubscriptionUris(subId: String): List<String> {
        return dao.getUrisForSubscription(subId).map { it.uri }
    }

    suspend fun saveSubscriptionUris(subId: String, uris: List<String>) {
        dao.deleteUrisForSubscription(subId)
        dao.insertUris(uris.map { SubscriptionUriEntity(subId, it) })
    }

    suspend fun deleteSubscriptionUris(subId: String) {
        dao.deleteUrisForSubscription(subId)
    }
}

private fun Subscription.toEntity() = SubscriptionEntity(
    id = id,
    note = note,
    url = url,
    total = total,
    working = working,
    updatedAt = updatedAt,
    duplicated = duplicated,
    parseErr = parseErr,
    validErr = validErr
)

private fun SubscriptionEntity.toModel() = Subscription(
    id = id,
    note = note,
    url = url,
    total = total,
    working = working,
    updatedAt = updatedAt,
    duplicated = duplicated,
    parseErr = parseErr,
    validErr = validErr
)
