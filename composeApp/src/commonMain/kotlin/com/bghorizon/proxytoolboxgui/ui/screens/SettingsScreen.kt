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
import com.bghorizon.proxytoolboxgui.data.AppLanguage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bghorizon.proxytoolboxgui.di.LocalAppModule
import com.bghorizon.proxytoolboxgui.viewmodel.*
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
    data object SpeedTestRounds : SettingsDialog
    data object SpeedTestProvider : SettingsDialog
    data object SpeedTestTargetBytes : SettingsDialog
    data object Language : SettingsDialog
}

sealed interface SettingsScreenUiMode : ScreenUiMode {
    data object Normal : SettingsScreenUiMode, ScreenUiMode.Normal
}

data class SettingsScreenState(
    override val mode: SettingsScreenUiMode = SettingsScreenUiMode.Normal,
) : AppScreen {
    @Composable
    override fun TopBar(mainVm: MainViewModel) {
        val module = LocalAppModule.current
        val settingsVm = viewModel { SettingsScreenViewModel(module) }
        SettingsScreenTopBar(settingsVm)
    }

    @Composable
    override fun Content(mainVm: MainViewModel) {
        val module = LocalAppModule.current
        val settingsVm: SettingsScreenViewModel = viewModel { SettingsScreenViewModel(module) }
        SettingsScreen(mainVm, settingsVm)
    }

    @Composable
    override fun FAB(mainVm: MainViewModel) {
    }
}

