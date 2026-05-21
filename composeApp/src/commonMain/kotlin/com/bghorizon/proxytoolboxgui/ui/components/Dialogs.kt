package com.bghorizon.proxytoolboxgui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Photo_library
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrCodeMatrix
import io.github.alexzhirkevich.qrose.options.QrCodeShape
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.*

@Composable
fun SimpleAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(Res.string.dialog_btn_cancel),
    onConfirm: (() -> Boolean)? = null,
    confirmEnabled: Boolean = true,
    showCancel: Boolean = onConfirm != null,
    confirmButtonColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (onConfirm == null || onConfirm()) {
                        onDismiss()
                    }
                },
                enabled = confirmEnabled,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = confirmButtonColor
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = if (showCancel) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.dialog_btn_cancel))
                }
            }
        } else null
    )
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String,
    isDestructive: Boolean = false
) {
    SimpleAlertDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = confirmText,
        onConfirm = {
            onConfirm()
            true
        },
        confirmButtonColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    ) {
        Text(message)
    }
}

@Composable
fun NumberInputDialog(
    title: String,
    initialValue: Int,
    hint: String,
    onDismiss: () -> Unit,
    onSave: (Int) -> Boolean,
    isValid: (Int) -> Boolean = { true },
    errorText: String? = null
) {
    var text by remember { mutableStateOf(initialValue.toString()) }
    val number = text.toIntOrNull() ?: 0
    val isNumberValid = text.toIntOrNull() != null && isValid(number)

    SimpleAlertDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.dialog_btn_save),
        onConfirm = { onSave(number) },
        confirmEnabled = isNumberValid
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() } },
            label = { Text(hint) },
            isError = !isNumberValid && text.isNotEmpty(),
            supportingText = if (!isNumberValid && text.isNotEmpty() && errorText != null) {
                { Text(errorText) }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean
) {
    var text by remember { mutableStateOf(initialValue) }

    SimpleAlertDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.dialog_btn_save),
        onConfirm = { onSave(text) },
        confirmEnabled = text.isNotBlank()
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(title) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Close,
                                contentDescription = stringResource(Res.string.dialog_btn_close)
                            )
                        }
                    }
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T?,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    emptyText: String? = null,
    itemLabel: @Composable (T) -> String,
    itemSecondaryLabel: (@Composable (T) -> String)? = null
) {
    SimpleAlertDialog(title = title, onDismiss = onDismiss) {
        if (items.isEmpty() && emptyText != null) {
            Text(emptyText)
        } else {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(item)
                            onDismiss()
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = item == selectedItem,
                        onClick = {
                            onSelect(item)
                            onDismiss()
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(itemLabel(item))
                        itemSecondaryLabel?.let {
                            Text(
                                text = it(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanQrCodeDialog(
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit,
    isScannerSupported: Boolean,
    onPickImage: suspend () -> String?,
    title: String = stringResource(Res.string.sub_add_qr),
    scannerSupportedText: String = stringResource(Res.string.sub_qr_not_supported),
    pickImageText: String = stringResource(Res.string.sub_add_qr_file)
) {
    val scope = rememberCoroutineScope()

    SimpleAlertDialog(
        onDismiss = onDismiss,
        title = title,
    ) {
        if (isScannerSupported) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                QrScannerView(
                    modifier = Modifier.fillMaxSize(),
                    onCodeScanned = onCodeScanned
                )
            }
        } else {
            Text(scannerSupportedText, textAlign = TextAlign.Center)
        }

        Button(
            onClick = {
                scope.launch {
                    onPickImage()?.let { result ->
                        onCodeScanned(result)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(MaterialSymbols.Rounded.Photo_library, null)
            Spacer(Modifier.width(8.dp))
            Text(pickImageText)
        }
    }
}

private class QuietZoneShape(private val modules: Int = 4) : QrCodeShape {
    override val shapeSizeIncrease: Float = 1f

    override fun QrCodeMatrix.transform(): QrCodeMatrix {
        val newSize = size + 2 * modules
        val newMatrix = QrCodeMatrix(newSize, QrCodeMatrix.PixelType.LightPixel)
        for (i in 0 until size) {
            for (j in 0 until size) {
                newMatrix[modules + i, modules + j] = this[i, j]
            }
        }
        return newMatrix
    }
}

@Composable
fun ShowQrCodeDialog(
    content: String,
    onDismiss: () -> Unit
) {
    FullScreenDialog(
        title = stringResource(Res.string.title_qr_code),
        onDismiss = onDismiss
    ) {
        if (content.isNotBlank()) {
            val painter = rememberQrCodePainter(content) {
                shapes {
                    pattern = QuietZoneShape(4)
                }
                background {
                    fill = SolidColor(Color.White)
                }
                colors {
                    light = QrBrush.solid(Color.White)
                    dark = QrBrush.solid(Color.Black)
                    frame = QrBrush.solid(Color.Black)
                    ball = QrBrush.solid(Color.Black)
                }
            }

            Image(
                painter = painter,
                contentDescription = stringResource(Res.string.title_qr_code),
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(stringResource(Res.string.error_no_content))
        }
    }
}
