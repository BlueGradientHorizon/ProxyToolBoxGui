package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

@Database(
    entities = [AppSettingsEntity::class],
    version = 1,
//    autoMigrations = [
//        AutoMigration(from = 1, to = 2)
//    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
}

// Placeholder for future migrations
val APP_DB_MIGRATIONS = emptyList<Migration>()
