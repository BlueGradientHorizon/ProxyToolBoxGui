package com.bghorizon.proxytoolboxgui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.data.Subscription
import com.bghorizon.proxytoolboxgui.ui.removeFabMenuPaddings
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.Res
import proxytoolboxgui.composeapp.generated.resources.sub_add
import proxytoolboxgui.composeapp.generated.resources.sub_add_clipboard
import proxytoolboxgui.composeapp.generated.resources.sub_add_manual
import proxytoolboxgui.composeapp.generated.resources.dialog_btn_cancel
import proxytoolboxgui.composeapp.generated.resources.dialog_btn_delete
import proxytoolboxgui.composeapp.generated.resources.sub_del_confirm
import proxytoolboxgui.composeapp.generated.resources.sub_edit
import proxytoolboxgui.composeapp.generated.resources.sub_edit_link
import proxytoolboxgui.composeapp.generated.resources.title_manage_subscriptions
import proxytoolboxgui.composeapp.generated.resources.sub_edit_note
import proxytoolboxgui.composeapp.generated.resources.dialog_btn_save
import proxytoolboxgui.composeapp.generated.resources.sub_item_total_working
import proxytoolboxgui.composeapp.generated.resources.sub_not_updated
import proxytoolboxgui.composeapp.generated.resources.btn_subs_update
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsTopBar(viewModel: MainViewModel) {
    TopAppBar(
        title = { Text(stringResource(Res.string.title_manage_subscriptions)) },
        actions = {
            IconButton(onClick = { viewModel.updateSubscriptions() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(Res.string.btn_subs_update)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubscriptionsFAB(viewModel: MainViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }

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
                expanded = false
            },
            icon = { Icon(Icons.Default.ContentPaste, null) },
            text = { Text(stringResource(Res.string.sub_add_clipboard)) }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                viewModel.showAddSubscription()
                expanded = false
            },
            icon = { Icon(Icons.Default.Edit, null) },
            text = { Text(stringResource(Res.string.sub_add_manual)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val activeDialog = uiState.activeDialog

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subscriptions) { sub ->
            SubscriptionItem(
                subscription = sub,
                onEdit = { viewModel.showEditSubscription(sub) },
                onDelete = { viewModel.showDeleteSubscription(sub) }
            )
        }
    }

    when (activeDialog) {
        is com.bghorizon.proxytoolboxgui.viewmodel.ActiveDialog.AddSubscription -> {
            AddSubscriptionDialog(
                editingSubscription = null,
                onDismiss = { viewModel.hideDialog() },
                onSave = { note, url -> viewModel.saveSubscription(note, url) }
            )
        }

        is com.bghorizon.proxytoolboxgui.viewmodel.ActiveDialog.EditSubscription -> {
            AddSubscriptionDialog(
                editingSubscription = activeDialog.subscription,
                onDismiss = { viewModel.hideDialog() },
                onSave = { note, url -> viewModel.saveSubscription(note, url) }
            )
        }

        is com.bghorizon.proxytoolboxgui.viewmodel.ActiveDialog.DeleteConfirmation -> {
            DeleteConfirmationDialog(
                subscription = activeDialog.subscription,
                onDismiss = { viewModel.hideDialog() },
                onConfirm = { viewModel.confirmDeleteSubscription() }
            )
        }

        else -> {}
    }
}

@Composable
private fun SubscriptionItem(
    subscription: Subscription,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.sub_edit)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.dialog_btn_delete)
                    )
                }
            }
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
                        error = true
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
