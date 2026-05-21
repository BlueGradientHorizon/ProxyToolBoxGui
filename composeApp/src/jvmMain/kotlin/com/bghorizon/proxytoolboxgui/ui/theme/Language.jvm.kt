package com.bghorizon.proxytoolboxgui.ui.theme

import androidx.compose.runtime.*
import java.util.Locale

@Composable
actual fun LanguageWrapper(languageCode: String?, content: @Composable () -> Unit) {
    val locale = remember(languageCode) {
        if (languageCode == null) Locale.getDefault() else Locale.forLanguageTag(languageCode)
    }
    
    // On Desktop, stringResource() uses the default JVM locale.
    // Changing it globally might affect other things, but it's the standard way.
    SideEffect {
        if (Locale.getDefault() != locale) {
            Locale.setDefault(locale)
        }
    }
    
    // We use a key to force recomposition of the whole tree when locale changes.
    // This ensures all stringResource() calls are re-evaluated.
    key(locale) {
        content()
    }
}
