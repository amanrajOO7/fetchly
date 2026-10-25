package com.fetchly.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Inspects a URL before downloading.
 *
 * Uses a `Range: bytes=0-0` GET instead of HEAD. Many servers answer HEAD
 * inaccurately or not at all, while a one-byte range request reveals both
 * range support and the total size in a single round trip:
 * - 206 Partial Content: ranges supported; total size is in `Content-Range`.
 * - 200 OK: ranges ignored; size comes from `Content-Length` if present.
 */
object HttpProbe {

    suspend fun probe(client: OkHttpClient, url: String): ProbeResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val etag = response.header("ETag")
            val lastModified = response.header("Last-Modified")
            val finalUrl = response.request.url.toString()

            when (response.code) {
                206 -> ProbeResult(
                    finalUrl = finalUrl,
                    contentLength = parseTotalLength(response.header("Content-Range")),
                    acceptsRanges = true,
                    etag = etag,
                    lastModified = lastModified,
                )
                200 -> ProbeResult(
                    finalUrl = finalUrl,
                    contentLength = response.header("Content-Length")?.toLongOrNull(),
                    acceptsRanges = false,
                    etag = etag,
                    lastModified = lastModified,
                )
                else -> throw HttpStatusException(response.code)
            }
        }
    }

    /** Parses the total length from `Content-Range: bytes 0-0/12345`. Returns null for `*` or malformed values. */
    internal fun parseTotalLength(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/', missingDelimiterValue = "")?.toLongOrNull()
}

class HttpStatusException(val code: Int) : java.io.IOException("HTTP $code")
