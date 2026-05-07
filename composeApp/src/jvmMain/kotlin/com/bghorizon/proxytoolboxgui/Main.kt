package com.bghorizon.proxytoolboxgui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bghorizon.proxytoolboxgui.data.NativeLoader

fun main() {
    NativeLoader.init()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ProxyToolBoxGui",
            state = rememberWindowState()
        ) {
            App()
        }
    }
}
