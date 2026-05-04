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
import proxytoolboxgui.composeapp.generated.resources.cancel
import proxytoolboxgui.composeapp.generated.resources.current_worker
import proxytoolboxgui.composeapp.generated.resources.no_workers_available
import proxytoolboxgui.composeapp.generated.resources.settings
import proxytoolboxgui.composeapp.generated.resources.theme
import proxytoolboxgui.composeapp.generated.resources.theme_dark
import proxytoolboxgui.composeapp.generated.resources.theme_light
import proxytoolboxgui.composeapp.generated.resources.theme_system

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val workers by viewModel.workers.collectAsState()
    val showThemeDialog by viewModel.showThemeDialog.collectAsState()
    val showWorkerDialog by viewModel.showWorkerDialog.collectAsState()

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
                SettingsCategory(title = "Appearance")
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
                SettingsCategory(title = "Worker")
                SettingsClickableItem(
                    title = stringResource(Res.string.current_worker),
                    subtitle = workers.find { it.path == settings.selectedWorker }?.name
                        ?: stringResource(Res.string.no_workers_available),
                    onClick = { viewModel.showWorkerDialog() }
                )
            }

            item {
                SettingsCategory(title = "Download")
                SettingsNumberItem(
                    title = "Subscription download timeout",
                    value = settings.downloadTimeout,
                    hint = "Seconds",
                    onValueChange = {
                        viewModel.updateSettings(settings.copy(downloadTimeout = it.coerceAtLeast(1)))
                    }
                )
                SettingsSwitchItem(
                    title = "Perform deduplication",
                    checked = settings.performDedup,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(performDedup = it))
                    }
                )
            }

            item {
                SettingsCategory(title = "Testing")
                SettingsNumberItem(
                    title = "Latency test rounds",
                    value = settings.latencyRounds,
                    hint = "Count",
                    onValueChange = {
                        viewModel.updateSettings(settings.copy(latencyRounds = it.coerceAtLeast(1)))
                    }
                )
                SettingsNumberItem(
                    title = "Round timeout",
                    value = settings.roundTimeout,
                    hint = "Seconds",
                    onValueChange = {
                        viewModel.updateSettings(settings.copy(roundTimeout = it.coerceAtLeast(1)))
                    }
                )
                SettingsSwitchItem(
                    title = "Test by batches",
                    checked = settings.testByBatches,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(testByBatches = it))
                    }
                )
                SettingsNumberItem(
                    title = "Batch size",
                    value = settings.batchSize,
                    hint = "Count",
                    enabled = settings.testByBatches,
                    onValueChange = {
                        viewModel.updateSettings(settings.copy(batchSize = it.coerceAtLeast(1)))
                    }
                )
            }

            item {
                SettingsCategory(title = "Web Server")
                SettingsSwitchItem(
                    title = "Auto-start web-server after test",
                    checked = settings.autoStartWebServer,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(autoStartWebServer = it))
                    }
                )
                SettingsNumberItem(
                    title = "Web-server port",
                    value = settings.webServerPort,
                    hint = "1024-65535",
                    onValueChange = {
                        val port = it.coerceIn(1024, 65535)
                        viewModel.updateSettings(settings.copy(webServerPort = port))
                    }
                )
                SettingsSwitchItem(
                    title = "Web-server listens only localhost",
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
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
private fun SettingsNumberItem(
    title: String,
    value: Int,
    hint: String,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText.filter { it.isDigit() }
                    text.toIntOrNull()?.let { onValueChange(it) }
                },
                label = { Text(hint) },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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