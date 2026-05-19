package com.bghorizon.proxytoolboxgui.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WorkerRepository {
    private val _workers = MutableStateFlow<List<WorkerInfo>>(emptyList())
    val workers: StateFlow<List<WorkerInfo>> = _workers.asStateFlow()

    fun setWorkers(workers: List<WorkerInfo>) {
        _workers.value = workers
    }
}
