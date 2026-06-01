package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 0")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY rowid ASC")
    fun getAllSubsFlow(): kotlinx.coroutines.flow.Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY rowid ASC")
    suspend fun getAllSubs(): List<SubscriptionEntity>

    @Upsert
    suspend fun upsertSubscription(subscription: SubscriptionEntity)

    @Transaction
    @Upsert
    suspend fun upsertSubscriptions(subscriptions: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: String)

    @Transaction
    @Query("DELETE FROM subscriptions WHERE id IN (:ids)")
    suspend fun deleteSubscriptions(ids: List<String>)

    @Query("SELECT * FROM subscriptions_data WHERE subId = :subId")
    suspend fun getConfigs(subId: String): List<SubscriptionDataEntity>

    @Query("SELECT * FROM subscriptions_data")
    fun getAllConfigsFlow(): kotlinx.coroutines.flow.Flow<List<SubscriptionDataEntity>>

    @Query("SELECT * FROM subscriptions_data")
    suspend fun getAllConfigs(): List<SubscriptionDataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<SubscriptionDataEntity>)

    @Transaction
    suspend fun setConfigsUris(subId: String, configs: List<SubscriptionDataEntity>) {
        deleteConfigsBySubId(subId)
        insertConfigs(configs)
    }

    @Query("DELETE FROM subscriptions_data WHERE subId = :subId")
    suspend fun deleteConfigsBySubId(subId: String)


    @Query("UPDATE subscriptions_data SET working = :working, fixedConnURI = :fixedUri, delay = :delay WHERE subId = :subId AND configId = :configId")
    suspend fun updateConfigTestResult(
        subId: String,
        configId: Int,
        working: Boolean,
        fixedUri: String?,
        delay: Long,
    )

    @Query("UPDATE subscriptions_data SET speed = :speed WHERE subId = :subId AND configId = :configId")
    suspend fun updateConfigSpeedResult(subId: String, configId: Int, speed: Long)

    @Transaction
    suspend fun updateConfigTestResultsBatch(results: List<ConfigTestResultUpdate>) {
        results.forEach {
            if (it.speed != null) {
                updateConfigSpeedResult(it.subId, it.configId, it.speed)
            } else {
                updateConfigTestResult(
                    it.subId,
                    it.configId,
                    it.working ?: false,
                    it.fixedUri,
                    it.delay ?: -1
                )
            }
        }
    }

    @Query("UPDATE subscriptions_data SET parseErr = 1 WHERE subId = :subId AND configId = :configId")
    suspend fun markConfigParseErr(subId: String, configId: Int)

    @Transaction
    suspend fun markConfigsParseErrBatch(ids: List<Pair<String, Int>>) {
        ids.forEach { markConfigParseErr(it.first, it.second) }
    }

    @Query("UPDATE subscriptions_data SET parseErr = 0")
    suspend fun resetParseErrorData()

    @Query("UPDATE subscriptions_data SET validErr = 1 WHERE subId = :subId AND configId = :configId")
    suspend fun markConfigValidErr(subId: String, configId: Int)

    @Transaction
    suspend fun markConfigsValidErrBatch(ids: List<Pair<String, Int>>) {
        ids.forEach { markConfigValidErr(it.first, it.second) }
    }

    @Query("UPDATE subscriptions_data SET validErr = 0")
    suspend fun resetValidErrorData()

    @Query("UPDATE subscriptions_data SET working = 0, fixedConnURI = NULL, delay = -1, speed = -1")
    suspend fun resetWorkingData()

    @Query("UPDATE subscriptions SET duplicated = 0")
    suspend fun resetDuplicatedData()

    @Query(
        """
        SELECT s.*, 
               COUNT(d.configId) as total,
               SUM(CASE WHEN d.working = 1 THEN 1 ELSE 0 END) as working,
               SUM(CASE WHEN d.speed > 0 THEN 1 ELSE 0 END) as speedPassed,
               SUM(CASE WHEN d.parseErr = 1 THEN 1 ELSE 0 END) as parseErr,
               SUM(CASE WHEN d.validErr = 1 THEN 1 ELSE 0 END) as validErr
        FROM subscriptions s 
        LEFT JOIN subscriptions_data d ON s.id = d.subId
        GROUP BY s.id
        ORDER BY s.rowid ASC
    """
    )
    fun getSubscriptionsWithStatsFlow(): kotlinx.coroutines.flow.Flow<List<SubscriptionWithStats>>
}

data class SubscriptionWithStats(
    val id: String,
    val note: String,
    val url: String,
    val updatedAt: Long,
    val duplicated: Int,
    val includeInTest: Boolean,
    val total: Int,
    val working: Int,
    val speedPassed: Int,
    val parseErr: Int,
    val validErr: Int
)

data class ConfigTestResultUpdate(
    val subId: String,
    val configId: Int,
    val working: Boolean? = null,
    val fixedUri: String? = null,
    val delay: Long? = null,
    val speed: Long? = null
)
