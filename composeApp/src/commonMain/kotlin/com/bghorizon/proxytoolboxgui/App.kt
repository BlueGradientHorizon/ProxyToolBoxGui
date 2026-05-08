package com.bghorizon.proxytoolboxgui

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bghorizon.proxytoolboxgui.data.SettingsRepository
import com.bghorizon.proxytoolboxgui.data.SettingsStore
import com.bghorizon.proxytoolboxgui.data.SubscriptionRepository
import com.bghorizon.proxytoolboxgui.data.ProxyTestManager
import com.bghorizon.proxytoolboxgui.data.ProxyWebServer
import com.bghorizon.proxytoolboxgui.platform.getPlatform
import com.bghorizon.proxytoolboxgui.ui.screens.MainScreen
import com.bghorizon.proxytoolboxgui.ui.screens.SettingsScreen
import com.bghorizon.proxytoolboxgui.ui.screens.SubscriptionsScreen
import com.bghorizon.proxytoolboxgui.ui.theme.AppTheme
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import com.bghorizon.proxytoolboxgui.viewmodel.Screen

@Composable
fun App() {
    val platform = remember { getPlatform() }
    val settingsStore = remember { createSettingsStore(platform) }
    val settingsRepository = remember { SettingsRepository(settingsStore) }
    val subscriptionRepository = remember { SubscriptionRepository(settingsStore) }
    val testManager = remember { ProxyTestManager(subscriptionRepository) }
    val webServer = remember { ProxyWebServer() }

    val viewModel: MainViewModel = viewModel {
        MainViewModel(platform, settingsRepository, subscriptionRepository, testManager, webServer)
    }

    val uiState by viewModel.uiState.collectAsState()

    AppTheme(themeMode = uiState.settings.theme) {
        when (uiState.currentScreen) {
            Screen.Main -> MainScreen(viewModel)
            Screen.Subscriptions -> SubscriptionsScreen(viewModel)
            Screen.Settings -> SettingsScreen(viewModel)
        }
    }
}

expect fun createSettingsStore(platform: com.bghorizon.proxytoolboxgui.platform.Platform): SettingsStore