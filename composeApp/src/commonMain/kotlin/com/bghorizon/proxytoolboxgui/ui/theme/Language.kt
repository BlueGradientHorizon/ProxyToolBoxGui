package com.bghorizon.proxytoolboxgui.ui.theme

import androidx.compose.runtime.Composable

@Composable
expect fun LanguageWrapper(languageCode: String?, content: @Composable () -> Unit)
