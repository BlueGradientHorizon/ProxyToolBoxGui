package com.bghorizon.proxytoolboxgui.ui

import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Negates both the hardcoded horizontal padding `FabMenuPaddingHorizontal` and bottom
 * padding `FabMenuButtonPaddingBottom` in the Material 3 [FloatingActionButtonMenu].
 * Paddings are already applied by [Scaffold].
 * The height will still be 72dp though, but this wouldn't be detectable visually.
 * This is probably the worst decision ever made when they hardcoded paddings without
 * easy way to remove them.
 * Maybe buggy, but works at least :/
 */
fun Modifier.removeFabMenuPaddings(
    horizontalPadding: Dp = 16.dp,
    bottomPadding: Dp = 16.dp,
): Modifier = this.layout { measurable, constraints ->
    val horizontalPx = horizontalPadding.roundToPx()
    val bottomPx = bottomPadding.roundToPx()

    val placeable = measurable.measure(
        constraints.copy(minWidth = 0, minHeight = 0)
    )

    val width = maxOf(0, placeable.width - (horizontalPx * 2))
    val height = maxOf(0, placeable.height - bottomPx)

    layout(width, height) {
        placeable.place(-horizontalPx, 0)
    }
}
