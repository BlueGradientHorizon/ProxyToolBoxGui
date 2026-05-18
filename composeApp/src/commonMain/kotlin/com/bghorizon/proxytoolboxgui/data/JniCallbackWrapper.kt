package com.bghorizon.proxytoolboxgui.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("unused")
class JniCallbackWrapper(private val delegate: GoTestCallback) {
    fun onParseFailedJson(json: String) {
        val errors = parseErrorsJson(json)
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onParseFailed(errors)
        }
    }

    fun onValidateFailedJson(json: String) {
        val errors = parseErrorsJson(json)
        CoroutineScope(Dispatchers.Main).launch {
            delegate.onValidateFailed(errors)
        }
    }

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

    private fun parseErrorsJson(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            JsonConfig.json.decodeFromString<Map<String, String>>(json)
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
