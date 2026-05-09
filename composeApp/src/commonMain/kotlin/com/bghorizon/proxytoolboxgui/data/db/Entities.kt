package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val theme: Int,
    val selectedWorker: String,
    val selectedWorkerName: String = "",
    val downloadTimeout: Int,
    val performDedup: Boolean,
    val latencyRounds: Int,
    val roundTimeout: Int,
    val testByBatches: Boolean,
    val batchSize: Int,
    val autoStartWebServer: Boolean,
    val webServerPort: Int,
    val webServerLocalhost: Boolean
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
    val parseErr: Boolean = false,
    val validErr: Boolean = false,
    val fixedConnURI: String? = null,
    val working: Boolean = false
)
