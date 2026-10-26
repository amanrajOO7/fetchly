package com.fetchly.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.vector.ImageVector
import com.fetchly.app.data.FileCategory

/** Shared between the downloads list and the link grabber, so a file type looks the same everywhere. */
fun categoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.EVERYTHING -> Icons.Filled.Apps
    FileCategory.COMPRESSED -> Icons.Filled.Archive
    FileCategory.DOCUMENTS -> Icons.Filled.Description
    FileCategory.MUSIC -> Icons.Filled.MusicNote
    FileCategory.VIDEOS -> Icons.Filled.Movie
    FileCategory.SUBTITLES -> Icons.Filled.ClosedCaption
    FileCategory.PHOTOS -> Icons.Filled.Image
    FileCategory.PROGRAMS -> Icons.Filled.Android
    FileCategory.OTHERS -> Icons.Filled.HelpOutline
}
