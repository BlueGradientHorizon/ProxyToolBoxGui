package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 0")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions")
    suspend fun getAllSubscriptions(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscriptions(subscriptions: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAllSubscriptions()

    @Query("SELECT * FROM subscription_uris WHERE subId = :subId")
    suspend fun getUrisForSubscription(subId: String): List<SubscriptionUriEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUris(uris: List<SubscriptionUriEntity>)

    @Query("DELETE FROM subscription_uris WHERE subId = :subId")
    suspend fun deleteUrisForSubscription(subId: String)

    @Query("SELECT * FROM working_configs")
    suspend fun getWorkingConfigs(): List<WorkingConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWorkingConfigs(configs: List<WorkingConfigEntity>)

    @Query("DELETE FROM working_configs")
    suspend fun deleteWorkingConfigs()
}
