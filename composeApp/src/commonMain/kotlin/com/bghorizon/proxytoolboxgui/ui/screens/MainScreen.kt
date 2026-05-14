package com.bghorizon.proxytoolboxgui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.LocalScaffoldPadding
import com.bghorizon.proxytoolboxgui.ScreenPadding
import com.bghorizon.proxytoolboxgui.ui.components.*
import com.bghorizon.proxytoolboxgui.ui.removeFabMenuPaddings
import com.bghorizon.proxytoolboxgui.data.AppStatus
import com.bghorizon.proxytoolboxgui.data.TestProgress
import com.bghorizon.proxytoolboxgui.viewmodel.*
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.Res
import proxytoolboxgui.composeapp.generated.resources.app_name
import proxytoolboxgui.composeapp.generated.resources.batch_title
import proxytoolboxgui.composeapp.generated.resources.column_failed
import proxytoolboxgui.composeapp.generated.resources.column_round
import proxytoolboxgui.composeapp.generated.resources.column_running
import proxytoolboxgui.composeapp.generated.resources.column_succeeded
import proxytoolboxgui.composeapp.generated.resources.column_total
import proxytoolboxgui.composeapp.generated.resources.completed
import proxytoolboxgui.composeapp.generated.resources.btn_copy
import proxytoolboxgui.composeapp.generated.resources.error
import proxytoolboxgui.composeapp.generated.resources.btn_export
import proxytoolboxgui.composeapp.generated.resources.no_workers_found
import proxytoolboxgui.composeapp.generated.resources.lbl_parsing_errors
import proxytoolboxgui.composeapp.generated.resources.lbl_profiles_duplicated
import proxytoolboxgui.composeapp.generated.resources.lbl_profiles_found
import proxytoolboxgui.composeapp.generated.resources.ready
import proxytoolboxgui.composeapp.generated.resources.btn_test_stop
import proxytoolboxgui.composeapp.generated.resources.btn_test
import proxytoolboxgui.composeapp.generated.resources.test_progress_status
import proxytoolboxgui.composeapp.generated.resources.testing
import proxytoolboxgui.composeapp.generated.resources.lbl_validation_errors
import proxytoolboxgui.composeapp.generated.resources.btn_web_server
import proxytoolboxgui.composeapp.generated.resources.lbl_working_profiles

sealed interface MainUiMode : ScreenUiMode {
    data object Normal : MainUiMode, ScreenUiMode.Normal
}

data class MainScreenState(
    override val mode: MainUiMode = MainUiMode.Normal
) : AppScreen {
    @Composable
    override fun TopBar(viewModel: MainViewModel) {
        MainTopBar(viewModel)
    }

    @Composable
    override fun Content(viewModel: MainViewModel) {
        MainScreen(viewModel)
    }

    @Composable
    override fun FAB(viewModel: MainViewModel) {
        MainFAB(viewModel)
    }
}

