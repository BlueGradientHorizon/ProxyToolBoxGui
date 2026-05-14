package com.bghorizon.proxytoolboxgui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bghorizon.proxytoolboxgui.data.*
import com.bghorizon.proxytoolboxgui.di.AppModule
import com.bghorizon.proxytoolboxgui.ui.screens.SubscriptionsScreenUiMode
import com.bghorizon.proxytoolboxgui.utils.ConfigUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.getString
import proxytoolboxgui.composeapp.generated.resources.*

class SubscriptionsScreenViewModel(private val module: AppModule) : ViewModel() {
    private val _uiState = MutableStateFlow(SubscriptionsScreenUiState())
    val uiState = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    val subscriptions: StateFlow<List<Subscription>> = module.subscriptionRepository.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateMode(mode: SubscriptionsScreenUiMode) {
        _uiState.update { state ->
            state.copy(
                mode = mode,
                selectedIds = if (mode is SubscriptionsScreenUiMode.Normal) emptySet() else state.selectedIds
            )
        }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedIds.contains(id)) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(selectedIds = newSelection)
        }
    }

    fun selectAll() {
        val ids = subscriptions.value.map { it.id }
        _uiState.update { it.copy(selectedIds = ids.toSet()) }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun updateSubscriptions(mainVm: MainViewModel) {
        if (downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            mainVm.updateAppStatus(AppStatus.UPDATING_SUBS)
            val subs = subscriptions.value
            _uiState.update {
                it.copy(
                    updatingIds = subs.map { it.id }.toSet(),
                    updateProgress = SubsUpdateProgress(
                        total = subs.size,
                        isRunning = true
                    )
                )
            }

            val settings = module.settingsRepository.settings.value

            try {
                SubscriptionDownloader.downloadParallel(
                    urls = subs,
                    getUrl = { it.url },
                    timeoutSeconds = settings.downloadTimeout,
                    maxParallel = settings.parallelSubscriptionDownloads,
                    onDownloadComplete = { sub, content ->
                        val lines = content.lines().filter { it.isNotBlank() }

                        // 1. Save metadata
                        val updatedSub = sub.copy(updatedAt = System.currentTimeMillis())
                        module.subscriptionRepository.saveSub(updatedSub)

                        // 2. Save child data (URIs)
                        module.subscriptionRepository.setConfigsUris(sub.id, lines)

                        _uiState.update {
                            it.copy(
                                updatingIds = it.updatingIds - sub.id,
                                updateProgress = it.updateProgress.copy(
                                    succeeded = it.updateProgress.succeeded + 1
                                )
                            )
                        }
                    },
                    onDownloadError = { sub, e ->
                        e.printStackTrace()
                        _uiState.update {
                            it.copy(
                                updatingIds = it.updatingIds - sub.id,
                                updateProgress = it.updateProgress.copy(
                                    failed = it.updateProgress.failed + 1
                                )
                            )
                        }
                    }
                )
            } finally {
                _uiState.update {
                    it.copy(updateProgress = it.updateProgress.copy(isRunning = false))
                }
                mainVm.updateAppStatus(AppStatus.IDLE)
            }
        }
    }

    fun clearUpdateProgress() {
        _uiState.update { it.copy(updateProgress = SubsUpdateProgress()) }
    }

    fun importFromClipboard() {
        val text = module.platform.getClipboardText() ?: return
        importFromUrl(text)
    }

    fun exportSelectedToClipboard(includeNotes: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedIds = _uiState.value.selectedIds
            val selected = subscriptions.value.filter { selectedIds.contains(it.id) }
            if (selected.isEmpty()) return@launch

            val text = selected.joinToString("\n") {
                if (includeNotes) "${it.note} ${it.url}" else it.url
            }

            val msg = getString(Res.string.msg_copied_to_clipboard)
            val label = getString(Res.string.label_proxy_configs)
            withContext(Dispatchers.Main) {
                module.platform.copyToClipboard(text, label)
                module.platform.showToast(msg)
                updateMode(SubscriptionsScreenUiMode.Normal)
            }
        }
    }

    fun getSelectedExportText(includeNotes: Boolean): String {
        val selectedIds = _uiState.value.selectedIds
        val selected = subscriptions.value.filter { selectedIds.contains(it.id) }
        return selected.joinToString("\n") {
            if (includeNotes) "${it.note} ${it.url}" else it.url
        }
    }

    fun saveSubscription(note: String, url: String, existing: Subscription? = null) {
        val newSub = existing?.copy(note = note, url = url)
            ?: Subscription(
                id = ConfigUtils.generateUUID(),
                note = note,
                url = url
            )
        viewModelScope.launch {
            module.subscriptionRepository.saveSub(newSub)
        }
    }

    fun confirmDeleteSubscription(subId: String) {
        viewModelScope.launch {
            module.subscriptionRepository.deleteSub(subId)
        }
    }

    fun importFromUrl(content: String) {
        viewModelScope.launch {
            val unnamedStr = getString(Res.string.sub_unnamed)
            val subs = content.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { line ->
                    val lastSpaceIndex = line.lastIndexOf(' ')
                    if (lastSpaceIndex != -1) {
                        val note = line.substring(0, lastSpaceIndex).trim()
                        val url = line.substring(lastSpaceIndex + 1).trim()
                        if (note.isEmpty()) unnamedStr to url else note to url
                    } else {
                        unnamedStr to line
                    }
                }
            if (subs.isNotEmpty()) {
                subs.forEach { (note, url) ->
                    module.subscriptionRepository.saveSub(
                        Subscription(
                            id = ConfigUtils.generateUUID(),
                            note = note,
                            url = url
                        )
                    )
                }
                module.platform.showToast(getString(Res.string.msg_imported_subs, subs.size))
            } else {
                module.platform.showToast(getString(Res.string.msg_no_subs_found))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
    }
}

data class SubscriptionsScreenUiState(
    val mode: SubscriptionsScreenUiMode = SubscriptionsScreenUiMode.Normal,
    val selectedIds: Set<String> = emptySet(),
    val updatingIds: Set<String> = emptySet(),
    val updateProgress: SubsUpdateProgress = SubsUpdateProgress()
)
