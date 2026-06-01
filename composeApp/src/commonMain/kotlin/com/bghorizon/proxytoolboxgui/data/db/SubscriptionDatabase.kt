package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(entities = [SubscriptionEntity::class, SubscriptionDataEntity::class], version = 2)
@ConstructedBy(SubscriptionDatabaseConstructor::class)
abstract class SubscriptionDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SubscriptionDatabaseConstructor : RoomDatabaseConstructor<SubscriptionDatabase>

val MIGRATION_SUBSCRIPTION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE subscriptions ADD COLUMN includeInTest INTEGER NOT NULL DEFAULT 1"
        )
    }
}

val SUB_DB_MIGRATIONS = listOf(MIGRATION_SUBSCRIPTION_1_2)
