package com.fetchly.app.download

import com.fetchly.app.data.DownloadDao
import com.fetchly.app.data.DownloadEntity
import com.fetchly.app.data.DownloadStatus
import com.fetchly.app.data.SegmentEntity
import com.fetchly.app.engine.HttpProbe
import com.fetchly.app.engine.ProbeResult
import com.fetchly.app.engine.Segment
import com.fetchly.app.engine.SegmentedDownloader
import com.fetchly.app.engine.SingleStreamDownloader
import com.fetchly.app.engine.planSegments
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Runs one download from its database row, saving progress as it goes so a later run
 * can resume from the saved offsets.
 *
 * Cancelling the coroutine pauses the download. Any other failure marks it FAILED.
 */
class DownloadRunner(
    private val dao: DownloadDao,
    private val client: OkHttpClient,
) {

    private val segmented = SegmentedDownloader(client)
    private val singleStream = SingleStreamDownloader(client)

    suspend fun run(id: Long) {
        withContext(Dispatchers.IO) {
            val current = dao.getDownload(id) ?: return@withContext
            try {
                dao.updateStatus(id, DownloadStatus.DOWNLOADING, null, now())

                val probe = HttpProbe.probe(client, current.url)
                val entity = reconcile(current, probe)
                val total = entity.totalBytes
                val part = File(entity.destinationPath + PART_SUFFIX)
                val dest = File(entity.destinationPath)

                if (probe.acceptsRanges && total != null && total > 0) {
                    downloadSegmented(id, probe.finalUrl, part, total, entity.connections)
                } else {
                    downloadSingle(id, probe.finalUrl, part)
                }

                val actual = part.length()
                if (total != null && actual != total) {
                    throw IOException("Size mismatch: expected $total bytes, got $actual")
                }
                if (dest.exists() && !dest.delete()) throw IOException("Could not replace ${dest.path}")
                if (!part.renameTo(dest)) throw IOException("Could not move download into place")

                dao.deleteSegments(id)
                dao.updateProgress(id, actual, now())
                dao.updateStatus(id, DownloadStatus.COMPLETED, null, now())
            } catch (e: CancellationException) {
                withContext(NonCancellable) { dao.updateStatus(id, DownloadStatus.PAUSED, null, now()) }
                throw e
            } catch (e: IOException) {
                // OkHttp reports a cancelled call as an IOException, so check whether we were paused.
                val status = if (coroutineContext.isActive) DownloadStatus.FAILED else DownloadStatus.PAUSED
                withContext(NonCancellable) {
                    dao.updateStatus(id, status, e.message ?: "Download failed", now())
                }
            }
        }
    }

    /**
     * Compares the server's current file with what was saved. If it changed or the partial file
     * is missing, drops the saved progress and starts over. Saves the probe results either way.
     */
    private suspend fun reconcile(entity: DownloadEntity, probe: ProbeResult): DownloadEntity {
        val part = File(entity.destinationPath + PART_SUFFIX)
        val canResume = entity.totalBytes != null &&
            entity.totalBytes == probe.contentLength &&
            entity.etag == probe.etag &&
            entity.lastModified == probe.lastModified &&
            part.exists()

        if (!canResume) {
            part.delete()
            dao.deleteSegments(entity.id)
        }

        val updated = entity.copy(
            finalUrl = probe.finalUrl,
            totalBytes = probe.contentLength,
            acceptsRanges = probe.acceptsRanges,
            etag = probe.etag,
            lastModified = probe.lastModified,
            downloadedBytes = if (canResume) entity.downloadedBytes else 0L,
            updatedAt = now(),
        )
        dao.updateDownload(updated)
        return updated
    }

    private suspend fun downloadSegmented(
        id: Long,
        url: String,
        part: File,
        total: Long,
        connections: Int,
    ) {
        val segments = loadOrPlanSegments(id, total, connections)
        coroutineScope {
            val saver = launch {
                while (true) {
                    delay(SAVE_INTERVAL_MS)
                    saveSegments(id, segments)
                }
            }
            try {
                segmented.download(url, part, total, segments) { _ -> }
            } finally {
                saver.cancel()
                withContext(NonCancellable) { saveSegments(id, segments) }
            }
        }
    }

    private suspend fun downloadSingle(id: Long, url: String, part: File) {
        // A single stream can't resume, so every attempt starts from zero.
        part.delete()
        dao.updateProgress(id, 0L, now())

        val downloaded = AtomicLong(0L)
        coroutineScope {
            val saver = launch {
                while (true) {
                    delay(SAVE_INTERVAL_MS)
                    dao.updateProgress(id, downloaded.get(), now())
                }
            }
            try {
                singleStream.download(url, part) { bytes, _ -> downloaded.set(bytes) }
            } finally {
                saver.cancel()
                withContext(NonCancellable) { dao.updateProgress(id, downloaded.get(), now()) }
            }
        }
    }

    /** Uses the saved segment rows if there are any, so an interrupted download keeps its offsets. */
    private suspend fun loadOrPlanSegments(id: Long, total: Long, connections: Int): List<Segment> {
        val saved = dao.getSegments(id)
        if (saved.isNotEmpty()) {
            return saved.map { Segment(it.start, it.end, it.downloaded) }
        }
        val planned = planSegments(total, connections)
        dao.insertSegments(
            planned.mapIndexed { index, segment ->
                SegmentEntity(id, index, segment.start, segment.end, segment.downloaded.get())
            },
        )
        return planned
    }

    private suspend fun saveSegments(id: Long, segments: List<Segment>) {
        segments.forEachIndexed { index, segment ->
            dao.updateSegmentProgress(id, index, segment.downloaded.get())
        }
        dao.updateProgress(id, segments.sumOf { it.downloaded.get() }, now())
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        const val PART_SUFFIX = ".part"
        private const val SAVE_INTERVAL_MS = 1_000L
    }
}
