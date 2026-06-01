package com.bghorizon.proxytoolboxgui.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import com.bghorizon.proxytoolboxgui.LocalScaffoldPadding
import com.bghorizon.proxytoolboxgui.ScreenPadding
import com.bghorizon.proxytoolboxgui.data.Subscription
import com.bghorizon.proxytoolboxgui.data.DownloadProgress
import com.bghorizon.proxytoolboxgui.ui.components.*
import com.bghorizon.proxytoolboxgui.ui.removeFabMenuPaddings
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bghorizon.proxytoolboxgui.di.LocalAppModule
import com.bghorizon.proxytoolboxgui.viewmodel.*
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.*
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed interface SubscriptionsScreenDialog : UiDialog {
    data object Add : SubscriptionsScreenDialog
    data object Scan : SubscriptionsScreenDialog
    data class Edit(val subscription: Subscription) : SubscriptionsScreenDialog
    data class Delete(val subscription: Subscription) : SubscriptionsScreenDialog
    data class DeleteSelected(val count: Int) : SubscriptionsScreenDialog
    data object Export : SubscriptionsScreenDialog
    data class QrCode(val content: String) : SubscriptionsScreenDialog
    data object UpdateConfirm : SubscriptionsScreenDialog
    data class UpdateResult(val total: Int, val succeeded: Int, val failed: Int) :
        SubscriptionsScreenDialog
}

sealed interface SubscriptionsScreenUiMode : ScreenUiMode {
    data object Normal : SubscriptionsScreenUiMode, ScreenUiMode.Normal
    data object Selection : SubscriptionsScreenUiMode
}

data class SubscriptionsScreenState(
    override val mode: SubscriptionsScreenUiMode = SubscriptionsScreenUiMode.Normal,
    val selectedIds: Set<String> = emptySet()
) : AppScreen {
    @Composable
    override fun TopBar(mainVm: MainViewModel) {
        val module = LocalAppModule.current
        val subVm: SubscriptionsScreenViewModel = viewModel { SubscriptionsScreenViewModel(module) }
        SubscriptionsScreenTopBar(mainVm, subVm)
    }

    @Composable
    override fun Content(mainVm: MainViewModel) {
        val module = LocalAppModule.current
        val subVm: SubscriptionsScreenViewModel = viewModel { SubscriptionsScreenViewModel(module) }
        SubscriptionsScreen(mainVm, subVm)
    }

    @Composable
    override fun FAB(mainVm: MainViewModel) {
        val module = LocalAppModule.current
        val subVm: SubscriptionsScreenViewModel = viewModel { SubscriptionsScreenViewModel(module) }
        SubscriptionsScreenFAB(mainVm, subVm)
    }
}

