package com.bghorizon.proxytoolboxgui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.LocalScaffoldPadding
import com.bghorizon.proxytoolboxgui.ScreenPadding
import com.bghorizon.proxytoolboxgui.data.Subscription
import com.bghorizon.proxytoolboxgui.ui.removeFabMenuPaddings
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import com.bghorizon.proxytoolboxgui.viewmodel.UiDialog
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.*
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed interface SubscriptionDialog : UiDialog {
    data object Add : SubscriptionDialog
    data class Edit(val subscription: Subscription) : SubscriptionDialog
    data class Delete(val subscription: Subscription) : SubscriptionDialog
    data object Export : SubscriptionDialog
    data object UpdateConfirm : SubscriptionDialog
    data class UpdateResult(val total: Int, val succeeded: Int, val failed: Int) :
        SubscriptionDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsTopBar(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val isAllSelected =
        viewModel.selectedSubscriptionIds.size == subscriptions.size && subscriptions.isNotEmpty()
    val isUpdating = uiState.subsUpdateProgress.isRunning

    if (viewModel.isExportMode) {
        TopAppBar(
            title = { Text(stringResource(Res.string.title_export_subscriptions)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            navigationIcon = {
                IconButton(onClick = { viewModel.toggleExportMode() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        if (isAllSelected) viewModel.deselectAllSubscriptions()
                        else viewModel.selectAllSubscriptions()
                    }
                ) {
                    Icon(
                        imageVector = if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        contentDescription = stringResource(
                            if (isAllSelected) Res.string.sub_deselect_all else Res.string.sub_select_all
                        )
                    )
                }
            }
        )
    } else {
        TopAppBar(
            title = { Text(stringResource(Res.string.title_manage_subscriptions)) },
            actions = {
                IconButton(
                    onClick = { viewModel.toggleExportMode() },
                    enabled = !isUpdating
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(Res.string.sub_export_share)
                    )
                }
                IconButton(
                    onClick = { viewModel.updateDialog(SubscriptionDialog.UpdateConfirm) },
                    enabled = !isUpdating
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.btn_subs_update)
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubscriptionsFAB(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isUpdating = uiState.subsUpdateProgress.isRunning

    if (viewModel.isExportMode) {
        ExtendedFloatingActionButton(
            onClick = { if (!isUpdating) viewModel.updateDialog(SubscriptionDialog.Export) },
            icon = { Icon(Icons.Default.Share, null) },
            text = { Text(stringResource(Res.string.btn_export_options)) }
        )
    } else {
        var expanded by rememberSaveable { mutableStateOf(false) }

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
                            if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add
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
                    viewModel.importFromClipboard()
                },
                icon = { Icon(Icons.Default.ContentPaste, null) },
                text = { Text(stringResource(Res.string.sub_add_clipboard)) }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    viewModel.updateDialog(SubscriptionDialog.Add)
                },
                icon = { Icon(Icons.Default.Edit, null) },
                text = { Text(stringResource(Res.string.sub_add_manual)) }
            )
        }
    }
}

@Composable
fun SubscriptionsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val activeDialog = uiState.activeDialog

    val scaffoldPadding = LocalScaffoldPadding.current

    val updateProgress = uiState.subsUpdateProgress
    LaunchedEffect(updateProgress.isRunning) {
        if (!updateProgress.isRunning && updateProgress.total > 0) {
            viewModel.updateDialog(
                SubscriptionDialog.UpdateResult(
                    total = updateProgress.total,
                    succeeded = updateProgress.succeeded,
                    failed = updateProgress.failed
                )
            )
            viewModel.clearSubsUpdateProgress()
        }
    }

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
        items(subscriptions) { sub ->
            SubscriptionItem(
                subscription = sub,
                isExportMode = viewModel.isExportMode,
                isSelected = viewModel.selectedSubscriptionIds.contains(sub.id),
                isUpdating = uiState.updatingSubscriptionsIds.contains(sub.id),
                isAnyUpdating = uiState.subsUpdateProgress.isRunning,
                onSelectionChange = { viewModel.toggleSubscriptionSelection(sub.id) },
                onEdit = { viewModel.updateDialog(SubscriptionDialog.Edit(sub)) },
                onDelete = { viewModel.updateDialog(SubscriptionDialog.Delete(sub)) }
            )
        }
    }

    when (val dialog = activeDialog as? SubscriptionDialog) {
        SubscriptionDialog.Add -> {
            AddSubscriptionDialog(
                editingSubscription = null,
                onDismiss = { viewModel.hideDialog() },
                onSave = { note, url -> viewModel.saveSubscription(note, url) }
            )
        }

        is SubscriptionDialog.Edit -> {
            AddSubscriptionDialog(
                editingSubscription = dialog.subscription,
                onDismiss = { viewModel.hideDialog() },
                onSave = { note, url ->
                    viewModel.saveSubscription(note, url, dialog.subscription)
                }
            )
        }

        is SubscriptionDialog.Delete -> {
            DeleteConfirmationDialog(
                subscription = dialog.subscription,
                onDismiss = { viewModel.hideDialog() },
                onConfirm = { viewModel.confirmDeleteSubscription(dialog.subscription.id) }
            )
        }

        SubscriptionDialog.Export -> {
            ExportOptionsDialog(
                onDismiss = { viewModel.hideDialog() },
                onExportToClipboard = { includeNotes ->
                    viewModel.exportSelectedToClipboard(includeNotes)
                }
            )
        }

        is SubscriptionDialog.UpdateResult -> {
            UpdateResultDialog(
                total = dialog.total,
                succeeded = dialog.succeeded,
                failed = dialog.failed,
                onDismiss = { viewModel.hideDialog() }
            )
        }

        SubscriptionDialog.UpdateConfirm -> {
            UpdateConfirmationDialog(
                onDismiss = { viewModel.hideDialog() },
                onConfirm = {
                    viewModel.hideDialog()
                    viewModel.updateSubscriptions()
                }
            )
        }

        null -> {}
    }
}

