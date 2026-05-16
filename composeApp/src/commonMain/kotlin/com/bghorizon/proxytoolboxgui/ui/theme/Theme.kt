package com.bghorizon.proxytoolboxgui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bghorizon.proxytoolboxgui.data.ThemeMode

@Composable
expect fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean
): ColorScheme?

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = remember(themeMode, isSystemDark) {
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemDark
        }
    }

    val platformScheme = platformColorScheme(darkTheme, dynamicColor)
    val colorScheme = remember(platformScheme, darkTheme) {
        platformScheme ?: if (darkTheme) DarkColors else LightColors
    }

    Surface(
        color = colorScheme.background
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