@Composable
fun SettingsScreenTopBar(settingsVm: SettingsScreenViewModel) {
    val settingsUiState by settingsVm.uiState.collectAsState()
    when (settingsUiState.mode) {
        is SettingsScreenUiMode.Normal -> {
            NormalSettingsScreenTopBar()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalSettingsScreenTopBar() {
    TopAppBar(
        title = { Text(stringResource(Res.string.title_settings)) },
    )
}

@Composable
fun SettingsScreen(mainVm: MainViewModel, settingsVm: SettingsScreenViewModel) {
    val mainUiState by mainVm.uiState.collectAsState()
    val settings by settingsVm.settings.collectAsState()
    val activeDialog = mainUiState.activeDialog

    val scaffoldPadding = LocalScaffoldPadding.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding),
        contentPadding = PaddingValues(
            top = scaffoldPadding.calculateTopPadding() + ScreenPadding,
            bottom = scaffoldPadding.calculateBottomPadding() + ScreenPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SettingsSection(title = stringResource(Res.string.category_appearance)) {
                SettingsItem(title = stringResource(Res.string.app_theme)) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeMode.entries.forEachIndexed { index, theme ->
                            SegmentedButton(
                                selected = settings.theme == theme,
                                onClick = { settingsVm.updateTheme(theme) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size
                                ),
                                label = {
                                    Text(stringResource(settingsVm.getThemeLabel(theme)))
                                }
                            )
                        }
                    }
                }
                SettingsSwitchItem(
                    title = stringResource(Res.string.use_monet),
                    checked = settings.dynamicColor,
                    onCheckedChange = {
                        settingsVm.updateSettings(settings.copy(dynamicColor = it))
                    },
                    enabled = mainUiState.isDynamicColorSupported,
                    subtitle = if (!mainUiState.isDynamicColorSupported) stringResource(Res.string.monet_not_supported) else null
                )
                val nativeName = settingsVm.getLanguageNativeName(settings.language)
                val title = if (settings.language != AppLanguage.ENGLISH) {
                    "${stringResource(Res.string.app_language)} (Language)"
                } else {
                    stringResource(Res.string.app_language)
                }

                SettingsItem(
                    title = title,
                    subtitle = if (nativeName == null) {
                        stringResource(Res.string.app_language_system)
                    } else {
                        stringResource(nativeName)
                    },
                    onClick = { mainVm.updateDialog(SettingsDialog.Language) }
                )
            }
        }

        item {
            SettingsSection(title = stringResource(Res.string.category_worker)) {
                SettingsItem(
                    title = stringResource(Res.string.current_worker),
                    subtitle = mainUiState.workers.find { it.path == settings.selectedWorker }?.name
                        ?: stringResource(Res.string.no_workers_available),
                    onClick = { mainVm.updateDialog(SettingsDialog.Worker) }
                )
            }
        }

        item {
            SettingsSection(title = stringResource(Res.string.category_subscriptions)) {
                SettingsItem(
                    title = stringResource(Res.string.sub_download_timeout),
                    subtitle = stringResource(
                        Res.string.hint_seconds_preview,
                        settings.downloadTimeout
                    ),
                    onClick = { mainVm.updateDialog(SettingsDialog.DownloadTimeout) }
                )
                SettingsItem(
                    title = stringResource(Res.string.parallel_sub_downloads),
                    subtitle = settings.parallelSubscriptionDownloads.toString(),
                    onClick = { mainVm.updateDialog(SettingsDialog.ParallelDownloads) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.perform_deduplication),
                    checked = settings.performDedup,
                    onCheckedChange = {
                        settingsVm.updateSettings(settings.copy(performDedup = it))
                    }
                )
            }
        }

        item {
            SettingsSection(title = stringResource(Res.string.category_testing)) {
                SettingsSwitchItem(
                    title = stringResource(Res.string.low_mem_mode),
                    subtitle = stringResource(Res.string.low_mem_mode_hint),
                    checked = settings.lowMemMode,
                    onCheckedChange = {
                        settingsVm.updateSettings(settings.copy(lowMemMode = it))
                    }
                )
                SettingsItem(
                    title = stringResource(Res.string.latency_test_rounds),
                    subtitle = stringResource(
                        Res.string.hint_rounds_preview,
                        settings.latencyRounds
                    ),
                    onClick = { mainVm.updateDialog(SettingsDialog.LatencyRounds) }
                )
                SettingsItem(
                    title = stringResource(Res.string.round_timeout),
                    subtitle = stringResource(
                        Res.string.hint_seconds_preview,
                        settings.roundTimeout
                    ),
                    onClick = { mainVm.updateDialog(SettingsDialog.RoundTimeout) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.test_by_batches),
                    checked = settings.testByBatches,
                    onCheckedChange = {
                        settingsVm.updateSettings(settings.copy(testByBatches = it))
                    }
                )
                SettingsItem(
                    title = stringResource(Res.string.batch_size),
                    subtitle = settings.batchSize.toString(),
                    enabled = settings.testByBatches,
                    onClick = { mainVm.updateDialog(SettingsDialog.BatchSize) }
                )
                SettingsItem(
                    title = stringResource(Res.string.latency_test_url),
                    subtitle = settings.testUrl,
                    onClick = { mainVm.updateDialog(SettingsDialog.TestUrl) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.latency_test_sort_by_delay),
                    checked = settings.sortProfilesByDelay,
                    onCheckedChange = {
                        settingsVm.updateSettings(settings.copy(sortProfilesByDelay = it))
                    }
                )

                SettingsSwitchItem(
                    title = stringResource(Res.string.perform_speed_test),
                    checked = settings.performSpeedTests,
                    onCheckedChange = {
                        settingsVm.updateSettings(settings.copy(performSpeedTests = it))
                    }
                )
                SettingsItem(
                    title = stringResource(Res.string.speed_test_rounds),
                    subtitle = stringResource(
                        Res.string.hint_rounds_preview,
                        settings.speedTestRounds
                    ),
                    enabled = settings.performSpeedTests,
                    onClick = { mainVm.updateDialog(SettingsDialog.SpeedTestRounds) }
                )
                SettingsItem(
                    title = stringResource(Res.string.speed_test_provider),
                    subtitle = mainUiState.speedTestPresets[settings.speedTestProviderId]
                        ?: settings.speedTestProviderId,
                    enabled = settings.performSpeedTests,
                    onClick = { mainVm.updateDialog(SettingsDialog.SpeedTestProvider) }
                )
                SettingsItem(
                    title = stringResource(Res.string.speed_test_mode),
                    enabled = settings.performSpeedTests
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("download", "upload").forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.speedTestMode == mode,
                                onClick = {
                                    settingsVm.updateSettings(settings.copy(speedTestMode = mode))
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = 2
                                ),
                                label = {
                                    Text(stringResource(settingsVm.getSpeedTestModeLabel(mode)))
                                },
                                enabled = settings.performSpeedTests
                            )
                        }
                    }
                }
                SettingsItem(
                    title = stringResource(Res.string.speed_test_target_bytes),
                    subtitle = settings.speedTestTargetBytes.toString(),
                    enabled = settings.performSpeedTests,
                    onClick = { mainVm.updateDialog(SettingsDialog.SpeedTestTargetBytes) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.speed_test_sort_by_latency_delay),
                    subtitle = if (!settings.sortProfilesByDelay) stringResource(
                        Res.string.speed_test_sort_by_latency_delay_hint,
                        stringResource(Res.string.latency_test_sort_by_delay)
                    ) else null,
                    checked = settings.sortByLatencyDelay,
                    enabled = settings.performSpeedTests && settings.sortProfilesByDelay,
                    onCheckedChange = {
                        settingsVm.updateSettings(settings.copy(sortByLatencyDelay = it))
                    }
                )
            }
        }

        item {
            SettingsSection(title = stringResource(Res.string.category_web_server)) {
                SettingsSwitchItem(
                    title = stringResource(Res.string.web_server_auto_start),
                    checked = settings.autoStartWebServer,
                    onCheckedChange = {
                        settingsVm.updateSettings(settings.copy(autoStartWebServer = it))
                    }
                )
                SettingsItem(
                    title = stringResource(Res.string.web_server_port),
                    subtitle = settings.webServerPort.toString(),
                    onClick = { mainVm.updateDialog(SettingsDialog.Port) }
                )
                SettingsSwitchItem(
                    title = stringResource(Res.string.web_server_localhost),
                    checked = settings.webServerLocalhost,
                    onCheckedChange = {
                        settingsVm.updateSettings(settings.copy(webServerLocalhost = it))
                    }
                )
            }
        }
    }

    when (activeDialog as? SettingsDialog) {
        SettingsDialog.Worker -> {
            val workers = mainUiState.workers
            SelectionDialog(
                title = stringResource(Res.string.current_worker),
                items = workers,
                selectedItem = workers.find { it.path == settings.selectedWorker },
                onDismiss = { mainVm.hideDialog() },
                onSelect = { settingsVm.selectWorker(it.name, it.path) },
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
                onDismiss = { mainVm.hideDialog() },
                onSave = { value ->
                    settingsVm.updateSettings(settings.copy(downloadTimeout = value.coerceAtLeast(1)))
                    true
                }
            )
        }

        SettingsDialog.LatencyRounds -> {
            NumberInputDialog(
                title = stringResource(Res.string.latency_test_rounds),
                initialValue = settings.latencyRounds,
                hint = stringResource(Res.string.hint_rounds),
                onDismiss = { mainVm.hideDialog() },
                onSave = { value ->
                    settingsVm.updateSettings(settings.copy(latencyRounds = value.coerceAtLeast(1)))
                    true
                }
            )
        }

        SettingsDialog.RoundTimeout -> {
            NumberInputDialog(
                title = stringResource(Res.string.round_timeout),
                initialValue = settings.roundTimeout,
                hint = stringResource(Res.string.hint_seconds, settings.roundTimeout),
                onDismiss = { mainVm.hideDialog() },
                onSave = { value ->
                    settingsVm.updateSettings(settings.copy(roundTimeout = value.coerceAtLeast(1)))
                    true
                }
            )
        }

        SettingsDialog.BatchSize -> {
            NumberInputDialog(
                title = stringResource(Res.string.batch_size),
                initialValue = settings.batchSize,
                hint = stringResource(Res.string.hint_number),
                onDismiss = { mainVm.hideDialog() },
                onSave = { value ->
                    settingsVm.updateSettings(settings.copy(batchSize = value.coerceAtLeast(1)))
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
                onDismiss = { mainVm.hideDialog() },
                onSave = { value -> settingsVm.savePort(value) },
                isValid = { it in 1024..65535 },
                errorText = stringResource(Res.string.error_invalid_port)
            )
        }

        SettingsDialog.TestUrl -> {
            TextInputDialog(
                title = stringResource(Res.string.latency_test_url),
                initialValue = settings.testUrl,
                onDismiss = { mainVm.hideDialog() },
                onSave = {
                    if (it.isNotBlank()) {
                        settingsVm.updateSettings(settings.copy(testUrl = it))
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
                onDismiss = { mainVm.hideDialog() },
                onSave = {
                    settingsVm.updateSettings(
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

        SettingsDialog.SpeedTestRounds -> {
            NumberInputDialog(
                title = stringResource(Res.string.speed_test_rounds),
                initialValue = settings.speedTestRounds,
                hint = stringResource(Res.string.hint_rounds),
                onDismiss = { mainVm.hideDialog() },
                onSave = {
                    settingsVm.updateSettings(settings.copy(speedTestRounds = it.coerceAtLeast(1)))
                    true
                }
            )
        }

        SettingsDialog.SpeedTestProvider -> {
            val presets = mainUiState.speedTestPresets.map { (id, name) -> id to name }
            SelectionDialog(
                title = stringResource(Res.string.speed_test_provider),
                items = presets,
                selectedItem = presets.find { it.first == settings.speedTestProviderId },
                onDismiss = { mainVm.hideDialog() },
                onSelect = { settingsVm.updateSettings(settings.copy(speedTestProviderId = it.first)) },
                emptyText = stringResource(Res.string.no_speed_test_providers_available),
                itemLabel = { it.second }
            )
        }

        SettingsDialog.SpeedTestTargetBytes -> {
            NumberInputDialog(
                title = stringResource(Res.string.speed_test_target_bytes),
                initialValue = settings.speedTestTargetBytes.toInt(),
                hint = stringResource(Res.string.hint_number),
                onDismiss = { mainVm.hideDialog() },
                onSave = {
                    settingsVm.updateSettings(
                        settings.copy(
                            speedTestTargetBytes = it.toLong().coerceAtLeast(1)
                        )
                    )
                    true
                }
            )
        }

        SettingsDialog.Language -> {
            val languages = AppLanguage.entries
            SelectionDialog(
                title = stringResource(Res.string.app_language),
                items = languages,
                selectedItem = settings.language,
                onDismiss = { mainVm.hideDialog() },
                onSelect = { settingsVm.updateSettings(settings.copy(language = it)) },
                itemLabel = { lang ->
                    settingsVm.getLanguageNativeName(lang)?.let { stringResource(it) }
                        ?: stringResource(Res.string.app_language_system)
                }
            )
        }

        null -> {}
    }
}
