package com.bghorizon.proxytoolboxgui.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bghorizon.proxytoolboxgui.data.Subscription
import com.bghorizon.proxytoolboxgui.viewmodel.MainViewModel
import org.jetbrains.compose.resources.stringResource
import proxytoolboxgui.composeapp.generated.resources.Res
import proxytoolboxgui.composeapp.generated.resources.sub_add
import proxytoolboxgui.composeapp.generated.resources.back
import proxytoolboxgui.composeapp.generated.resources.dialog_btn_cancel
import proxytoolboxgui.composeapp.generated.resources.dialog_btn_delete
import proxytoolboxgui.composeapp.generated.resources.sub_del_confirm
import proxytoolboxgui.composeapp.generated.resources.sub_edit
import proxytoolboxgui.composeapp.generated.resources.sub_edit_link
import proxytoolboxgui.composeapp.generated.resources.title_manage_subscriptions
import proxytoolboxgui.composeapp.generated.resources.sub_edit_note
import proxytoolboxgui.composeapp.generated.resources.dialog_btn_save
import proxytoolboxgui.composeapp.generated.resources.sub_item_total_working
import proxytoolboxgui.composeapp.generated.resources.btn_subs_update
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(viewModel: MainViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val showAddDialog by viewModel.showAddSubscription.collectAsState()
    val editingSub by viewModel.editingSubscription.collectAsState()
    val showDeleteDialog by viewModel.showDeleteConfirmation.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_manage_subscriptions)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.updateSubscriptions() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.btn_subs_update)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddSubscription() }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.sub_add)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            editingSubscription = editingSub,
            onDismiss = { viewModel.hideAddSubscription() },
            onSave = { note, url -> viewModel.saveSubscription(note, url) }
        )
    }

    if (showDeleteDialog != null) {
        DeleteConfirmationDialog(
            subscription = showDeleteDialog!!,
            onDismiss = { viewModel.hideDeleteConfirmation() },
            onConfirm = { viewModel.confirmDeleteSubscription() }
        )
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
                if (subscription.updatedAt > 0) {
                    val instant = Instant.fromEpochMilliseconds(subscription.updatedAt)
                    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                    val dateStr = "${localDateTime.date} ${localDateTime.time.hour.toString().padStart(2, '0')}:${localDateTime.time.minute.toString().padStart(2, '0')}"
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
        title = {
            Text(
                if (editingSubscription != null)
                    stringResource(Res.string.sub_edit)
                else
                    stringResource(Res.string.sub_add)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        error = false
                    },
                    label = { Text(stringResource(Res.string.sub_edit_note)) },
                    isError = error && note.isBlank(),
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