@Composable
private fun SubscriptionItem(
    subscription: Subscription,
    isExportMode: Boolean,
    isSelected: Boolean,
    isUpdating: Boolean,
    isAnyUpdating: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        onClick = { if (isExportMode) onSelectionChange(!isSelected) }
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
            if (isExportMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectionChange
                )
            } else {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onEdit, enabled = !isAnyUpdating) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.sub_edit)
                            )
                        }
                        IconButton(onClick = onDelete, enabled = !isAnyUpdating) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(Res.string.dialog_btn_delete)
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
    onExportToClipboard: (Boolean) -> Unit
) {
    var includeNotes by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.btn_export_options)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { /* Not implemented */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.export_show_qr))
                }
                Button(
                    onClick = { onExportToClipboard(includeNotes) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentPaste, null)
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_btn_cancel))
            }
        }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Text(
                if (editingSubscription != null)
                    stringResource(Res.string.sub_edit)
                else
                    stringResource(Res.string.sub_add)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (note.isBlank() || url.isBlank()) {
//                        error = true
                    } else {
                        onSave(note, url)
                    }
                }
            ) {
                Text(stringResource(Res.string.dialog_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_btn_cancel))
            }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    subscription: Subscription,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_btn_delete)) },
        text = {
            Text(stringResource(Res.string.sub_del_confirm, subscription.note))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(Res.string.dialog_btn_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_btn_cancel))
            }
        }
    )
}

@Composable
private fun UpdateConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.btn_subs_update)) },
        text = {
            Text(stringResource(Res.string.sub_update_confirm))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(Res.string.btn_subs_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_btn_cancel))
            }
        }
    )
}

@Composable
private fun UpdateResultDialog(
    total: Int,
    succeeded: Int,
    failed: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.btn_subs_update)) },
        text = {
            Text(
                stringResource(
                    Res.string.btn_subs_update_result,
                    total,
                    failed,
                    succeeded
                )
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_btn_close))
            }
        }
    )
}
