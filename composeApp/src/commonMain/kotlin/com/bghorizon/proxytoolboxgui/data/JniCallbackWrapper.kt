package com.bghorizon.proxytoolboxgui.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun parseErrorsJson(json: String): Map<String, String> {
    if (json.isBlank()) return emptyMap()
    return try {
        JsonConfig.json.decodeFromString<Map<String, String>>(json)
    } catch (_: Exception) {
        emptyMap()
    }
}

@Suppress("unused")
class JniErrorCallbackWrapper(private val delegate: GoErrorCallback) {
    fun onError(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onError(message)
        }
    }
}

@Suppress("unused")
class JniParseCallbackWrapper(private val delegate: GoParseCallback) {
    fun onParseFailedJson(json: String) {
        val errors = parseErrorsJson(json)
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onParseFailed(errors)
        }
    }

    fun onError(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onError(message)
        }
    }
}

@Suppress("unused")
class JniValidateCallbackWrapper(private val delegate: GoValidateCallback) {
    fun onValidateFailedJson(json: String) {
        val errors = parseErrorsJson(json)
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onValidateFailed(errors)
        }
    }

    fun onError(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onError(message)
        }
    }
}

@Suppress("unused")
class JniTestCallbackWrapper(private val delegate: GoTestCallback) {
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

    fun onError(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onError(message)
        }
    }
}
