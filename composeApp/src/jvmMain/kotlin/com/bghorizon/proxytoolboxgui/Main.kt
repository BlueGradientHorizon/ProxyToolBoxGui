package com.bghorizon.proxytoolboxgui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.data.NativeLoader
import com.bghorizon.proxytoolboxgui.data.db.PlatformContext
import com.bghorizon.proxytoolboxgui.data.db.createAppDatabase
import com.bghorizon.proxytoolboxgui.data.db.createSubscriptionDatabase
import com.bghorizon.proxytoolboxgui.data.db.getAppDatabaseBuilder
import com.bghorizon.proxytoolboxgui.data.db.getSubscriptionDatabaseBuilder
import com.bghorizon.proxytoolboxgui.platform.JVMPlatform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.Res
import proxytoolboxgui.composeapp.generated.resources.app_name
import proxytoolboxgui.composeapp.generated.resources.ic_launcher_playstore

fun main() {
    NativeLoader.init()
    application {
        val trayState = rememberTrayState()

        LaunchedEffect(trayState) {
            JVMPlatform.trayState = trayState
        }

        Tray(
            state = trayState,
            icon = painterResource(Res.drawable.ic_launcher_playstore),
            tooltip = stringResource(Res.string.app_name)
        )

        val appDb = remember {
            createAppDatabase(getAppDatabaseBuilder(object : PlatformContext() {}))
        }
        val subDb = remember {
            createSubscriptionDatabase(
                getSubscriptionDatabaseBuilder(
                    object : PlatformContext() {},
                ),
            )
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.ic_launcher_playstore),
            state = rememberWindowState(
                width = 480.dp,
                height = 800.dp
            )
        ) {
            App(appDb, subDb)
        }
    }
}
