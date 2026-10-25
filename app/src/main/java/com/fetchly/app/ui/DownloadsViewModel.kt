package com.fetchly.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.FetchlyApp
import com.fetchly.app.data.DownloadEntity
import com.fetchly.app.data.DownloadStatus
import com.fetchly.app.download.DownloadService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsState(
    val downloads: List<DownloadEntity> = emptyList(),
    /** Current speed in bytes per second, for each download that is transferring. */
    val speeds: Map<Long, Long> = emptyMap(),
)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as FetchlyApp).container

    /** Last byte count seen for each transferring download, used to work out its speed. */
    private val samples = HashMap<Long, Sample>()

    val state: StateFlow<DownloadsState> = container.downloads.observeDownloads()
        .map { list -> DownloadsState(downloads = list, speeds = measureSpeeds(list)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsState())

    fun addDownload(url: String) {
        viewModelScope.launch {
            container.downloads.add(url)
            DownloadService.start(getApplication())
        }
    }

    fun pause(id: Long) {
        viewModelScope.launch { container.downloads.pause(id) }
    }

    fun resume(id: Long) {
        viewModelScope.launch {
            container.downloads.resume(id)
            DownloadService.start(getApplication())
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { container.downloads.delete(id) }
    }

    /**
     * Works out speed as bytes gained per second. The database updates about once a second,
     * so the sample is taken at most once per second, which avoids noisy readings from
     * back-to-back updates.
     */
    private fun measureSpeeds(list: List<DownloadEntity>): Map<Long, Long> {
        val now = System.currentTimeMillis()
        val result = HashMap<Long, Long>()
        for (download in list) {
            if (download.status != DownloadStatus.DOWNLOADING) {
                samples.remove(download.id)
                continue
            }
            val previous = samples[download.id]
            if (previous == null) {
                samples[download.id] = Sample(download.downloadedBytes, now, 0L)
                result[download.id] = 0L
                continue
            }
            val elapsed = now - previous.time
            when {
                // Too long since the last sample (e.g. the screen was off), so start a fresh one.
                elapsed > STALE_MS -> {
                    samples[download.id] = Sample(download.downloadedBytes, now, 0L)
                    result[download.id] = 0L
                }
                elapsed >= SAMPLE_MS -> {
                    val speed = ((download.downloadedBytes - previous.bytes) * 1000 / elapsed).coerceAtLeast(0L)
                    samples[download.id] = Sample(download.downloadedBytes, now, speed)
                    result[download.id] = speed
                }
                else -> result[download.id] = previous.speed
            }
        }
        return result
    }

    private class Sample(val bytes: Long, val time: Long, val speed: Long)

    private companion object {
        const val SAMPLE_MS = 1_000L
        const val STALE_MS = 10_000L
    }
}
