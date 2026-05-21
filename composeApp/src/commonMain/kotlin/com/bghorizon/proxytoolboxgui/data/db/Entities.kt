package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val theme: Int,
    val dynamicColor: Boolean,
    val selectedWorker: String,
    val selectedWorkerName: String,
    val downloadTimeout: Int,
    val performDedup: Boolean,
    val latencyRounds: Int,
    val roundTimeout: Int,
    val testByBatches: Boolean,
    val batchSize: Int,
    val autoStartWebServer: Boolean,
    val webServerPort: Int,
    val webServerLocalhost: Boolean,
    val testUrl: String,
    val parallelSubscriptionDownloads: Int,
    val lowMemMode: Boolean,
    val sortProfilesByDelay: Boolean,
    val performSpeedTests: Boolean,
    val speedTestRounds: Int,
    val speedTestProvider: String,
    val speedTestMode: String,
    val speedTestTargetBytes: Long,
    val sortByLatencyDelay: Boolean
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val note: String,
    val url: String,
    val updatedAt: Long,
    val duplicated: Int
)

@Entity(
    tableName = "subscriptions_data",
    primaryKeys = ["subId", "configId"],
    foreignKeys = [ForeignKey(
        entity = SubscriptionEntity::class,
        parentColumns = ["id"],
        childColumns = ["subId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SubscriptionDataEntity(
    val subId: String,
    val configId: Int,
    val connURI: String,
    val parseErr: Boolean,
    val validErr: Boolean,
    val fixedConnURI: String?,
    val working: Boolean,
    val delay: Long,
    val speed: Long
)
