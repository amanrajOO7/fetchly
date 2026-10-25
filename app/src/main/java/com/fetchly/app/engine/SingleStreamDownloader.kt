package com.fetchly.app.engine

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * Fallback downloader for servers that don't support byte ranges.
 * It cannot resume: a restart downloads the file from the beginning.
 */
class SingleStreamDownloader(private val client: OkHttpClient) {

    /**
     * Streams [url] into [target], overwriting any existing file.
     * [onProgress] receives bytes written so far and the total size if known.
     * Cancelling the calling coroutine cancels the HTTP call too.
     */
    suspend fun download(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(url).get().build())
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }

        call.execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body ?: throw IOException("Empty response body")
            val total = body.contentLength().takeIf { it >= 0 }

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    onProgress(downloaded, total)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
