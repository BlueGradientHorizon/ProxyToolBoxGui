package com.bghorizon.proxytoolboxgui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.data.AppStatus
import com.bghorizon.proxytoolboxgui.data.BatchProgress
import com.bghorizon.proxytoolboxgui.data.DownloadProgress
import com.bghorizon.proxytoolboxgui.data.TestProgress
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import com.bghorizon.proxytoolboxgui.viewmodel.Screen
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.Res
import proxytoolboxgui.composeapp.generated.resources.app_name
import proxytoolboxgui.composeapp.generated.resources.batch_title
import proxytoolboxgui.composeapp.generated.resources.column_failed
import proxytoolboxgui.composeapp.generated.resources.column_running
import proxytoolboxgui.composeapp.generated.resources.column_succeeded
import proxytoolboxgui.composeapp.generated.resources.column_total
import proxytoolboxgui.composeapp.generated.resources.completed
import proxytoolboxgui.composeapp.generated.resources.copy
import proxytoolboxgui.composeapp.generated.resources.error
import proxytoolboxgui.composeapp.generated.resources.export
import proxytoolboxgui.composeapp.generated.resources.no_workers_found
import proxytoolboxgui.composeapp.generated.resources.parsing_errors
import proxytoolboxgui.composeapp.generated.resources.profiles_duplicated
import proxytoolboxgui.composeapp.generated.resources.profiles_found
import proxytoolboxgui.composeapp.generated.resources.ready
import proxytoolboxgui.composeapp.generated.resources.settings
import proxytoolboxgui.composeapp.generated.resources.stop
import proxytoolboxgui.composeapp.generated.resources.subscriptions
import proxytoolboxgui.composeapp.generated.resources.test
import proxytoolboxgui.composeapp.generated.resources.test_progress_status
import proxytoolboxgui.composeapp.generated.resources.testing
import proxytoolboxgui.composeapp.generated.resources.update
import proxytoolboxgui.composeapp.generated.resources.updating_progress
import proxytoolboxgui.composeapp.generated.resources.validation_errors
import proxytoolboxgui.composeapp.generated.resources.web_server
import proxytoolboxgui.composeapp.generated.resources.working_profiles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val subs by viewModel.subscriptions.collectAsState()
    val testProgress by viewModel.testProgress.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val appStatus by viewModel.appStatus.collectAsState()
    val workers by viewModel.workers.collectAsState()
    val webServerRunning by viewModel.webServerRunning.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val workingConfigs by viewModel.workingConfigs.collectAsState()

    val totalFound = subs.sumOf { it.total }
    val totalDuplicate = subs.sumOf { it.duplicated }
    val totalParseErr = subs.sumOf { it.parseErr }
    val totalValidErr = subs.sumOf { it.validErr }
    val totalWorking = subs.sumOf { it.working }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                actions = {
                    TextButton(onClick = { viewModel.updateSubscriptions() }) {
                        val dp by viewModel.downloadProgress.collectAsState()
                        Text(
                            if (dp.isRunning)
                                stringResource(Res.string.updating_progress, dp.total, dp.succeeded, dp.failed)
                            else
                                stringResource(Res.string.update)
                        )
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.Subscriptions) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(Res.string.subscriptions)
                        )
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.Settings) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(Res.string.settings)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsCard(
                found = totalFound,
                duplicated = totalDuplicate,
                parseErr = totalParseErr,
                validErr = totalValidErr,
                working = totalWorking
            )

            if (testProgress.batchProgresses.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(testProgress.batchProgresses) { batch ->
                        BatchTable(batch)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
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

            if (testProgress.isRunning) {
                TestProgressBar(testProgress)
            }

            if (downloadProgress.isRunning) {
                DownloadProgressIndicator(downloadProgress)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.copyWorkingConfigs() },
                    modifier = Modifier.weight(1f),
                    enabled = totalWorking > 0
                ) {
                    Text(stringResource(Res.string.copy))
                }
                Button(
                    onClick = { viewModel.exportWorkingConfigs() },
                    modifier = Modifier.weight(1f),
                    enabled = totalWorking > 0
                ) {
                    Text(stringResource(Res.string.export))
                }
                Button(
                    onClick = { viewModel.toggleWebServer() },
                    modifier = Modifier.weight(1f),
                    colors = if (webServerRunning) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else ButtonDefaults.buttonColors()
                ) {
                    Text(stringResource(Res.string.web_server))
                }
            }

            Button(
                onClick = {
                    if (appStatus == AppStatus.TESTING) {
                        viewModel.stopTest()
                    } else {
                        viewModel.startTest()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = workers.isNotEmpty() || appStatus == AppStatus.TESTING,
                colors = if (appStatus == AppStatus.TESTING) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = when {
                        appStatus == AppStatus.TESTING -> stringResource(Res.string.stop)
                        workers.isEmpty() -> stringResource(Res.string.no_workers_found)
                        else -> stringResource(Res.string.test)
                    }
                )
            }
        }
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
            StatLine(stringResource(Res.string.profiles_found, found))
            StatLine(stringResource(Res.string.profiles_duplicated, duplicated))
            StatLine(stringResource(Res.string.parsing_errors, parseErr))
            StatLine(stringResource(Res.string.validation_errors, validErr))
            StatLine(
                stringResource(Res.string.working_profiles, working),
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
private fun BatchTable(batch: BatchProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = stringResource(Res.string.batch_title, batch.batchNum),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TableHeaderCell(stringResource(Res.string.column_total), Modifier.weight(1f))
                TableHeaderCell(stringResource(Res.string.column_running), Modifier.weight(1f))
                TableHeaderCell(stringResource(Res.string.column_failed), Modifier.weight(1f))
                TableHeaderCell(stringResource(Res.string.column_succeeded), Modifier.weight(1f))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TableCell(batch.total.toString(), Modifier.weight(1f))
                TableCell(batch.running.toString(), Modifier.weight(1f))
                TableCell(batch.failed.toString(), Modifier.weight(1f))
                TableCell(batch.succeeded.toString(), Modifier.weight(1f))
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
    val fraction = if (progress.totalSeconds > 0) 1f - (remaining.toFloat() / progress.totalSeconds.toFloat()) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(Res.string.test_progress_status, remaining, progress.currentBatch, progress.totalBatches, progress.currentRound, progress.totalRounds),
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
                Res.string.updating_progress,
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