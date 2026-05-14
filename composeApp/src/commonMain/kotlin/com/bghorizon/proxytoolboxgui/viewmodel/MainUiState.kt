package com.bghorizon.proxytoolboxgui.viewmodel

import com.bghorizon.proxytoolboxgui.data.*

/** Marker interface for any dialog state in the app */
interface UiDialog

/** Marker interface for any contextual mode in the app */
interface UiMode

sealed interface MainMode : UiMode {
    data object Normal : MainMode
}

sealed interface SubscriptionMode : UiMode {
    data object Normal : SubscriptionMode
    data object Selection : SubscriptionMode
}

sealed interface SettingsMode : UiMode {
    data object Normal : SettingsMode
}

data class MainUiState(
    val currentScreen: Screen = Screen.Main,
    val testProgress: TestProgress = TestProgress(),
    val subsUpdateProgress: SubsUpdateProgress = SubsUpdateProgress(),
    val workers: List<WorkerInfo> = emptyList(),
    val appStatus: AppStatus = AppStatus.IDLE,
    val webServerRunning: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val isDynamicColorSupported: Boolean = false,
    val isQrScannerSupported: Boolean = false,
    val activeDialog: UiDialog? = null,
    val mainMode: MainMode = MainMode.Normal,
    val subscriptionMode: SubscriptionMode = SubscriptionMode.Normal,
    val settingsMode: SettingsMode = SettingsMode.Normal,
    val selectedSubscriptionIds: Set<String> = emptySet(),
    val updatingSubscriptionsIds: Set<String> = emptySet(),
)

enum class Screen {
    Main, Subscriptions, Settings
}
