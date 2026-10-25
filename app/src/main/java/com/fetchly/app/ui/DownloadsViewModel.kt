package com.fetchly.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.FetchlyApp
import com.fetchly.app.data.DownloadEntity
import com.fetchly.app.download.DownloadService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as FetchlyApp).container

    val downloads: StateFlow<List<DownloadEntity>> = container.downloads.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
}
