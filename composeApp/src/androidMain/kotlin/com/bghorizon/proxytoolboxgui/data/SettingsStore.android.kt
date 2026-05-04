package com.bghorizon.proxytoolboxgui.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SettingsStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("proxytoolbox_settings", Context.MODE_PRIVATE)
    }

    actual suspend fun getString(key: String, default: String): String = withContext(Dispatchers.IO) {
        prefs.getString(key, default) ?: default
    }

    actual suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    actual suspend fun getInt(key: String, default: Int): Int = withContext(Dispatchers.IO) {
        prefs.getInt(key, default)
    }

    actual suspend fun putInt(key: String, value: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(key, value).apply()
    }

    actual suspend fun getBoolean(key: String, default: Boolean): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(key, default)
    }

    actual suspend fun putBoolean(key: String, value: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(key, value).apply()
    }

    actual suspend fun getLong(key: String, default: Long): Long = withContext(Dispatchers.IO) {
        prefs.getLong(key, default)
    }

    actual suspend fun putLong(key: String, value: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(key, value).apply()
    }
}