package com.fetchly.app.engine

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Runs one complete download: probes the URL, picks segmented or single-stream
 * transfer, writes to `<name>.part`, verifies the size, and renames the file into place.
 */
class DownloadOrchestrator(client: OkHttpClient) {

    private val segmented = SegmentedDownloader(client)
    private val singleStream = SingleStreamDownloader(client)
    private val client = client

    /**
     * @param destination final file location. Its partial data lives at `destination.path + ".part"`
     *   until the download completes.
     * @param onProgress called with (downloaded bytes, total bytes or null if unknown).
     * @return the completed file.
     * @throws IOException on HTTP errors, a size mismatch, or a failed rename.
     */
    suspend fun download(
        url: String,
        destination: File,
        connections: Int = DEFAULT_CONNECTIONS,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val partFile = File(destination.path + PART_SUFFIX)
        val probe = HttpProbe.probe(client, url)
        val total = probe.contentLength

        if (probe.acceptsRanges && total != null && total > 0) {
            segmented.download(
                url = probe.finalUrl,
                target = partFile,
                totalBytes = total,
                segments = planSegments(total, connections),
            ) { downloaded -> onProgress(downloaded, total) }
        } else {
            singleStream.download(probe.finalUrl, partFile, onProgress)
        }

        val actual = partFile.length()
        if (total != null && actual != total) {
            throw IOException("Size mismatch: expected $total bytes, got $actual")
        }

        if (destination.exists() && !destination.delete()) {
            throw IOException("Could not replace existing file: ${destination.path}")
        }
        if (!partFile.renameTo(destination)) {
            throw IOException("Could not move download into place: ${destination.path}")
        }
        destination
    }

    companion object {
        const val DEFAULT_CONNECTIONS = 4
        const val PART_SUFFIX = ".part"
    }
}
