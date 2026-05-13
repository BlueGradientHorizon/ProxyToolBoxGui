package com.bghorizon.proxytoolboxgui.viewmodel

import com.bghorizon.proxytoolboxgui.data.*

/** Marker interface for any dialog state in the app */
interface UiDialog

data class MainUiState(
    val currentScreen: Screen = Screen.Main,
    val testProgress: TestProgress = TestProgress(),
    val subsUpdateProgress: SubsUpdateProgress = SubsUpdateProgress(),
    val workers: List<WorkerInfo> = emptyList(),
    val appStatus: AppStatus = AppStatus.IDLE,
    val webServerRunning: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val isDynamicColorSupported: Boolean = false,
    val activeDialog: UiDialog? = null,
    val updatingSubscriptionsIds: Set<String> = emptySet()
)

enum class Screen {
    Main, Subscriptions, Settings
}
