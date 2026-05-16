package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration

@Database(entities = [SubscriptionEntity::class, SubscriptionDataEntity::class], version = 1)
@ConstructedBy(SubscriptionDatabaseConstructor::class)
abstract class SubscriptionDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SubscriptionDatabaseConstructor : RoomDatabaseConstructor<SubscriptionDatabase>

// Placeholder for future migrations
val SUB_DB_MIGRATIONS = emptyList<Migration>()
