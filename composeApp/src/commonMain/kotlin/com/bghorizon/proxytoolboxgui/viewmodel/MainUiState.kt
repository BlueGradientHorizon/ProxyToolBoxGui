package com.bghorizon.proxytoolboxgui.viewmodel

import com.bghorizon.proxytoolboxgui.data.*

data class MainUiState(
    val currentScreen: Screen = Screen.Main,
    val testProgress: TestProgress = TestProgress(),
    val downloadProgress: DownloadProgress = DownloadProgress(),
    val subscriptions: List<Subscription> = emptyList(),
    val workers: List<WorkerInfo> = emptyList(),
    val appStatus: AppStatus = AppStatus.IDLE,
    val webServerRunning: Boolean = false,
    val workingConfigs: List<ProxyConfig> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val activeDialog: ActiveDialog = ActiveDialog.None
)

sealed class ActiveDialog {
    object None : ActiveDialog()
    object AddSubscription : ActiveDialog()
    data class EditSubscription(val subscription: Subscription) : ActiveDialog()
    data class DeleteConfirmation(val subscription: Subscription) : ActiveDialog()
    object Theme : ActiveDialog()
    object Worker : ActiveDialog()
    object DownloadTimeout : ActiveDialog()
    object LatencyRounds : ActiveDialog()
    object RoundTimeout : ActiveDialog()
    object BatchSize : ActiveDialog()
    object Port : ActiveDialog()
}

enum class Screen {
    Main, Subscriptions, Settings
}
