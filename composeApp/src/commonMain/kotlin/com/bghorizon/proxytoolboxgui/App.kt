package com.bghorizon.proxytoolboxgui

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bghorizon.proxytoolboxgui.data.db.AppDatabase
import com.bghorizon.proxytoolboxgui.data.db.SubscriptionDatabase
import com.bghorizon.proxytoolboxgui.data.SettingsRepository
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
fun App(appDb: AppDatabase, subDb: SubscriptionDatabase) {
    val platform = remember { getPlatform() }
    val settingsRepository = remember { SettingsRepository(appDb.settingsDao()) }
    val subscriptionRepository = remember { SubscriptionRepository(subDb.subscriptionDao()) }
    val testManager = remember { ProxyTestManager(subscriptionRepository) }
    val webServer = remember { ProxyWebServer() }

    LaunchedEffect(Unit) {
        settingsRepository.loadSettings()
    }

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