@Composable
fun SubscriptionsScreenTopBar(mainVm: MainViewModel, subVm: SubscriptionsScreenViewModel) {
    val subUiState by subVm.uiState.collectAsState()
    val subscriptions by subVm.subscriptions.collectAsState()
    val isAllSelected =
        (subUiState.selectedIds.size == subscriptions.size) && subscriptions.isNotEmpty()
    val isUpdating = subUiState.updateProgress.isRunning

    when (subUiState.mode) {
        is SubscriptionsScreenUiMode.Selection -> {
            SelectionSubscriptionsTopBar(
                isAllSelected = isAllSelected,
                selectedCount = subUiState.selectedIds.size,
                onBack = { subVm.updateMode(SubscriptionsScreenUiMode.Normal) },
                onToggleSelectAll = {
                    if (isAllSelected) subVm.deselectAll()
                    else subVm.selectAll()
                },
                onDeleteSelected = {
                    mainVm.updateDialog(SubscriptionsScreenDialog.DeleteSelected(subUiState.selectedIds.size))
                }
            )
        }

        is SubscriptionsScreenUiMode.Normal -> {
            NormalSubscriptionsTopBar(
                isUpdating = isUpdating,
                onSelectionMode = { subVm.updateMode(SubscriptionsScreenUiMode.Selection) },
                onUpdateConfirm = { mainVm.updateDialog(SubscriptionsScreenDialog.UpdateConfirm) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionSubscriptionsTopBar(
    isAllSelected: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.title_select_subscriptions)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Arrow_back,
                    contentDescription = stringResource(Res.string.back)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Delete,
                    contentDescription = stringResource(Res.string.dialog_btn_delete)
                )
            }
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    imageVector = if (isAllSelected) MaterialSymbols.Rounded.Deselect else MaterialSymbols.Rounded.Select_all,
                    contentDescription = stringResource(
                        if (isAllSelected) Res.string.sub_deselect_all else Res.string.sub_select_all
                    )
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalSubscriptionsTopBar(
    isUpdating: Boolean,
    onSelectionMode: () -> Unit,
    onUpdateConfirm: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.title_manage_subscriptions)) },
        actions = {
            IconButton(
                onClick = onSelectionMode,
                enabled = !isUpdating
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Library_add_check,
                    contentDescription = stringResource(Res.string.sub_select_mode)
                )
            }
            IconButton(
                onClick = onUpdateConfirm,
                enabled = !isUpdating
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Refresh,
                    contentDescription = stringResource(Res.string.btn_subs_update)
                )
            }
        }
    )
}

@Composable
fun SubscriptionsScreenFAB(mainVm: MainViewModel, subVm: SubscriptionsScreenViewModel) {
    val subUiState by subVm.uiState.collectAsState()
    val isUpdating = subUiState.updateProgress.isRunning

    when (subUiState.mode) {
        is SubscriptionsScreenUiMode.Selection -> {
            SelectionSubscriptionsFAB(
                isUpdating = isUpdating,
                onExport = { mainVm.updateDialog(SubscriptionsScreenDialog.Export) }
            )
        }

        is SubscriptionsScreenUiMode.Normal -> {
            NormalSubscriptionsFAB(
                isUpdating = isUpdating,
                onImportClipboard = { subVm.importFromClipboard() },
                onImportQr = { mainVm.updateDialog(SubscriptionsScreenDialog.Scan) },
                onAddManual = { mainVm.updateDialog(SubscriptionsScreenDialog.Add) }
            )
        }
    }
}

@Composable
private fun SelectionSubscriptionsFAB(
    isUpdating: Boolean,
    onExport: () -> Unit
) {
    ExtendedFloatingActionButton(
        onClick = { if (!isUpdating) onExport() },
        icon = { Icon(MaterialSymbols.Rounded.Share, null) },
        text = { Text(stringResource(Res.string.btn_export_options)) }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NormalSubscriptionsFAB(
    isUpdating: Boolean,
    onImportClipboard: () -> Unit,
    onImportQr: () -> Unit,
    onAddManual: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(value = false) }

    FloatingActionButtonMenu(
        modifier = Modifier.removeFabMenuPaddings(),
        expanded = expanded && !isUpdating,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { if (!isUpdating) expanded = !expanded }
            ) {
                val imageVector by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) MaterialSymbols.Rounded.Close else MaterialSymbols.Rounded.Add
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
                onImportClipboard()
                @Suppress("AssignedValueIsNeverRead")
                expanded = false
            },
            icon = { Icon(MaterialSymbols.Rounded.Content_paste, null) },
            text = { Text(stringResource(Res.string.sub_add_clipboard)) }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onImportQr()
                @Suppress("AssignedValueIsNeverRead")
                expanded = false
            },
            icon = { Icon(MaterialSymbols.Rounded.Qr_code_scanner, null) },
            text = { Text(stringResource(Res.string.sub_add_qr)) }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onAddManual()
                @Suppress("AssignedValueIsNeverRead")
                expanded = false
            },
            icon = { Icon(MaterialSymbols.Rounded.Edit, null) },
            text = { Text(stringResource(Res.string.sub_add_manual)) }
        )
    }
}

