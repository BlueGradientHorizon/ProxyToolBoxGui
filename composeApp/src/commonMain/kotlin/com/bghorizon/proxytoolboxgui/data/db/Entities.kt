package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val theme: Int,
    val selectedWorker: String,
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
    val total: Int,
    val working: Int,
    val updatedAt: Long,
    val duplicated: Int,
    val parseErr: Int,
    val validErr: Int
)

@Entity(
    tableName = "subscription_uris",
    primaryKeys = ["subId", "uri"],
    foreignKeys = [ForeignKey(
        entity = SubscriptionEntity::class,
        parentColumns = ["id"],
        childColumns = ["subId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SubscriptionUriEntity(
    val subId: String,
    val uri: String
)

@Entity(tableName = "working_configs")
data class WorkingConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tag: String,
    val connURI: String
)
