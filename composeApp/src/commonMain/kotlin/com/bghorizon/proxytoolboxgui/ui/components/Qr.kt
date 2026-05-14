package com.bghorizon.proxytoolboxgui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.Res
import proxytoolboxgui.composeapp.generated.resources.dialog_btn_close
import proxytoolboxgui.composeapp.generated.resources.title_qr_code
import kotlin.math.max
import kotlin.math.sqrt

@Composable
fun QrCodeDisplay(
    content: String,
    modifier: Modifier = Modifier,
    maxSizeDp: Int = calculateQrSizeDp(content.length)
) {
    if (content.isNotBlank()) {
        val qrColor = MaterialTheme.colorScheme.onSurface
        Image(
            painter = rememberQrCodePainter(content) {
                colors {
                    dark = QrBrush.solid(qrColor)
                }
            },
            contentDescription = stringResource(Res.string.title_qr_code),
            modifier = modifier
                .widthIn(max = maxSizeDp.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
        )
    } else {
        Text("No content")
    }
}

@Composable
fun QrCodeDialog(
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.title_qr_code)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QrCodeDisplay(content = content)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_btn_close))
            }
        }
    )
}

fun calculateQrSizeDp(textLength: Int): Int {
    val minSizeDp = 100
    val paddingDp = 24
    val density = 1.5
    val scale = 10.0

    val calculated = (sqrt(textLength.toDouble() / density) * scale).toInt() + paddingDp
    return max(minSizeDp, calculated)
}
