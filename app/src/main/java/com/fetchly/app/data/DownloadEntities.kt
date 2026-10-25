package com.fetchly.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
}

/** One row per download. Byte counts and metadata are saved so a download can resume after a restart. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    /** URL after redirects; null until the probe has run. */
    val finalUrl: String?,
    val fileName: String,
    /** Final file location. Partial data lives at this path plus ".part". */
    val destinationPath: String,
    val status: DownloadStatus,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val acceptsRanges: Boolean,
    /** Validators to check the file hasn't changed before resuming. */
    val etag: String?,
    val lastModified: String?,
    val connections: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Progress for one byte range of a segmented download. Rows are removed when their download is deleted. */
@Entity(
    tableName = "segments",
    primaryKeys = ["downloadId", "segmentIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("downloadId")],
)
data class SegmentEntity(
    val downloadId: Long,
    val segmentIndex: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long,
)
