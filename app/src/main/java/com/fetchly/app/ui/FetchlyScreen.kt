package com.fetchly.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fetchly.app.data.DownloadEntity
import com.fetchly.app.data.DownloadStatus
import com.fetchly.app.data.FileCategory
import com.fetchly.app.data.SettingsStore
import com.fetchly.app.data.categoryOf
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The filter tabs under the app bar. Each tab says which statuses it shows. */
private enum class DownloadTab(val label: String, val matches: (DownloadStatus) -> Boolean) {
    ALL("All", { true }),
    RUNNING("Running", { it == DownloadStatus.DOWNLOADING || it == DownloadStatus.QUEUED }),
    COMPLETED("Completed", { it == DownloadStatus.COMPLETED }),
    ERROR("Error", { it == DownloadStatus.FAILED }),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FetchlyScreen(
    viewModel: DownloadsViewModel = viewModel(),
    onOpenLinkGrabber: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedCategoryOrdinal by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    RequestNotificationPermission()

    val tabs = DownloadTab.entries
    val selectedCategory = FileCategory.entries[selectedCategoryOrdinal]
    val counts = tabs.map { tab -> state.downloads.count { tab.matches(it.status) } }
    val categoryCounts = FileCategory.entries.associateWith { category ->
        if (category == FileCategory.EVERYTHING) {
            state.downloads.size
        } else {
            state.downloads.count { categoryOf(it.fileName) == category }
        }
    }
    val visible = state.downloads.filter { download ->
        tabs[selectedTab].matches(download.status) &&
            (selectedCategory == FileCategory.EVERYTHING || categoryOf(download.fileName) == selectedCategory) &&
            (query.isBlank() || download.fileName.contains(query.trim(), ignoreCase = true))
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    categoryCounts = categoryCounts,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { category ->
                        selectedCategoryOrdinal = category.ordinal
                        coroutineScope.launch { drawerState.close() }
                    },
                    onOpenLinkGrabber = {
                        coroutineScope.launch { drawerState.close() }
                        onOpenLinkGrabber()
                    },
                    downloadsPath = viewModel.downloadsPath,
                    maxConcurrent = viewModel.maxConcurrentDownloads.collectAsState().value,
                    onMaxConcurrentChange = viewModel::setMaxConcurrentDownloads,
                    connectionsPerDownload = viewModel.connectionsPerDownload.collectAsState().value,
                    onConnectionsChange = viewModel::setConnectionsPerDownload,
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        },
                        title = {
                            Text(
                                text = "Fetchly",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    searching = !searching
                                    if (!searching) query = ""
                                },
                            ) {
                                Icon(
                                    imageVector = if (searching) Icons.Filled.Close else Icons.Filled.Search,
                                    contentDescription = if (searching) "Close search" else "Search",
                                )
                            }
                        },
                    )
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("Search downloads") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
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
                FloatingActionButton(onClick = { showAddSheet = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New download")
                }
            },
        ) { padding ->
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
                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            message = emptyMessage(
                                tab = tabs[selectedTab],
                                category = selectedCategory,
                                isSearch = query.isNotBlank(),
                            ),
                        )
                    }
                } else {
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
}

