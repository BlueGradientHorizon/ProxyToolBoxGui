package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

    @Upsert
    suspend fun upsertSubscriptions(subscriptions: List<SubscriptionEntity>)

    @Upsert
    suspend fun upsertSubscription(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAllSubscriptions()

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: String)

    @Query("SELECT * FROM subscriptions_data WHERE subId = :subId")
    suspend fun getDataForSubscription(subId: String): List<SubscriptionDataEntity>

    @Query("SELECT * FROM subscriptions_data")
    suspend fun getAllData(): List<SubscriptionDataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertData(data: List<SubscriptionDataEntity>)

    @Transaction
    suspend fun deleteAndInsertData(subId: String, data: List<SubscriptionDataEntity>) {
        deleteDataForSubscription(subId)
        insertData(data)
    }

    @Query("DELETE FROM subscriptions_data WHERE subId = :subId")
    suspend fun deleteDataForSubscription(subId: String)

    @Query("UPDATE subscriptions_data SET parseErr = 1 WHERE subId = :subId AND configId = :configId")
    suspend fun markParseErr(subId: String, configId: Int)

    @Query("UPDATE subscriptions_data SET validErr = 1 WHERE subId = :subId AND configId = :configId")
    suspend fun markValidErr(subId: String, configId: Int)

    @Query("UPDATE subscriptions_data SET working = :working, fixedConnURI = :fixedUri WHERE subId = :subId AND configId = :configId")
    suspend fun updateTestResult(subId: String, configId: Int, working: Boolean, fixedUri: String?)

    @Query("UPDATE subscriptions_data SET working = 0, parseErr = 0, validErr = 0, fixedConnURI = NULL")
    suspend fun resetAllTestData()
}
