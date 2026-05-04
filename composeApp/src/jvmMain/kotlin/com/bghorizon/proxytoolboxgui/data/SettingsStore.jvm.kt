package com.bghorizon.proxytoolboxgui.data

import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SettingsStore {
    private val file = File(System.getProperty("user.home"), ".proxytoolboxgui/settings.properties")
    private val props = Properties()

    init {
        file.parentFile?.mkdirs()
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        file.outputStream().use { props.store(it, "ProxyToolBoxGUI Settings") }
    }

    actual suspend fun getString(key: String, default: String): String = withContext(Dispatchers.IO) {
        props.getProperty(key, default)
    }

    actual suspend fun putString(key: String, value: String) {
        props.setProperty(key, value)
        save()
    }

    actual suspend fun getInt(key: String, default: Int): Int = withContext(Dispatchers.IO) {
        props.getProperty(key)?.toIntOrNull() ?: default
    }

    actual suspend fun putInt(key: String, value: Int) {
        props.setProperty(key, value.toString())
        save()
    }

    actual suspend fun getBoolean(key: String, default: Boolean): Boolean = withContext(Dispatchers.IO) {
        props.getProperty(key)?.toBooleanStrictOrNull() ?: default
    }

    actual suspend fun putBoolean(key: String, value: Boolean) {
        props.setProperty(key, value.toString())
        save()
    }

    actual suspend fun getLong(key: String, default: Long): Long = withContext(Dispatchers.IO) {
        props.getProperty(key)?.toLongOrNull() ?: default
    }

    actual suspend fun putLong(key: String, value: Long) {
        props.setProperty(key, value.toString())
        save()
    }
}