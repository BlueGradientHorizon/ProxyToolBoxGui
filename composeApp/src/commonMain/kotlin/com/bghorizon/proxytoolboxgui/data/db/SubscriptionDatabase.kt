package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

@Database(entities = [SubscriptionEntity::class, SubscriptionUriEntity::class, WorkingConfigEntity::class], version = 1)
abstract class SubscriptionDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
}

// Placeholder for future migrations
val SUB_DB_MIGRATIONS = emptyList<Migration>()
