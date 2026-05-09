package com.bghorizon.proxytoolboxgui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.remember
import com.bghorizon.proxytoolboxgui.data.NativeLoader
import com.bghorizon.proxytoolboxgui.data.db.PlatformContext
import com.bghorizon.proxytoolboxgui.data.db.createAppDatabase
import com.bghorizon.proxytoolboxgui.data.db.createSubscriptionDatabase
import com.bghorizon.proxytoolboxgui.data.db.getAppDatabaseBuilder
import com.bghorizon.proxytoolboxgui.data.db.getSubscriptionDatabaseBuilder

fun main() {
    NativeLoader.init()
    application {
        val appDb = remember { createAppDatabase(getAppDatabaseBuilder(object : PlatformContext() {})) }
        val subDb = remember { createSubscriptionDatabase(getSubscriptionDatabaseBuilder(object : PlatformContext() {})) }

        Window(
            onCloseRequest = ::exitApplication,
            title = "ProxyToolBoxGui",
            state = rememberWindowState()
        ) {
            App(appDb, subDb)
        }
    }
}
