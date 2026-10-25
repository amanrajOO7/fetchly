package com.fetchly.app.engine

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Thrown when the server answers a range request with the full file (HTTP 200). Retrying won't help. */
class RangeNotSupportedException : IOException("Server ignored the Range request")

/**
 * Downloads a file as parallel byte ranges written into one preallocated target file.
 *
 * Each [Segment] runs in its own coroutine and writes at its own offset, so no merge step is needed.
 * Segments that are already complete are skipped, and partial ones resume from [Segment.nextOffset].
 */
class SegmentedDownloader(private val client: OkHttpClient) {

    /**
     * @param segments ranges to download, usually from [planSegments] or restored from storage.
     * @param onProgress called with the total bytes downloaded across all segments. It may be
     *   called from several threads at once, so the caller must make its own state thread-safe.
     * @throws IOException if a segment still fails after [MAX_RETRIES] attempts.
     */
    suspend fun download(
        url: String,
        target: File,
        totalBytes: Long,
        segments: List<Segment>,
        onProgress: (downloadedBytes: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // Preallocate so every segment can write at its offset. setLength never truncates
        // when the size already matches, so resuming into an existing file is safe.
        RandomAccessFile(target, "rw").use { it.setLength(totalBytes) }

        val reportProgress = { onProgress(segments.sumOf { it.downloaded.get() }) }

        coroutineScope {
            segments
                .filterNot { it.isComplete }
                .map { segment ->
                    async { downloadWithRetry(url, target, segment, reportProgress) }
                }
                .awaitAll()
        }
    }

    private suspend fun downloadWithRetry(
        url: String,
        target: File,
        segment: Segment,
        onBytes: () -> Unit,
    ) {
        var attempt = 0
        while (true) {
            try {
                downloadSegment(url, target, segment, onBytes)
                return
            } catch (e: IOException) {
                if (!isRetryable(e)) throw e
                attempt++
                if (attempt > MAX_RETRIES) throw e
                delay(backoffMillis(attempt))
            }
        }
    }

    private suspend fun downloadSegment(
        url: String,
        target: File,
        segment: Segment,
        onBytes: () -> Unit,
    ) {
        val from = segment.nextOffset
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$from-${segment.end}")
            .get()
            .build()

        val call = client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }

        call.execute().use { response ->
            when (response.code) {
                206 -> Unit
                200 -> throw RangeNotSupportedException()
                else -> throw HttpStatusException(response.code)
            }
            val body = response.body ?: throw IOException("Empty response body")

            body.byteStream().use { input ->
                RandomAccessFile(target, "rw").use { file ->
                    file.seek(from)
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        file.write(buffer, 0, read)
                        segment.downloaded.addAndGet(read.toLong())
                        onBytes()
                    }
                }
            }
        }

        // The server may close the connection early without an error. Treat that as a failure so it gets retried.
        if (!segment.isComplete) throw IOException("Connection closed before segment finished: $segment")
    }

    private fun isRetryable(e: IOException): Boolean = when (e) {
        is RangeNotSupportedException -> false
        is HttpStatusException -> e.code !in 400..499 // 4xx means our request is wrong; retrying won't fix it.
        else -> true
    }

    private fun backoffMillis(attempt: Int): Long = 1_000L shl (attempt - 1).coerceAtMost(4)

    companion object {
        const val MAX_RETRIES = 5
        private const val BUFFER_SIZE = 64 * 1024
    }
}
