package com.bghorizon.proxytoolboxgui.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppStatusInfo(
    val status: AppStatus = AppStatus.IDLE,
    val description: String? = null
)

class AppStatusManager {
    private val _statusInfo = MutableStateFlow(AppStatusInfo())
    val statusInfo: StateFlow<AppStatusInfo> = _statusInfo.asStateFlow()

    fun updateStatus(status: AppStatus, description: String? = null) {
        _statusInfo.update { it.copy(status = status, description = description) }
    }
}
