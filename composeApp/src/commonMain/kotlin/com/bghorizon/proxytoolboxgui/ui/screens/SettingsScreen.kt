package com.bghorizon.proxytoolboxgui.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.data.ThemeMode
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import com.bghorizon.proxytoolboxgui.viewmodel.UiDialog
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.*

sealed interface SettingsDialog : UiDialog {
    data object Worker : SettingsDialog
    data object DownloadTimeout : SettingsDialog
    data object LatencyRounds : SettingsDialog
    data object RoundTimeout : SettingsDialog
    data object BatchSize : SettingsDialog
    data object Port : SettingsDialog
    data object TestUrl : SettingsDialog
    data object ParallelDownloads : SettingsDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar() {
    TopAppBar(
        title = { Text(stringResource(Res.string.title_settings)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val workers = uiState.workers
    val activeDialog = uiState.activeDialog

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SettingsSection(title = stringResource(Res.string.category_appearance)) {
                SettingsItem(title = stringResource(Res.string.app_theme), customHeight = true) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeMode.entries.forEachIndexed { index, theme ->
                            SegmentedButton(
                                selected = settings.theme == theme,
                                onClick = { viewModel.updateTheme(theme) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size
                                ),
                                label = {
                                    Text(
                                        when (theme) {
                                            ThemeMode.LIGHT -> stringResource(Res.string.app_theme_light)
                                            ThemeMode.DARK -> stringResource(Res.string.app_theme_dark)
                                            ThemeMode.SYSTEM -> stringResource(Res.string.app_theme_system)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
                SettingsSwitchItem(
                    title = stringResource(Res.string.use_monet),
                    checked = settings.dynamicColor,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(dynamicColor = it))
                    },
                    enabled = uiState.isDynamicColorSupported
                )
            }
        }

        item {
            SettingsSection(title = stringResource(Res.string.category_worker)) {
                SettingsItem(
                    title = stringResource(Res.string.current_worker),
                    subtitle = workers.find { it.path == settings.selectedWorker }?.name
                        ?: stringResource(Res.string.no_workers_available),
                    onClick = { viewModel.updateDialog(SettingsDialog.Worker) }
                )
            }
        }

        item {
            SettingsSection(title = stringResource(Res.string.category_download)) {
                SettingsItem(
                    title = stringResource(Res.string.sub_download_timeout),
                    subtitle = stringResource(
                        Res.string.hint_seconds_preview,
                        settings.downloadTimeout
                    ),
                    onClick = { viewModel.updateDialog(SettingsDialog.DownloadTimeout) }
                )
                SettingsItem(
                    title = stringResource(Res.string.parallel_sub_downloads),
                    subtitle = settings.parallelSubscriptionDownloads.toString(),
                    onClick = { viewModel.updateDialog(SettingsDialog.ParallelDownloads) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.perform_deduplication),
                    checked = settings.performDedup,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(performDedup = it))
                    }
                )
            }
        }

        item {
            SettingsSection(title = stringResource(Res.string.category_testing)) {
                SettingsItem(
                    title = stringResource(Res.string.latency_test_rounds),
                    subtitle = stringResource(
                        Res.string.hint_rounds_preview,
                        settings.latencyRounds
                    ),
                    onClick = { viewModel.updateDialog(SettingsDialog.LatencyRounds) }
                )
                SettingsItem(
                    title = stringResource(Res.string.round_timeout),
                    subtitle = stringResource(
                        Res.string.hint_seconds_preview,
                        settings.roundTimeout
                    ),
                    onClick = { viewModel.updateDialog(SettingsDialog.RoundTimeout) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.test_by_batches),
                    checked = settings.testByBatches,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(testByBatches = it))
                    }
                )
                SettingsItem(
                    title = stringResource(Res.string.batch_size),
                    subtitle = settings.batchSize.toString(),
                    enabled = settings.testByBatches,
                    onClick = { viewModel.updateDialog(SettingsDialog.BatchSize) }
                )
                SettingsItem(
                    title = stringResource(Res.string.latency_test_url),
                    subtitle = settings.testUrl,
                    onClick = { viewModel.updateDialog(SettingsDialog.TestUrl) }
                )
            }
        }

        item {
            SettingsSection(title = stringResource(Res.string.category_web_server)) {
                SettingsSwitchItem(
                    title = stringResource(Res.string.web_server_auto_start),
                    checked = settings.autoStartWebServer,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(autoStartWebServer = it))
                    }
                )
                SettingsItem(
                    title = stringResource(Res.string.web_server_port),
                    subtitle = settings.webServerPort.toString(),
                    onClick = { viewModel.updateDialog(SettingsDialog.Port) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.web_server_localhost),
                    checked = settings.webServerLocalhost,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(webServerLocalhost = it))
                    }
                )
            }
        }
    }

    when (activeDialog as? SettingsDialog) {
        SettingsDialog.Worker -> {
            SelectionDialog(
                title = stringResource(Res.string.current_worker),
                items = workers,
                selectedItem = workers.find { it.path == settings.selectedWorker },
                onDismiss = { viewModel.hideDialog() },
                onSelect = { viewModel.selectWorker(it) },
                emptyText = stringResource(Res.string.no_workers_available),
                itemLabel = { it.name },
                itemSecondaryLabel = { it.version }
            )
        }

        SettingsDialog.DownloadTimeout -> {
            NumberInputDialog(
                title = stringResource(Res.string.sub_download_timeout),
                initialValue = settings.downloadTimeout,
                hint = stringResource(Res.string.hint_seconds, settings.downloadTimeout),
                onDismiss = { viewModel.hideDialog() },
                onSave = {
                    viewModel.updateSettings(settings.copy(downloadTimeout = it.coerceAtLeast(1)))
                    true
                }
            )
        }

        SettingsDialog.LatencyRounds -> {
            NumberInputDialog(
                title = stringResource(Res.string.latency_test_rounds),
                initialValue = settings.latencyRounds,
                hint = stringResource(Res.string.hint_rounds),
                onDismiss = { viewModel.hideDialog() },
                onSave = {
                    viewModel.updateSettings(settings.copy(latencyRounds = it.coerceAtLeast(1)))
                    true
                }
            )
        }

        SettingsDialog.RoundTimeout -> {
            NumberInputDialog(
                title = stringResource(Res.string.round_timeout),
                initialValue = settings.roundTimeout,
                hint = stringResource(Res.string.hint_seconds, settings.roundTimeout),
                onDismiss = { viewModel.hideDialog() },
                onSave = {
                    viewModel.updateSettings(settings.copy(roundTimeout = it.coerceAtLeast(1)))
                    true
                }
            )
        }

        SettingsDialog.BatchSize -> {
            NumberInputDialog(
                title = stringResource(Res.string.batch_size),
                initialValue = settings.batchSize,
                hint = stringResource(Res.string.hint_number),
                onDismiss = { viewModel.hideDialog() },
                onSave = {
                    viewModel.updateSettings(settings.copy(batchSize = it.coerceAtLeast(1)))
                    true
                }
            )
        }

        SettingsDialog.Port -> {
            NumberInputDialog(
                title = stringResource(Res.string.web_server_port),
                initialValue = settings.webServerPort,
                hint = stringResource(
                    Res.string.hint_number_specify,
                    stringResource(Res.string.hint_allowed_ports)
                ),
                onDismiss = { viewModel.hideDialog() },
                onSave = { viewModel.savePort(it) },
                isValid = { it in 1024..65535 },
                errorText = stringResource(Res.string.error_invalid_port)
            )
        }

        SettingsDialog.TestUrl -> {
            TextInputDialog(
                title = stringResource(Res.string.latency_test_url),
                initialValue = settings.testUrl,
                onDismiss = { viewModel.hideDialog() },
                onSave = {
                    if (it.isNotBlank()) {
                        viewModel.updateSettings(settings.copy(testUrl = it))
                        true
                    } else false
                }
            )
        }

        SettingsDialog.ParallelDownloads -> {
            NumberInputDialog(
                title = stringResource(Res.string.parallel_sub_downloads),
                initialValue = settings.parallelSubscriptionDownloads,
                hint = stringResource(Res.string.hint_number),
                onDismiss = { viewModel.hideDialog() },
                onSave = {
                    viewModel.updateSettings(
                        settings.copy(
                            parallelSubscriptionDownloads = it.coerceAtLeast(
                                1
                            )
                        )
                    )
                    true
                }
            )
        }

        null -> {}
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    SettingsCategory(title = title)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun SettingsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    customHeight: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        onClick = onClick ?: {},
        modifier = modifier.fillMaxWidth(),
        enabled = enabled && onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (customHeight) Modifier else Modifier.height(64.dp)
                )
                .fillMaxWidth()
                .padding(
                    vertical = if (customHeight) 12.dp else 0.dp,
                    horizontal = 12.dp
                )
                .alpha(if (enabled) 1f else ListItemDefaults.colors().disabledHeadlineColor.alpha),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (trailingContent != null) {
                    trailingContent()
                }
            }
            content?.invoke()
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    SettingsItem(
        title = title,
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}

@Composable
private fun SettingsDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(Res.string.dialog_btn_cancel),
    onConfirm: (() -> Boolean)? = null,
    confirmEnabled: Boolean = true,
    showCancel: Boolean = onConfirm != null,
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
                enabled = confirmEnabled
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
private fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T?,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    emptyText: String? = null,
    itemLabel: @Composable (T) -> String,
    itemSecondaryLabel: (@Composable (T) -> String)? = null
) {
    SettingsDialog(title = title, onDismiss = onDismiss) {
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
private fun NumberInputDialog(
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

    SettingsDialog(
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
private fun TextInputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean
) {
    var text by remember { mutableStateOf(initialValue) }

    SettingsDialog(
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
