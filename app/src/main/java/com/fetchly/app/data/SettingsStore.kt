package com.fetchly.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App settings, backed by SharedPreferences so they survive restarts. Each value is also kept
 * in a StateFlow so the UI and DownloadManager can react to changes immediately, without
 * re-reading SharedPreferences on every use.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _maxConcurrentDownloads = MutableStateFlow(
        prefs.getInt(KEY_MAX_CONCURRENT, DEFAULT_MAX_CONCURRENT),
    )
    val maxConcurrentDownloads: StateFlow<Int> = _maxConcurrentDownloads.asStateFlow()

    private val _connectionsPerDownload = MutableStateFlow(
        prefs.getInt(KEY_CONNECTIONS, DEFAULT_CONNECTIONS),
    )
    val connectionsPerDownload: StateFlow<Int> = _connectionsPerDownload.asStateFlow()

    fun setMaxConcurrentDownloads(value: Int) {
        val clamped = value.coerceIn(MIN_CONCURRENT, MAX_CONCURRENT)
        prefs.edit().putInt(KEY_MAX_CONCURRENT, clamped).apply()
        _maxConcurrentDownloads.value = clamped
    }

    fun setConnectionsPerDownload(value: Int) {
        val clamped = value.coerceIn(MIN_CONNECTIONS, MAX_CONNECTIONS)
        prefs.edit().putInt(KEY_CONNECTIONS, clamped).apply()
        _connectionsPerDownload.value = clamped
    }

    companion object {
        private const val PREFS_NAME = "fetchly_settings"
        private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
        private const val KEY_CONNECTIONS = "connections_per_download"

        const val DEFAULT_MAX_CONCURRENT = 3
        const val MIN_CONCURRENT = 1
        const val MAX_CONCURRENT = 5

        const val DEFAULT_CONNECTIONS = 4
        const val MIN_CONNECTIONS = 1
        const val MAX_CONNECTIONS = 8
    }
}