@Composable
fun SubscriptionsScreen(mainVm: MainViewModel, subVm: SubscriptionsScreenViewModel) {
    val mainUiState by mainVm.uiState.collectAsState()
    val subUiState by subVm.uiState.collectAsState()
    val subscriptions by subVm.subscriptions.collectAsState()
    val activeDialog = mainUiState.activeDialog

    BackHandler(enabled = subUiState.mode is SubscriptionsScreenUiMode.Selection) {
        subVm.updateMode(SubscriptionsScreenUiMode.Normal)
    }

    val scaffoldPadding = LocalScaffoldPadding.current
    val totalFound = subscriptions.sumOf { it.total }
    val totalDuplicate = subscriptions.sumOf { it.duplicated }
    val profilesToTest = subscriptions.filter { it.includeInTest }.sumOf { it.total }

    val updateProgress = subUiState.updateProgress
    LaunchedEffect(updateProgress.isRunning) {
        if (!updateProgress.isRunning && (updateProgress.total > 0)) {
            mainVm.updateDialog(
                SubscriptionsScreenDialog.UpdateResult(
                    total = updateProgress.total,
                    succeeded = updateProgress.succeeded,
                    failed = updateProgress.failed
                )
            )
            subVm.clearUpdateProgress()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatsCard(
            modifier = Modifier
                .padding(horizontal = ScreenPadding)
                .padding(top = ScreenPadding, bottom = 8.dp),
        ) {
            StatLine(stringResource(Res.string.lbl_profiles_found, totalFound))
            StatLine(stringResource(Res.string.lbl_profiles_duplicated, totalDuplicate))
            StatLine(stringResource(Res.string.lbl_profiles_to_test, profilesToTest))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = ScreenPadding),
            contentPadding = PaddingValues(
                bottom = scaffoldPadding.calculateBottomPadding() + ScreenPadding
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(subscriptions) { sub ->
            SubscriptionItem(
                subscription = sub,
                uiMode = subUiState.mode,
                isSelected = subUiState.selectedIds.contains(sub.id),
                isUpdating = subUiState.updatingIds.contains(sub.id),
                downloadProgress = subUiState.updateProgress.downloadProgress[sub.id],
                isAnyUpdating = subUiState.updateProgress.isRunning,
                onIncludeInTestChange = { include ->
                    subVm.setIncludeInTest(sub.id, include)
                },
                onSelectionChange = { subVm.toggleSelection(sub.id) },
                onEdit = { mainVm.updateDialog(SubscriptionsScreenDialog.Edit(sub)) },
                onDelete = { mainVm.updateDialog(SubscriptionsScreenDialog.Delete(sub)) },
                onLongClick = {
                    if (subUiState.mode is SubscriptionsScreenUiMode.Normal) {
                        subVm.updateMode(SubscriptionsScreenUiMode.Selection)
                        subVm.toggleSelection(sub.id)
                    }
                }
            )
        }
        }
    }

    when (val dialog = activeDialog as? SubscriptionsScreenDialog) {
        SubscriptionsScreenDialog.Add -> {
            AddSubscriptionDialog(
                editingSubscription = null,
                onDismiss = { mainVm.hideDialog() },
                onSave = { note, url -> subVm.saveSubscription(note, url) }
            )
        }

        SubscriptionsScreenDialog.Scan -> {
            ScanQrCodeDialog(
                onDismiss = { mainVm.hideDialog() },
                onCodeScanned = { result ->
                    mainVm.hideDialog()
                    subVm.importFromUrl(result)
                },
                isScannerSupported = mainUiState.isQrScannerSupported,
                onPickImage = { mainVm.module.platform.pickImageAndScanQr() }
            )
        }

        is SubscriptionsScreenDialog.Edit -> {
            AddSubscriptionDialog(
                editingSubscription = dialog.subscription,
                onDismiss = { mainVm.hideDialog() },
                onSave = { note, url ->
                    subVm.saveSubscription(note, url, dialog.subscription)
                }
            )
        }

        is SubscriptionsScreenDialog.Delete -> {
            ConfirmationDialog(
                title = stringResource(Res.string.dialog_btn_delete),
                message = stringResource(Res.string.sub_del_confirm, dialog.subscription.note),
                onDismiss = { mainVm.hideDialog() },
                onConfirm = { subVm.confirmDeleteSubscription(dialog.subscription.id) },
                confirmText = stringResource(Res.string.dialog_btn_delete),
                isDestructive = true
            )
        }

        is SubscriptionsScreenDialog.DeleteSelected -> {
            ConfirmationDialog(
                title = stringResource(Res.string.dialog_btn_delete),
                message = stringResource(Res.string.sub_del_multiple_confirm, dialog.count),
                onDismiss = { mainVm.hideDialog() },
                onConfirm = {
                    mainVm.hideDialog()
                    subVm.deleteSelectedSubscriptions()
                },
                confirmText = stringResource(Res.string.dialog_btn_delete),
                isDestructive = true
            )
        }

        SubscriptionsScreenDialog.Export -> {
            ExportOptionsDialog(
                onDismiss = { mainVm.hideDialog() },
                onExportToClipboard = { includeNotes ->
                    subVm.exportSelectedToClipboard(includeNotes)
                },
                onShowQrCode = { includeNotes ->
                    val content = subVm.getSelectedExportText(includeNotes)
                    mainVm.updateDialog(SubscriptionsScreenDialog.QrCode(content))
                }
            )
        }

        is SubscriptionsScreenDialog.QrCode -> {
            ShowQrCodeDialog(
                content = dialog.content,
                onDismiss = { mainVm.hideDialog() }
            )
        }

        is SubscriptionsScreenDialog.UpdateResult -> {
            SimpleAlertDialog(
                title = stringResource(Res.string.btn_subs_update),
                onDismiss = { mainVm.hideDialog() },
                confirmText = stringResource(Res.string.dialog_btn_close),
            ) {
                Text(
                    stringResource(
                        Res.string.btn_subs_update_result,
                        dialog.total,
                        dialog.failed,
                        dialog.succeeded
                    )
                )
            }
        }

        SubscriptionsScreenDialog.UpdateConfirm -> {
            ConfirmationDialog(
                title = stringResource(Res.string.btn_subs_update),
                message = stringResource(Res.string.sub_update_confirm),
                onDismiss = { mainVm.hideDialog() },
                onConfirm = {
                    mainVm.hideDialog()
                    subVm.updateSubscriptions()
                },
                confirmText = stringResource(Res.string.btn_subs_update),
                isDestructive = true
            )
        }

        null -> {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubscriptionItem(
    subscription: Subscription,
    uiMode: ScreenUiMode?,
    isSelected: Boolean,
    isUpdating: Boolean,
    downloadProgress: DownloadProgress?,
    isAnyUpdating: Boolean,
    onIncludeInTestChange: (Boolean) -> Unit,
    onSelectionChange: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    when (uiMode) {
                        is SubscriptionsScreenUiMode.Selection -> onSelectionChange()
                        else -> { /* No-op for now */
                        }
                    }
                },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.note,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        Res.string.sub_item_total_working,
                        subscription.total,
                        subscription.working
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val dateStr = if (subscription.updatedAt > 0) {
                    val instant = Instant.fromEpochMilliseconds(subscription.updatedAt)
                    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                    "${localDateTime.date} ${
                        localDateTime.time.hour.toString().padStart(2, '0')
                    }:${localDateTime.time.minute.toString().padStart(2, '0')}"
                } else {
                    stringResource(Res.string.sub_not_updated)
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (uiMode) {
                is SubscriptionsScreenUiMode.Selection -> {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectionChange() }
                    )
                }

                else -> {
                    val includeInTestLabel = stringResource(Res.string.sub_include_in_test)
                    if (isUpdating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (downloadProgress != null) {
                                val unitIdx = if (downloadProgress.totalBytes > 0)
                                    getUnitIndex(downloadProgress.totalBytes)
                                else getUnitIndex(downloadProgress.downloadedBytes)

                                val downloadedStr =
                                    formatBytes(downloadProgress.downloadedBytes, unitIdx)
                                val totalStr = if (downloadProgress.totalBytes > 0)
                                    formatBytes(downloadProgress.totalBytes, unitIdx)
                                else "?"
                                Text(
                                    text = "$downloadedStr / $totalStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = stringResource(Res.string.sub_in_queue),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(4.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onEdit, enabled = !isAnyUpdating) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Edit,
                                    contentDescription = stringResource(Res.string.sub_edit)
                                )
                            }
                            IconButton(onClick = onDelete, enabled = !isAnyUpdating) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Delete,
                                    contentDescription = stringResource(Res.string.dialog_btn_delete)
                                )
                            }
                            Switch(
                                checked = subscription.includeInTest,
                                onCheckedChange = onIncludeInTestChange,
                                enabled = !isAnyUpdating,
                                modifier = Modifier.semantics {
                                    contentDescription = includeInTestLabel
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportOptionsDialog(
    onDismiss: () -> Unit,
    onExportToClipboard: (Boolean) -> Unit,
    onShowQrCode: (Boolean) -> Unit
) {
    var includeNotes by remember { mutableStateOf(true) }

    SimpleAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.btn_export_options),
    ) {
        Button(
            onClick = { onShowQrCode(includeNotes) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(MaterialSymbols.Rounded.Qr_code, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.export_show_qr))
        }
        Button(
            onClick = { onExportToClipboard(includeNotes) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(MaterialSymbols.Rounded.Content_paste, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.export_to_clipboard))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = includeNotes,
                onCheckedChange = { includeNotes = it }
            )
            Text(
                text = stringResource(Res.string.export_include_notes),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AddSubscriptionDialog(
    editingSubscription: Subscription?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var note by remember { mutableStateOf(editingSubscription?.note ?: "") }
    var url by remember { mutableStateOf(editingSubscription?.url ?: "") }
    var error by remember { mutableStateOf(false) }

    SimpleAlertDialog(
        onDismiss = onDismiss,
        title = if (editingSubscription != null)
            stringResource(Res.string.sub_edit)
        else
            stringResource(Res.string.sub_add),
        confirmText = stringResource(Res.string.dialog_btn_save),
        onConfirm = {
            if (note.isBlank() || url.isBlank()) {
                @Suppress("AssignedValueIsNeverRead")
                error = true
                false
            } else {
                onSave(note, url)
                true
            }
        }
    ) {
        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it
                error = false
            },
            label = { Text(stringResource(Res.string.sub_edit_note)) },
            isError = error && note.isBlank(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                error = false
            },
            label = { Text(stringResource(Res.string.sub_edit_link)) },
            isError = error && url.isBlank(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

private fun formatBytes(bytes: Long, unitIndex: Int = -1): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    if (unitIndex != -1) {
        var value = bytes.toDouble()
        repeat(unitIndex) { value /= 1024.0 }
        return if (unitIndex == 0) "${value.toLong()} B" else {
            val rounded = (value * 10).toLong() / 10.0
            "$rounded ${units[unitIndex]}"
        }
    }

    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble() / 1024.0
    var i = 1
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024.0
        i++
    }
    val rounded = (value * 10).toLong() / 10.0
    return "$rounded ${units[i]}"
}

private fun getUnitIndex(bytes: Long): Int {
    if (bytes < 1024) return 0
    var value = bytes.toDouble() / 1024.0
    var i = 1
    while (value >= 1024 && i < 4) {
        value /= 1024.0
        i++
    }
    return i
}
