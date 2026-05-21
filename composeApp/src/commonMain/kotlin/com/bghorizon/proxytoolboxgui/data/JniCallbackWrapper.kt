package com.bghorizon.proxytoolboxgui.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("unused")
class JniLatencyTestCallbackWrapper(private val delegate: GoLatencyTestCallback) {
    fun onRoundStarted(batch: Long, round: Long, total: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onRoundStarted(batch, round, total)
        }
    }

    fun onProgress(tag: String, delay: Long, failed: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onProgress(tag, delay, failed)
        }
    }

    fun onRoundEnded(batch: Long, round: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onRoundEnded(batch, round)
        }
    }
}

@Suppress("unused")
class JniSpeedTestCallbackWrapper(private val delegate: GoSpeedTestCallback) {
    fun onRoundStarted(batch: Long, round: Long, total: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onRoundStarted(batch, round, total)
        }
    }

    fun onProgress(tag: String, speed: Double, failed: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onProgress(tag, speed, failed)
        }
    }

    fun onRoundEnded(batch: Long, round: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onRoundEnded(batch, round)
        }
    }
}
