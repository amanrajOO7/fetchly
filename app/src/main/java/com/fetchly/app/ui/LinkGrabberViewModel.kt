package com.fetchly.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fetchly.app.FetchlyApp
import com.fetchly.app.browser.FoundLink
import com.fetchly.app.browser.LinkScanner
import com.fetchly.app.download.DownloadService
import kotlinx.coroutines.launch

sealed interface ScanState {
    data object Idle : ScanState
    data object Loading : ScanState
    data class Found(val links: List<FoundLink>) : ScanState
    data class Failed(val message: String) : ScanState
}

class LinkGrabberViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as FetchlyApp).container
    private val scanner = LinkScanner(container.client)

    var state: ScanState by mutableStateOf(ScanState.Idle)
        private set

    fun scan(pageUrl: String) {
        val trimmed = pageUrl.trim()
        if (trimmed.isEmpty()) return
        state = ScanState.Loading
        viewModelScope.launch {
            state = try {
                val links = scanner.scan(trimmed)
                if (links.isEmpty()) {
                    ScanState.Failed("No downloadable files found on that page.")
                } else {
                    ScanState.Found(links)
                }
            } catch (e: Exception) {
                ScanState.Failed(e.message ?: "Couldn't read that page.")
            }
        }
    }

    fun download(link: FoundLink) {
        viewModelScope.launch {
            container.downloads.add(url = link.url, suggestedFileName = link.fileName)
            DownloadService.start(getApplication())
        }
    }

    fun downloadAll(links: List<FoundLink>) {
        viewModelScope.launch {
            links.forEach { container.downloads.add(url = it.url, suggestedFileName = it.fileName) }
            DownloadService.start(getApplication())
        }
    }
}
