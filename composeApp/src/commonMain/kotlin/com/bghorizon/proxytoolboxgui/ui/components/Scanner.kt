package com.bghorizon.proxytoolboxgui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun SubscriptionScannerView(
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit,
)
