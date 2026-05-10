package com.bghorizon.proxytoolboxgui.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual abstract class PlatformContext

actual fun getAppDatabaseBuilder(ctx: PlatformContext): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".proxytoolboxgui/app_settings.db")
    dbFile.parentFile.mkdirs()
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
}

actual fun getSubscriptionDatabaseBuilder(ctx: PlatformContext): RoomDatabase.Builder<SubscriptionDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".proxytoolboxgui/subscriptions.db")
    dbFile.parentFile.mkdirs()
    return Room.databaseBuilder<SubscriptionDatabase>(
        name = dbFile.absolutePath,
    )
}
