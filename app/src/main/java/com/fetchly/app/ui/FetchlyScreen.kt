package com.fetchly.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.data.DownloadEntity
import com.fetchly.app.data.DownloadStatus
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The filter tabs across the top of the list. Each tab says which statuses it shows. */
private enum class DownloadTab(val label: String, val matches: (DownloadStatus) -> Boolean) {
    ALL("All", { true }),
    RUNNING("Running", { it == DownloadStatus.DOWNLOADING || it == DownloadStatus.QUEUED }),
    COMPLETED("Completed", { it == DownloadStatus.COMPLETED }),
    ERROR("Error", { it == DownloadStatus.FAILED }),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FetchlyScreen(viewModel: DownloadsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    RequestNotificationPermission()

    val tabs = DownloadTab.entries
    val counts = tabs.map { tab -> state.downloads.count { tab.matches(it.status) } }
    val visible = state.downloads.filter { tabs[selectedTab].matches(it.status) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Fetchly") })
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text("${tab.label} (${counts[index]})") },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddSheet = true }) {
                Text("New download")
            }
        },
    ) { padding ->
        if (visible.isEmpty()) {
            EmptyState(
                message = emptyMessage(tabs[selectedTab]),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    // Leaves room so the last card isn't hidden behind the New download button.
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.id }) { download ->
                    DownloadCard(
                        download = download,
                        speed = state.speeds[download.id] ?: 0L,
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
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
private fun DownloadCard(
    download: DownloadEntity,
    speed: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val total = download.totalBytes
    val fraction = if (total != null && total > 0) {
        (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
    } else {
        null
    }
    val isTransferring = download.status == DownloadStatus.DOWNLOADING

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hostOf(download),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                StatusBadge(download.status)
            }

            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (isTransferring) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val done = Formatter.formatShortFileSize(context, download.downloadedBytes)
                val sizeLine = if (total != null) {
                    "$done of ${Formatter.formatShortFileSize(context, total)}"
                } else {
                    done
                }
                Text(
                    text = sizeLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isTransferring) {
                    Text(
                        text = "${Formatter.formatShortFileSize(context, speed)}/s",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (download.status == DownloadStatus.FAILED) {
                download.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (download.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING ->
                        FilledTonalButton(onClick = onPause) { Text("Pause") }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                        FilledTonalButton(onClick = onResume) { Text("Resume") }
                    DownloadStatus.COMPLETED -> Unit
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        DownloadStatus.DOWNLOADING -> scheme.primaryContainer to scheme.onPrimaryContainer
        DownloadStatus.QUEUED -> scheme.secondaryContainer to scheme.onSecondaryContainer
        DownloadStatus.PAUSED -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        DownloadStatus.COMPLETED -> scheme.primary to scheme.onPrimary
        DownloadStatus.FAILED -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUrlSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val trimmed = url.trim()
    val isValid = trimmed.toHttpUrlOrNull() != null
    val showError = trimmed.isNotEmpty() && !isValid

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "New download",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Paste a direct link to a file. Large files download over several " +
                    "connections when the server supports it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link") },
                singleLine = true,
                isError = showError,
                supportingText = {
                    Text(
                        if (showError) {
                            "Enter a valid http or https link"
                        } else {
                            "Example: https://example.com/file.zip"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onAdd(trimmed) },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start download")
            }
        }
    }
}

private fun emptyMessage(tab: DownloadTab): String = when (tab) {
    DownloadTab.ALL -> "No downloads yet. Tap New download to start."
    DownloadTab.RUNNING -> "Nothing is downloading right now."
    DownloadTab.COMPLETED -> "Finished downloads appear here."
    DownloadTab.ERROR -> "No failed downloads."
}

private fun hostOf(download: DownloadEntity): String =
    (download.finalUrl ?: download.url).toHttpUrlOrNull()?.host ?: download.url

/** Android 13+ needs this permission before the transfer notification can show. Asked once, at launch. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
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
