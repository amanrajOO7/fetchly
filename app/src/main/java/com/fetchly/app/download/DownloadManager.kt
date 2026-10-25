package com.fetchly.app.download

import com.fetchly.app.data.DownloadDao
import com.fetchly.app.data.DownloadEntity
import com.fetchly.app.data.DownloadStatus
import com.fetchly.app.data.SettingsStore
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Owns the download queue. Schedules runs with a concurrency limit, and exposes add, pause,
 * resume and delete. Database rows are the source of truth; this class only holds the running jobs.
 */
class DownloadManager(
    private val dao: DownloadDao,
    private val runner: DownloadRunner,
    private val scope: CoroutineScope,
    private val downloadsDir: File,
    private val settings: SettingsStore,
) {

    /**
     * How many downloads are running right now. A plain counter rather than kotlinx.coroutines'
     * Semaphore, because its permit count can't be changed once created, and the concurrency
     * limit here can change live from Settings.
     */
    private val activeCount = AtomicInteger(0)
    private val jobs = ConcurrentHashMap<Long, Job>()

    fun observeDownloads(): Flow<List<DownloadEntity>> = dao.observeDownloads()

    /** Creates a queued download for [url] and starts it. Returns the new download's id. */
    suspend fun add(url: String): Long {
        val now = System.currentTimeMillis()
        val target = uniqueFile(fileNameFor(url))
        val id = dao.insertDownload(
            DownloadEntity(
                url = url,
                finalUrl = null,
                fileName = target.name,
                destinationPath = target.path,
                status = DownloadStatus.QUEUED,
                totalBytes = null,
                downloadedBytes = 0L,
                acceptsRanges = false,
                etag = null,
                lastModified = null,
                connections = settings.connectionsPerDownload.value,
                errorMessage = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        launch(id)
        return id
    }

    /** Stops a running or queued download. Its partial data is kept so it can be resumed. */
    suspend fun pause(id: Long) {
        jobs[id]?.cancelAndJoin()
        // The runner also marks the download paused, but a queued job that never started
        // doesn't reach the runner, so set the status here too.
        dao.updateStatus(id, DownloadStatus.PAUSED, null, System.currentTimeMillis())
    }

    suspend fun resume(id: Long) {
        dao.updateStatus(id, DownloadStatus.QUEUED, null, System.currentTimeMillis())
        launch(id)
    }

    suspend fun delete(id: Long) {
        jobs[id]?.cancelAndJoin()
        val entity = dao.getDownload(id) ?: return
        File(entity.destinationPath).delete()
        File(entity.destinationPath + DownloadRunner.PART_SUFFIX).delete()
        dao.deleteSegments(id)
        dao.deleteDownload(id)
    }

    /**
     * Called at app start. Nothing from the previous process is still running, so any download
     * that was active is marked paused. The user resumes it manually, so transfers don't start
     * in the background without them.
     */
    fun recoverAfterRestart() {
        scope.launch {
            dao.getDownloadsWithStatus(listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED))
                .forEach { dao.updateStatus(it.id, DownloadStatus.PAUSED, null, System.currentTimeMillis()) }
        }
    }

    private fun launch(id: Long) {
        if (jobs.containsKey(id)) return
        // Create the job lazily so it's in the map before it starts. Otherwise a fast job could
        // finish before the map entry is added, and the stale entry would block future runs.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            acquireSlot()
            try {
                runner.run(id)
            } finally {
                activeCount.decrementAndGet()
            }
        }
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id, job) }
        job.start()
    }

    /** Waits until fewer downloads are running than the current limit, then claims a slot. */
    private suspend fun acquireSlot() {
        while (true) {
            val limit = settings.maxConcurrentDownloads.value
            val claimed = activeCount.incrementAndGet()
            if (claimed <= limit) return
            // Someone else filled the last slot first, or the limit just dropped. Give it back
            // and wait before trying again.
            activeCount.decrementAndGet()
            delay(SLOT_POLL_MS)
        }
    }

    private fun uniqueFile(name: String): File {
        downloadsDir.mkdirs()
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var candidate = File(downloadsDir, name)
        var n = 1
        while (candidate.exists() || File(candidate.path + DownloadRunner.PART_SUFFIX).exists()) {
            val suffixed = if (ext.isEmpty()) "$name ($n)" else "$base ($n).$ext"
            candidate = File(downloadsDir, suffixed)
            n++
        }
        return candidate
    }

    private fun fileNameFor(url: String): String {
        val segment = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
        return (segment ?: "download").replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    companion object {
        private const val SLOT_POLL_MS = 300L
    }
}
