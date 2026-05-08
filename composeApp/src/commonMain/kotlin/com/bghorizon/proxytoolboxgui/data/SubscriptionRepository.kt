package com.bghorizon.proxytoolboxgui.data

import kotlinx.serialization.encodeToString

class SubscriptionRepository(private val store: SettingsStore) {

    private fun subUriKey(subId: String) = "sub_uris_$subId"

    suspend fun loadSubscriptions(): List<Subscription> {
        val json = store.getString("subscriptions", "[]")
        return try {
            JsonConfig.json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveSubscriptions(subscriptions: List<Subscription>) {
        val json = JsonConfig.json.encodeToString(subscriptions)
        store.putString("subscriptions", json)
    }

    suspend fun loadWorkingConfigs(): List<ProxyConfig> {
        return try {
            val json = store.getString("working_configs", "[]")
            JsonConfig.json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveWorkingConfigs(configs: List<ProxyConfig>) {
        val json = JsonConfig.json.encodeToString(configs)
        store.putString("working_configs", json)
    }

    suspend fun loadSubscriptionUris(subId: String): List<String> {
        val json = store.getString(subUriKey(subId), "[]")
        return try {
            JsonConfig.json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveSubscriptionUris(subId: String, uris: List<String>) {
        val json = JsonConfig.json.encodeToString(uris)
        store.putString(subUriKey(subId), json)
    }

    suspend fun deleteSubscriptionUris(subId: String) {
        store.putString(subUriKey(subId), "[]")
    }
}
