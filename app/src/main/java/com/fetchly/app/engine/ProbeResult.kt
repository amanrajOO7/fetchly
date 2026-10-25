package com.fetchly.app.engine

/**
 * What the server told us about a download URL before we start transferring.
 *
 * @property finalUrl URL after following redirects; use this for all segment requests.
 * @property contentLength total size in bytes, or null if the server didn't report it.
 * @property acceptsRanges true if the server honored a byte-range request, so segmented download is possible.
 */
data class ProbeResult(
    val finalUrl: String,
    val contentLength: Long?,
    val acceptsRanges: Boolean,
    val etag: String?,
    val lastModified: String?,
)
