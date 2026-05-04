package com.bghorizon.proxytoolboxgui

import com.bghorizon.proxytoolboxgui.data.SettingsStore
import com.bghorizon.proxytoolboxgui.platform.Platform

actual fun createSettingsStore(platform: Platform): SettingsStore {
    return SettingsStore()
}