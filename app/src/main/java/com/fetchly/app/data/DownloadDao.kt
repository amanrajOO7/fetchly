package com.fetchly.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert
    suspend fun insertDownload(download: DownloadEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownload(id: Long): DownloadEntity?

    /** Downloads that were in progress or queued when the app last stopped. */
    @Query("SELECT * FROM downloads WHERE status IN (:statuses)")
    suspend fun getDownloadsWithStatus(statuses: List<DownloadStatus>): List<DownloadEntity>

    @Query("SELECT * FROM segments WHERE downloadId = :downloadId ORDER BY segmentIndex")
    suspend fun getSegments(downloadId: Long): List<SegmentEntity>

    @Query(
        "UPDATE segments SET downloaded = :downloaded " +
            "WHERE downloadId = :downloadId AND segmentIndex = :segmentIndex",
    )
    suspend fun updateSegmentProgress(downloadId: Long, segmentIndex: Int, downloaded: Long)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, error: String?, now: Long)

    @Query("UPDATE downloads SET downloadedBytes = :bytes, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, now: Long)

    @Query("DELETE FROM segments WHERE downloadId = :downloadId")
    suspend fun deleteSegments(downloadId: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)
}
