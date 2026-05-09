package com.bghorizon.proxytoolboxgui.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual typealias PlatformContext = Context

actual fun getAppDatabaseBuilder(ctx: PlatformContext): RoomDatabase.Builder<AppDatabase> {
    val dbFile = ctx.getDatabasePath("app_settings.db")
    return Room.databaseBuilder<AppDatabase>(
        context = ctx,
        name = dbFile.absolutePath
    )
}

actual fun getSubscriptionDatabaseBuilder(ctx: PlatformContext): RoomDatabase.Builder<SubscriptionDatabase> {
    val dbFile = ctx.getDatabasePath("subscriptions.db")
    return Room.databaseBuilder<SubscriptionDatabase>(
        context = ctx,
        name = dbFile.absolutePath
    )
}
