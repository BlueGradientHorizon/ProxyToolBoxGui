package com.bghorizon.proxytoolboxgui.di

import androidx.compose.runtime.staticCompositionLocalOf
import com.bghorizon.proxytoolboxgui.data.ProxyTestManager
import com.bghorizon.proxytoolboxgui.data.ProxyWebServer
import com.bghorizon.proxytoolboxgui.data.SettingsRepository
import com.bghorizon.proxytoolboxgui.data.SubscriptionRepository
import com.bghorizon.proxytoolboxgui.platform.Platform

class AppModule(
    val settingsRepository: SettingsRepository,
    val subscriptionRepository: SubscriptionRepository,
    val testManager: ProxyTestManager,
    val webServer: ProxyWebServer,
    val platform: Platform
)

val LocalAppModule = staticCompositionLocalOf<AppModule> {
    error("No AppModule provided")
}
