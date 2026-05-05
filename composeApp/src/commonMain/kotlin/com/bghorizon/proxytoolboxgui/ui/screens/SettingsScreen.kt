package com.bghorizon.proxytoolboxgui.ui.screens

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
import com.bghorizon.proxytoolboxgui.data.AppSettings
import com.bghorizon.proxytoolboxgui.data.ThemeMode
import com.bghorizon.proxytoolboxgui.data.WorkerInfo
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.Res
import proxytoolboxgui.composeapp.generated.resources.back
import proxytoolboxgui.composeapp.generated.resources.batch_size
import proxytoolboxgui.composeapp.generated.resources.cancel
import proxytoolboxgui.composeapp.generated.resources.category_appearance
import proxytoolboxgui.composeapp.generated.resources.category_download
import proxytoolboxgui.composeapp.generated.resources.category_testing
import proxytoolboxgui.composeapp.generated.resources.category_web_server
import proxytoolboxgui.composeapp.generated.resources.category_worker
import proxytoolboxgui.composeapp.generated.resources.count_hint
import proxytoolboxgui.composeapp.generated.resources.current_worker
import proxytoolboxgui.composeapp.generated.resources.error_invalid_port
import proxytoolboxgui.composeapp.generated.resources.latency_test_rounds
import proxytoolboxgui.composeapp.generated.resources.no_workers_available
import proxytoolboxgui.composeapp.generated.resources.perform_deduplication
import proxytoolboxgui.composeapp.generated.resources.port_hint
import proxytoolboxgui.composeapp.generated.resources.round_timeout
import proxytoolboxgui.composeapp.generated.resources.save
import proxytoolboxgui.composeapp.generated.resources.seconds_hint
import proxytoolboxgui.composeapp.generated.resources.settings
import proxytoolboxgui.composeapp.generated.resources.subscription_download_timeout
import proxytoolboxgui.composeapp.generated.resources.test_by_batches
import proxytoolboxgui.composeapp.generated.resources.theme
import proxytoolboxgui.composeapp.generated.resources.theme_dark
import proxytoolboxgui.composeapp.generated.resources.theme_light
import proxytoolboxgui.composeapp.generated.resources.theme_system
import proxytoolboxgui.composeapp.generated.resources.web_server_localhost
import proxytoolboxgui.composeapp.generated.resources.web_server_port

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val workers by viewModel.workers.collectAsState()
    val showThemeDialog by viewModel.showThemeDialog.collectAsState()
    val showWorkerDialog by viewModel.showWorkerDialog.collectAsState()
    val showDownloadTimeoutDialog by viewModel.showDownloadTimeoutDialog.collectAsState()
    val showLatencyRoundsDialog by viewModel.showLatencyRoundsDialog.collectAsState()
    val showRoundTimeoutDialog by viewModel.showRoundTimeoutDialog.collectAsState()
    val showBatchSizeDialog by viewModel.showBatchSizeDialog.collectAsState()
    val showPortDialog by viewModel.showPortDialog.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
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
                    title = stringResource(Res.string.theme),
                    subtitle = when (settings.theme) {
                        ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
                        ThemeMode.DARK -> stringResource(Res.string.theme_dark)
                        ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
                    },
                    onClick = { viewModel.showThemeDialog() }
                )
            }

            item {
                SettingsCategory(title = stringResource(Res.string.category_worker))
                SettingsClickableItem(
                    title = stringResource(Res.string.current_worker),
                    subtitle = workers.find { it.path == settings.selectedWorker }?.name
                        ?: stringResource(Res.string.no_workers_available),
                    onClick = { viewModel.showWorkerDialog() }
                )
            }

            item {
                SettingsCategory(title = stringResource(Res.string.category_download))
                SettingsClickableItem(
                    title = stringResource(Res.string.subscription_download_timeout),
                    subtitle = stringResource(Res.string.seconds_hint, settings.downloadTimeout),
                    onClick = { viewModel.showDownloadTimeoutDialog() }
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
                    subtitle = stringResource(Res.string.count_hint, settings.latencyRounds),
                    onClick = { viewModel.showLatencyRoundsDialog() }
                )
                SettingsClickableItem(
                    title = stringResource(Res.string.round_timeout),
                    subtitle = stringResource(Res.string.seconds_hint, settings.roundTimeout),
                    onClick = { viewModel.showRoundTimeoutDialog() }
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
                    subtitle = stringResource(Res.string.count_hint, settings.batchSize),
                    enabled = settings.testByBatches,
                    onClick = { viewModel.showBatchSizeDialog() }
                )
            }

            item {
                SettingsCategory(title = stringResource(Res.string.category_web_server))
                SettingsSwitchItem(
                    title = stringResource(Res.string.auto_start_web_server),
                    checked = settings.autoStartWebServer,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(autoStartWebServer = it))
                    }
                )
                SettingsClickableItem(
                    title = stringResource(Res.string.web_server_port),
                    subtitle = stringResource(Res.string.port_hint),
                    onClick = { viewModel.showPortDialog() }
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

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = settings.theme,
            onDismiss = { viewModel.hideThemeDialog() },
            onSelect = { viewModel.updateTheme(it) }
        )
    }

    if (showWorkerDialog) {
        WorkerSelectionDialog(
            workers = workers,
            currentWorker = settings.selectedWorker,
            onDismiss = { viewModel.hideWorkerDialog() },
            onSelect = { viewModel.selectWorker(it) }
        )
    }

    if (showDownloadTimeoutDialog) {
        NumberInputDialog(
            title = stringResource(Res.string.subscription_download_timeout),
            currentValue = settings.downloadTimeout,
            hint = stringResource(Res.string.seconds_hint, settings.downloadTimeout),
            onDismiss = { viewModel.hideDownloadTimeoutDialog() },
            onSave = { viewModel.updateSettings(settings.copy(downloadTimeout = it.coerceAtLeast(1))) }
        )
    }

    if (showLatencyRoundsDialog) {
        NumberInputDialog(
            title = stringResource(Res.string.latency_test_rounds),
            currentValue = settings.latencyRounds,
            hint = stringResource(Res.string.count_hint, settings.latencyRounds),
            onDismiss = { viewModel.hideLatencyRoundsDialog() },
            onSave = { viewModel.updateSettings(settings.copy(latencyRounds = it.coerceAtLeast(1))) }
        )
    }

    if (showRoundTimeoutDialog) {
        NumberInputDialog(
            title = stringResource(Res.string.round_timeout),
            currentValue = settings.roundTimeout,
            hint = stringResource(Res.string.seconds_hint, settings.roundTimeout),
            onDismiss = { viewModel.hideRoundTimeoutDialog() },
            onSave = { viewModel.updateSettings(settings.copy(roundTimeout = it.coerceAtLeast(1))) }
        )
    }

    if (showBatchSizeDialog) {
        NumberInputDialog(
            title = stringResource(Res.string.batch_size),
            currentValue = settings.batchSize,
            hint = stringResource(Res.string.count_hint, settings.batchSize),
            onDismiss = { viewModel.hideBatchSizeDialog() },
            onSave = { viewModel.updateSettings(settings.copy(batchSize = it.coerceAtLeast(1))) }
        )
    }

    if (showPortDialog) {
        PortInputDialog(
            currentValue = settings.webServerPort,
            onDismiss = { viewModel.hidePortDialog() },
            onSave = { viewModel.savePort(it) }
        )
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
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { theme ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = when (theme) {
                                ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
                                ThemeMode.DARK -> stringResource(Res.string.theme_dark)
                                ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
                            }
                        )
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = {
                                onSelect(theme)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun WorkerSelectionDialog(
    workers: List<WorkerInfo>,
    currentWorker: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.current_worker)) },
        text = {
            if (workers.isEmpty()) {
                Text(stringResource(Res.string.no_workers_available))
            } else {
                Column {
                    workers.forEach { worker ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(worker.name)
                                Text(
                                    worker.version,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = currentWorker == worker.path,
                                onClick = {
                                    onSelect(worker.path)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun NumberInputDialog(
    title: String,
    currentValue: Int,
    hint: String,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text(hint) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    text.toIntOrNull()?.let { onSave(it) }
                    onDismiss()
                },
                enabled = text.toIntOrNull() != null
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun PortInputDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Boolean
) {
    var text by remember { mutableStateOf(currentValue.toString()) }
    val isValid = text.toIntOrNull()?.let { it in 1024..65535 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.web_server_port)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(Res.string.port_hint)) },
                isError = !isValid && text.isNotEmpty(),
                supportingText = {
                    if (!isValid && text.isNotEmpty()) {
                        Text(stringResource(Res.string.error_invalid_port))
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val port = text.toIntOrNull() ?: 0
                    if (onSave(port)) {
                        onDismiss()
                    }
                },
                enabled = isValid
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
