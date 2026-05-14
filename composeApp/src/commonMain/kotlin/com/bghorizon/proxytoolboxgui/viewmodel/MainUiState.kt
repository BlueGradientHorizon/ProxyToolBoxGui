package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.compose.runtime.Composable
import com.bghorizon.proxytoolboxgui.data.*

/** Marker interface for any dialog state in the app */
interface UiDialog

/** Marker for all screen modes. Enforces that every screen has a 'Normal' mode. */
interface ScreenUiMode {
    interface Normal : ScreenUiMode
}

/**
 * Represents the full state and rendering logic of a screen.
 */
interface AppScreen {
    val mode: ScreenUiMode

    @Composable
    fun TopBar(viewModel: MainViewModel)

    @Composable
    fun Content(viewModel: MainViewModel)

    @Composable
    fun FAB(viewModel: MainViewModel)
}

data class MainUiState(
    val screen: AppScreen,
    val testProgress: TestProgress = TestProgress(),
    val subsUpdateProgress: SubsUpdateProgress = SubsUpdateProgress(),
    val workers: List<WorkerInfo> = emptyList(),
    val appStatus: AppStatus = AppStatus.IDLE,
    val webServerRunning: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val isDynamicColorSupported: Boolean = false,
    val isQrScannerSupported: Boolean = false,
    val activeDialog: UiDialog? = null,
    val updatingSubscriptionsIds: Set<String> = emptySet(),
)
