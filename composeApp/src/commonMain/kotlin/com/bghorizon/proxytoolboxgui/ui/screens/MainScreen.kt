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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.LocalScaffoldPadding
import com.bghorizon.proxytoolboxgui.ScreenPadding
import com.bghorizon.proxytoolboxgui.ui.removeFabMenuPaddings
import com.bghorizon.proxytoolboxgui.data.AppStatus
import com.bghorizon.proxytoolboxgui.data.BatchProgress
import com.bghorizon.proxytoolboxgui.data.DownloadProgress
import com.bghorizon.proxytoolboxgui.data.TestProgress
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
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
import proxytoolboxgui.composeapp.generated.resources.btn_subs_update
import proxytoolboxgui.composeapp.generated.resources.btn_subs_update_in_progress
import proxytoolboxgui.composeapp.generated.resources.lbl_validation_errors
import proxytoolboxgui.composeapp.generated.resources.btn_web_server
import proxytoolboxgui.composeapp.generated.resources.lbl_working_profiles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    TopAppBar(
        title = { Text(stringResource(Res.string.app_name)) },
        actions = {
            TextButton(onClick = { viewModel.updateSubscriptions() }) {
                Text(
                    if (uiState.downloadProgress.isRunning)
                        stringResource(
                            Res.string.btn_subs_update_in_progress,
                            uiState.downloadProgress.total,
                            uiState.downloadProgress.succeeded,
                            uiState.downloadProgress.failed
                        )
                    else
                        stringResource(Res.string.btn_subs_update)
                )
            }
        }
    )
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val subs by viewModel.subscriptions.collectAsState()

    val testProgress = uiState.testProgress
    val downloadProgress = uiState.downloadProgress
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
            StatsCard(
                found = totalFound,
                duplicated = totalDuplicate,
                parseErr = totalParseErr,
                validErr = totalValidErr,
                working = totalWorking
            )
        }

        if (testProgress.batchProgresses.isNotEmpty()) {
            val groupedBatches = testProgress.batchProgresses.groupBy { it.batchNum }.toList()
                .sortedBy { it.first }

            items(groupedBatches) { (batchNum, rounds) ->
                BatchTable(batchNum, rounds)
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

        if (testProgress.isRunning) {
            item {
                TestProgressBar(testProgress)
            }
        }

        if (downloadProgress.isRunning) {
            item {
                DownloadProgressIndicator(downloadProgress)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainFAB(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val subs by viewModel.subscriptions.collectAsState()
    val totalWorking = subs.sumOf { it.working }

    val appStatus = uiState.appStatus
    val workers = uiState.workers

    val isTesting = appStatus == AppStatus.TESTING

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
                    viewModel.toggleWebServer()
                    expanded = false
                },
                icon = { Icon(Icons.Default.Public, null) },
                text = { Text(stringResource(Res.string.btn_web_server)) }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    if (totalWorking > 0) {
                        viewModel.exportWorkingConfigs()
                        expanded = false
                    }
                },
                icon = { Icon(Icons.Default.Download, null) },
                text = { Text(stringResource(Res.string.btn_export)) }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    if (totalWorking > 0) {
                        viewModel.copyWorkingConfigs()
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
                    viewModel.stopTest()
                } else if (workers.isNotEmpty()) {
                    viewModel.startTest()
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
                        workers.isEmpty() -> stringResource(Res.string.no_workers_found)
                        else -> stringResource(Res.string.btn_test)
                    }
                )
            },
            containerColor = when {
                isTesting -> MaterialTheme.colorScheme.errorContainer
                workers.isEmpty() -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = when {
                isTesting -> MaterialTheme.colorScheme.onErrorContainer
                workers.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}


@Composable
private fun StatsCard(
    found: Int,
    duplicated: Int,
    parseErr: Int,
    validErr: Int,
    working: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatLine(stringResource(Res.string.lbl_profiles_found, found))
            StatLine(stringResource(Res.string.lbl_profiles_duplicated, duplicated))
            StatLine(stringResource(Res.string.lbl_parsing_errors, parseErr))
            StatLine(stringResource(Res.string.lbl_validation_errors, validErr))
            StatLine(
                stringResource(Res.string.lbl_working_profiles, working),
                isHighlighted = true
            )
        }
    }
}

@Composable
private fun StatLine(text: String, isHighlighted: Boolean = false) {
    Text(
        text = text,
        style = if (isHighlighted) {
            MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary
            )
        } else MaterialTheme.typography.bodyMedium,
        color = if (isHighlighted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BatchTable(batchNum: Int, rounds: List<BatchProgress>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = stringResource(Res.string.batch_title, batchNum),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TableHeaderCell(stringResource(Res.string.column_round), Modifier.weight(0.7f))
                TableHeaderCell(stringResource(Res.string.column_total), Modifier.weight(1f))
                TableHeaderCell(stringResource(Res.string.column_running), Modifier.weight(1f))
                TableHeaderCell(stringResource(Res.string.column_failed), Modifier.weight(1f))
                TableHeaderCell(stringResource(Res.string.column_succeeded), Modifier.weight(1f))
            }

            rounds.sortedBy { it.roundNum }.forEach { round ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TableCell(round.roundNum.toString(), Modifier.weight(0.7f))
                    TableCell(round.total.toString(), Modifier.weight(1f))
                    TableCell(round.running.toString(), Modifier.weight(1f))
                    TableCell(round.failed.toString(), Modifier.weight(1f))
                    TableCell(round.succeeded.toString(), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
private fun TableCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
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

@Composable
private fun DownloadProgressIndicator(progress: DownloadProgress) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(
                Res.string.btn_subs_update_in_progress,
                progress.total,
                progress.succeeded,
                progress.failed
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}