@Composable
fun MainTopBar(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val screen = uiState.screen as? MainScreenState ?: return

    when (screen.mode) {
        is MainUiMode.Normal -> {
            NormalMainTopBar()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalMainTopBar() {
    TopAppBar(
        title = { Text(stringResource(Res.string.app_name)) }
    )
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val subs by viewModel.subscriptions.collectAsState()

    val testProgress = uiState.testProgress
    val appStatus = uiState.appStatus

    val totalFound = subs.sumOf { it.total }
    val totalDuplicate = subs.sumOf { it.duplicated }
    val totalParseErr = subs.sumOf { it.parseErr }
    val totalValidErr = subs.sumOf { it.validErr }
    val totalWorking = subs.sumOf { it.working }

    val scaffoldPadding = LocalScaffoldPadding.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding),
        contentPadding = PaddingValues(
            top = scaffoldPadding.calculateTopPadding() + ScreenPadding,
            bottom = scaffoldPadding.calculateBottomPadding() + ScreenPadding
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            StatsCard {
                StatLine(stringResource(Res.string.lbl_profiles_found, totalFound))
                StatLine(stringResource(Res.string.lbl_profiles_duplicated, totalDuplicate))
                StatLine(stringResource(Res.string.lbl_parsing_errors, totalParseErr))
                StatLine(stringResource(Res.string.lbl_validation_errors, totalValidErr))
                StatLine(
                    stringResource(Res.string.lbl_working_profiles, totalWorking),
                    isHighlighted = true
                )
            }
        }

        if (testProgress.isRunning) {
            item {
                TestProgressBar(testProgress)
            }
        }

        if (testProgress.batchProgresses.isNotEmpty()) {
            val groupedBatches = testProgress.batchProgresses.groupBy { it.batchNum }.toList()
                .sortedBy { it.first }

            items(groupedBatches) { (batchNum, rounds) ->
                BatchTable(
                    title = stringResource(Res.string.batch_title, batchNum),
                    headers = listOf(
                        stringResource(Res.string.column_round),
                        stringResource(Res.string.column_total),
                        stringResource(Res.string.column_running),
                        stringResource(Res.string.column_failed),
                        stringResource(Res.string.column_succeeded)
                    ),
                    headerWeights = listOf(0.7f, 1f, 1f, 1f, 1f),
                    rows = rounds.sortedBy { it.roundNum }.map { round ->
                        listOf(
                            round.roundNum.toString(),
                            round.total.toString(),
                            round.running.toString(),
                            round.failed.toString(),
                            round.succeeded.toString()
                        )
                    }
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (appStatus) {
                            AppStatus.IDLE -> stringResource(Res.string.ready)
                            AppStatus.COMPLETED -> stringResource(Res.string.completed)
                            AppStatus.ERROR -> stringResource(Res.string.error)
                            else -> stringResource(Res.string.testing)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MainFAB(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val screen = uiState.screen as? MainScreenState ?: return
    val subs by viewModel.subscriptions.collectAsState()
    val totalWorking = subs.sumOf { it.working }

    val appStatus = uiState.appStatus
    val workers = uiState.workers
    val isTesting = appStatus == AppStatus.TESTING

    when (screen.mode) {
        is MainUiMode.Normal -> {
            NormalMainFAB(
                isTesting = isTesting,
                workersNotEmpty = workers.isNotEmpty(),
                totalWorking = totalWorking,
                onToggleWebServer = { viewModel.toggleWebServer() },
                onExportWorkingConfigs = { viewModel.exportWorkingConfigs() },
                onCopyWorkingConfigs = { viewModel.copyWorkingConfigs() },
                onStopTest = { viewModel.stopTest() },
                onStartTest = { viewModel.startTest() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NormalMainFAB(
    isTesting: Boolean,
    workersNotEmpty: Boolean,
    totalWorking: Int,
    onToggleWebServer: () -> Unit,
    onExportWorkingConfigs: () -> Unit,
    onCopyWorkingConfigs: () -> Unit,
    onStopTest: () -> Unit,
    onStartTest: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.End
    ) {
        FloatingActionButtonMenu(
            modifier = Modifier.removeFabMenuPaddings(),
            expanded = expanded,
            button = {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = { expanded = !expanded }
                ) {
                    val imageVector by remember {
                        derivedStateOf {
                            if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Share
                        }
                    }
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        modifier = Modifier.animateIcon({ checkedProgress })
                    )
                }
            }
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    onToggleWebServer()
                    expanded = false
                },
                icon = { Icon(Icons.Default.Public, null) },
                text = { Text(stringResource(Res.string.btn_web_server)) }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    if (totalWorking > 0) {
                        onExportWorkingConfigs()
                        expanded = false
                    }
                },
                icon = { Icon(Icons.Default.Download, null) },
                text = { Text(stringResource(Res.string.btn_export)) }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    if (totalWorking > 0) {
                        onCopyWorkingConfigs()
                        expanded = false
                    }
                },
                icon = { Icon(Icons.Default.ContentCopy, null) },
                text = { Text(stringResource(Res.string.btn_copy)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Test FAB
        ExtendedFloatingActionButton(
            onClick = {
                if (isTesting) {
                    onStopTest()
                } else if (workersNotEmpty) {
                    onStartTest()
                }
            },
            icon = {
                Icon(
                    imageVector = if (isTesting) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
            },
            text = {
                Text(
                    text = when {
                        isTesting -> stringResource(Res.string.btn_test_stop)
                        !workersNotEmpty -> stringResource(Res.string.no_workers_found)
                        else -> stringResource(Res.string.btn_test)
                    }
                )
            },
            containerColor = when {
                isTesting -> MaterialTheme.colorScheme.errorContainer
                !workersNotEmpty -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = when {
                isTesting -> MaterialTheme.colorScheme.onErrorContainer
                !workersNotEmpty -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}


@Composable
private fun TestProgressBar(progress: TestProgress) {
    var remaining by remember { mutableIntStateOf(progress.totalSeconds - progress.elapsedSeconds) }
    LaunchedEffect(progress.isRoundActive, progress.totalSeconds, progress.elapsedSeconds) {
        remaining = progress.totalSeconds - progress.elapsedSeconds
        if (!progress.isRoundActive) return@LaunchedEffect
        while (remaining > 0 && progress.isRoundActive) {
            delay(1000)
            remaining--
        }
    }
    val fraction =
        if (progress.totalSeconds > 0) 1f - (remaining.toFloat() / progress.totalSeconds.toFloat()) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                Res.string.test_progress_status,
                remaining,
                progress.currentBatch,
                progress.totalBatches,
                progress.currentRound,
                progress.totalRounds
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
