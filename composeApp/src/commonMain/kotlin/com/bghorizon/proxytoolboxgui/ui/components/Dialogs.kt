package com.bghorizon.proxytoolboxgui.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.Res
import proxytoolboxgui.composeapp.generated.resources.dialog_btn_cancel
import proxytoolboxgui.composeapp.generated.resources.dialog_btn_save

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
