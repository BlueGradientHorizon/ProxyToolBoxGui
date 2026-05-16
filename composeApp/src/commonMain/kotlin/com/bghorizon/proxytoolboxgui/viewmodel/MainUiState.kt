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
    fun TopBar(mainVm: MainViewModel)

    @Composable
    fun Content(mainVm: MainViewModel)

    @Composable
    fun FAB(mainVm: MainViewModel)
}

data class MainUiState(
    val screen: AppScreen,
    val appStatus: AppStatus = AppStatus.IDLE,
    val statusDescription: String? = null,
    val webServerRunning: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val workers: List<WorkerInfo> = emptyList(),
    val activeDialog: UiDialog? = null,
    val isDynamicColorSupported: Boolean = false,
    val isQrScannerSupported: Boolean = false,
)
