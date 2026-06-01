package com.bghorizon.proxytoolboxgui.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.core.os.LocaleListCompat

@Composable
actual fun LanguageWrapper(languageCode: String?, content: @Composable () -> Unit) {
    // We use a LaunchedEffect to update the app locales when the languageCode changes.
    LaunchedEffect(languageCode) {
        val appLocale: LocaleListCompat = if (languageCode == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }

        if (AppCompatDelegate.getApplicationLocales() != appLocale) {
            // AppCompatDelegate handles setting the locale, persistence (via autoStoreLocales),
            // and system-level syncing on Android 13+.
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    content()
}
