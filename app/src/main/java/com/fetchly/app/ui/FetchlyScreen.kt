package com.fetchly.app.ui

import android.Manifest
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.data.DownloadEntity
import com.fetchly.app.data.DownloadStatus
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FetchlyScreen(viewModel: DownloadsViewModel = viewModel()) {
    val downloads by viewModel.downloads.collectAsState()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    RequestNotificationPermission()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Fetchly") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddSheet = true }) {
                Text("Add download")
            }
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No downloads yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadCard(
                        download = download,
                        onPause = { viewModel.pause(download.id) },
                        onResume = { viewModel.resume(download.id) },
                        onDelete = { viewModel.delete(download.id) },
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddUrlSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { url ->
                viewModel.addDownload(url)
                showAddSheet = false
            },
        )
    }
}

@Composable
private fun DownloadCard(
    download: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val total = download.totalBytes

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = download.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val done = Formatter.formatShortFileSize(context, download.downloadedBytes)
            val sizeText = if (total != null) {
                "$done of ${Formatter.formatShortFileSize(context, total)}"
            } else {
                done
            }
            val errorText = if (download.status == DownloadStatus.FAILED) {
                download.errorMessage?.let { " · $it" } ?: ""
            } else {
                ""
            }
            Text(
                text = "${statusLabel(download.status)} · $sizeText$errorText",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (download.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (download.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING ->
                        TextButton(onClick = onPause) { Text("Pause") }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                        TextButton(onClick = onResume) { Text("Resume") }
                    DownloadStatus.COMPLETED -> Unit
                }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUrlSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val isValid = url.trim().toHttpUrlOrNull() != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New download", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                singleLine = true,
                isError = url.isNotBlank() && !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onAdd(url.trim()) },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Download")
            }
        }
    }
}

/** Android 13+ needs this permission before the transfer notification can show. Asked once, at launch. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.FAILED -> "Failed"
}
