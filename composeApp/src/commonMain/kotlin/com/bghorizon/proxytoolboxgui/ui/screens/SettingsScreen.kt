package com.bghorizon.proxytoolboxgui.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.data.ThemeMode
import com.bghorizon.proxytoolboxgui.viewmodel.ActiveDialog
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val workers = uiState.workers
    val activeDialog = uiState.activeDialog

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                SettingsCategory(title = stringResource(Res.string.category_appearance))
                SettingsClickableItem(
                    title = stringResource(Res.string.app_theme),
                    subtitle = when (settings.theme) {
                        ThemeMode.LIGHT -> stringResource(Res.string.app_theme_light)
                        ThemeMode.DARK -> stringResource(Res.string.app_theme_dark)
                        ThemeMode.SYSTEM -> stringResource(Res.string.app_theme_system)
                    },
                    onClick = { viewModel.updateDialog(ActiveDialog.Theme) }
                )
            }

            item {
                SettingsCategory(title = stringResource(Res.string.category_worker))
                SettingsClickableItem(
                    title = stringResource(Res.string.current_worker),
                    subtitle = workers.find { it.path == settings.selectedWorker }?.name
                        ?: stringResource(Res.string.no_workers_available),
                    onClick = { viewModel.updateDialog(ActiveDialog.Worker) }
                )
            }

            item {
                SettingsCategory(title = stringResource(Res.string.category_download))
                SettingsClickableItem(
                    title = stringResource(Res.string.sub_download_timeout),
                    subtitle = stringResource(Res.string.hint_seconds_preview, settings.downloadTimeout),
                    onClick = { viewModel.updateDialog(ActiveDialog.DownloadTimeout) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.perform_deduplication),
                    checked = settings.performDedup,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(performDedup = it))
                    }
                )
            }

            item {
                SettingsCategory(title = stringResource(Res.string.category_testing))
                SettingsClickableItem(
                    title = stringResource(Res.string.latency_test_rounds),
                    subtitle = stringResource(Res.string.hint_rounds_preview, settings.latencyRounds),
                    onClick = { viewModel.updateDialog(ActiveDialog.LatencyRounds) }
                )
                SettingsClickableItem(
                    title = stringResource(Res.string.round_timeout),
                    subtitle = stringResource(Res.string.hint_seconds_preview, settings.roundTimeout),
                    onClick = { viewModel.updateDialog(ActiveDialog.RoundTimeout) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.test_by_batches),
                    checked = settings.testByBatches,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(testByBatches = it))
                    }
                )
                SettingsClickableItem(
                    title = stringResource(Res.string.batch_size),
                    //subtitle = stringResource(Res.string.hint_rounds_preview, settings.batchSize),
                    subtitle = settings.batchSize.toString(),
                    enabled = settings.testByBatches,
                    onClick = { viewModel.updateDialog(ActiveDialog.BatchSize) }
                )
            }

            item {
                SettingsCategory(title = stringResource(Res.string.category_web_server))
                SettingsSwitchItem(
                    title = stringResource(Res.string.web_server_auto_start),
                    checked = settings.autoStartWebServer,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(autoStartWebServer = it))
                    }
                )
                SettingsClickableItem(
                    title = stringResource(Res.string.web_server_port),
                    subtitle = settings.webServerPort.toString(),
                    onClick = { viewModel.updateDialog(ActiveDialog.Port) }
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

    when (activeDialog) {
        ActiveDialog.Theme -> {
            SelectionDialog(
                title = stringResource(Res.string.app_theme),
                items = ThemeMode.entries,
                selectedItem = settings.theme,
                onDismiss = { viewModel.hideDialog() },
                onSelect = { viewModel.updateTheme(it) },
                itemLabel = { theme ->
                    when (theme) {
                        ThemeMode.LIGHT -> stringResource(Res.string.app_theme_light)
                        ThemeMode.DARK -> stringResource(Res.string.app_theme_dark)
                        ThemeMode.SYSTEM -> stringResource(Res.string.app_theme_system)
                    }
                }
            )
        }
        ActiveDialog.Worker -> {
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
        ActiveDialog.DownloadTimeout -> {
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
        ActiveDialog.LatencyRounds -> {
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
        ActiveDialog.RoundTimeout -> {
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
        ActiveDialog.BatchSize -> {
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
        ActiveDialog.Port -> {
            NumberInputDialog(
                title = stringResource(Res.string.web_server_port),
                initialValue = settings.webServerPort,
                hint = stringResource(Res.string.hint_number_specify, stringResource(Res.string.hint_allowed_ports)),
                onDismiss = { viewModel.hideDialog() },
                onSave = { viewModel.savePort(it) },
                isValid = { it in 1024..65535 },
                errorText = stringResource(Res.string.error_invalid_port)
            )
        }
        else -> {}
    }
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
private fun SettingsClickableItem(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
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
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    RadioButton(
                        selected = item == selectedItem,
                        onClick = {
                            onSelect(item)
                            onDismiss()
                        }
                    )
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