@Composable
private fun DrawerContent(
    categoryCounts: Map<FileCategory, Int>,
    selectedCategory: FileCategory,
    onSelectCategory: (FileCategory) -> Unit,
    onOpenLinkGrabber: () -> Unit,
    downloadsPath: String,
    maxConcurrent: Int,
    onMaxConcurrentChange: (Int) -> Unit,
    connectionsPerDownload: Int,
    onConnectionsChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text = "Fetchly", style = MaterialTheme.typography.headlineSmall)
        }
        HorizontalDivider()

        NavigationDrawerItem(
            label = { Text("Link grabber") },
            icon = { Icon(Icons.Filled.Link, contentDescription = null) },
            selected = false,
            onClick = onOpenLinkGrabber,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        HorizontalDivider()

        DrawerSectionLabel("Types")
        FileCategory.entries.forEach { category ->
            NavigationDrawerItem(
                label = { Text(categoryLabel(category, categoryCounts[category] ?: 0)) },
                icon = { Icon(categoryIcon(category), contentDescription = null) },
                selected = category == selectedCategory,
                onClick = { onSelectCategory(category) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        DrawerSectionLabel("Settings")

        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "Download location",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = downloadsPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SettingSlider(
            label = "Max concurrent downloads",
            value = maxConcurrent,
            onValueChange = onMaxConcurrentChange,
            range = SettingsStore.MIN_CONCURRENT..SettingsStore.MAX_CONCURRENT,
        )
        SettingSlider(
            label = "Connections per download",
            value = connectionsPerDownload,
            onValueChange = onConnectionsChange,
            range = SettingsStore.MIN_CONNECTIONS..SettingsStore.MAX_CONNECTIONS,
        )
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

private fun categoryLabel(category: FileCategory, count: Int): String =
    if (count > 0) "${category.label} ($count)" else category.label

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
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
private fun DownloadCard(
    download: DownloadEntity,
    speed: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val total = download.totalBytes
    val downloaded = download.downloadedBytes
    val fraction = if (total != null && total > 0) {
        (downloaded.toFloat() / total).coerceIn(0f, 1f)
    } else {
        null
    }
    val percent = fraction?.let { (it * 100).roundToInt() }
    val isTransferring = download.status == DownloadStatus.DOWNLOADING
    val eta = if (isTransferring && speed > 0 && total != null && total > downloaded) {
        formatEta(bytesLeft = total - downloaded, speed = speed)
    } else {
        null
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusIcon(download.status)
                Spacer(Modifier.width(12.dp))
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
                val done = Formatter.formatShortFileSize(context, downloaded)
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

            if (percent != null || eta != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = percent?.let { "$it%" } ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (eta != null) {
                        Text(
                            text = eta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                        FilledTonalButton(onClick = onPause) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pause")
                        }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                        FilledTonalButton(onClick = onResume) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Resume")
                        }
                    DownloadStatus.COMPLETED -> Unit
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: DownloadStatus) {
    val (container, tint) = statusColors(status)
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = container,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = statusIcon(status),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus) {
    val (container, content) = statusColors(status)
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

@Composable
private fun statusColors(status: DownloadStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        DownloadStatus.DOWNLOADING -> scheme.primaryContainer to scheme.onPrimaryContainer
        DownloadStatus.QUEUED -> scheme.secondaryContainer to scheme.onSecondaryContainer
        DownloadStatus.PAUSED -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        DownloadStatus.COMPLETED -> scheme.primary to scheme.onPrimary
        DownloadStatus.FAILED -> scheme.errorContainer to scheme.onErrorContainer
    }
}

private fun statusIcon(status: DownloadStatus): ImageVector = when (status) {
    DownloadStatus.DOWNLOADING -> Icons.Filled.Download
    DownloadStatus.QUEUED -> Icons.Filled.Schedule
    DownloadStatus.PAUSED -> Icons.Filled.Pause
    DownloadStatus.COMPLETED -> Icons.Filled.Done
    DownloadStatus.FAILED -> Icons.Filled.Warning
}

private fun emptyMessage(tab: DownloadTab, category: FileCategory, isSearch: Boolean): String = when {
    isSearch -> "No downloads match your search."
    category != FileCategory.EVERYTHING -> "No ${category.label.lowercase()} here yet."
    else -> when (tab) {
        DownloadTab.ALL -> "No downloads yet. Tap + to add one."
        DownloadTab.RUNNING -> "Nothing is downloading right now."
        DownloadTab.COMPLETED -> "Finished downloads appear here."
        DownloadTab.ERROR -> "No failed downloads."
    }
}

/** Turns bytes left and bytes per second into text such as "3m 20s left". */
private fun formatEta(bytesLeft: Long, speed: Long): String {
    val seconds = bytesLeft / speed
    return when {
        seconds >= 3_600 -> "${seconds / 3_600}h ${(seconds % 3_600) / 60}m left"
        seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds}s left"
    }
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
