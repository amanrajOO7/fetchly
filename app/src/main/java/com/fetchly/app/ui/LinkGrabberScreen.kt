package com.fetchly.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.browser.FoundLink
import com.fetchly.app.data.categoryOf

/**
 * Finds downloadable files on a page without loading it as a browser would: one request for the
 * page's HTML, no scripts, no images, no stylesheets, and no cookies kept between requests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkGrabberScreen(
    onBack: () -> Unit,
    viewModel: LinkGrabberViewModel = viewModel(),
) {
    var pageUrl by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val state = viewModel.state

    fun runScan() {
        focusManager.clearFocus()
        viewModel.scan(pageUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Link grabber") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = pageUrl,
                    onValueChange = { pageUrl = it },
                    singleLine = true,
                    label = { Text("Page URL") },
                    placeholder = { Text("example.com/downloads") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runScan() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { runScan() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Scan page")
                }
            }

            Text(
                text = "Reads only the page's HTML to list its file links. Nothing is rendered, " +
                    "no scripts run, and no cookies are kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            when (state) {
                is ScanState.Idle -> Unit
                is ScanState.Loading -> LoadingState()
                is ScanState.Failed -> MessageState(state.message)
                is ScanState.Found -> ResultsList(
                    links = state.links,
                    onDownload = viewModel::download,
                    onDownloadAll = { viewModel.downloadAll(state.links) },
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageState(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultsList(
    links: List<FoundLink>,
    onDownload: (FoundLink) -> Unit,
    onDownloadAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${links.size} file${if (links.size == 1) "" else "s"} found",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onDownloadAll) { Text("Download all") }
            }
        }
        items(links, key = { it.url }) { link ->
            LinkRow(link = link, onDownload = { onDownload(link) })
        }
    }
}

@Composable
private fun LinkRow(link: FoundLink, onDownload: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = categoryIcon(categoryOf(link.fileName)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = link.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
