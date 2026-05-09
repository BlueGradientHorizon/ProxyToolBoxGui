package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

expect abstract class PlatformContext

expect fun getAppDatabaseBuilder(ctx: PlatformContext): RoomDatabase.Builder<AppDatabase>
expect fun getSubscriptionDatabaseBuilder(ctx: PlatformContext): RoomDatabase.Builder<SubscriptionDatabase>


fun createAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*APP_DB_MIGRATIONS.toTypedArray())
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .build()
}

fun createSubscriptionDatabase(builder: RoomDatabase.Builder<SubscriptionDatabase>): SubscriptionDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*SUB_DB_MIGRATIONS.toTypedArray())
        .fallbackToDestructiveMigrationOnDowngrade(true)
        .build()
}
