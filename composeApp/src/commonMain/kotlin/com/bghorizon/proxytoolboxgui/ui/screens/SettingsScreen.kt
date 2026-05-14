package com.bghorizon.proxytoolboxgui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.LocalScaffoldPadding
import com.bghorizon.proxytoolboxgui.ScreenPadding
import com.bghorizon.proxytoolboxgui.ui.components.*
import com.bghorizon.proxytoolboxgui.data.ThemeMode
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import com.bghorizon.proxytoolboxgui.viewmodel.SettingsMode
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

@Composable
fun SettingsTopBar(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState.settingsMode) {
        is SettingsMode.Normal -> {
            NormalSettingsTopBar()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalSettingsTopBar() {
    TopAppBar(
        title = { Text(stringResource(Res.string.title_settings)) }
    )
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val workers = uiState.workers
    val activeDialog = uiState.activeDialog

    val scaffoldPadding = LocalScaffoldPadding.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding),
        contentPadding = PaddingValues(
            top = scaffoldPadding.calculateTopPadding() + ScreenPadding,
            bottom = scaffoldPadding.calculateBottomPadding() + ScreenPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
