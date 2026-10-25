package com.fetchly.app.engine

import java.util.concurrent.atomic.AtomicLong

/**
 * One byte range of a file, covering [start]..[end] inclusive.
 *
 * [downloaded] counts bytes already written for this range. It is atomic because
 * the range's worker coroutine updates it while progress reporting reads it.
 * Persisting [downloaded] is what lets a download resume after a restart.
 */
class Segment(
    val start: Long,
    val end: Long,
    initialDownloaded: Long = 0L,
) {
    val downloaded = AtomicLong(initialDownloaded)

    val length: Long get() = end - start + 1
    val isComplete: Boolean get() = downloaded.get() >= length

    /** Absolute file offset of the next byte still needed. */
    val nextOffset: Long get() = start + downloaded.get()

    override fun toString(): String = "Segment($start..$end, downloaded=${downloaded.get()})"
}

/**
 * Splits [totalBytes] into at most [connections] contiguous ranges of nearly equal size.
 * Never produces an empty range, so for a 3-byte file with 8 connections you get 3 segments.
 */
fun planSegments(totalBytes: Long, connections: Int): List<Segment> {
    require(totalBytes > 0) { "totalBytes must be positive, was $totalBytes" }
    require(connections >= 1) { "connections must be at least 1, was $connections" }

    val count = minOf(connections.toLong(), totalBytes).toInt()
    val baseSize = totalBytes / count
    val remainder = totalBytes % count

    var start = 0L
    return List(count) { index ->
        // The first `remainder` segments get one extra byte so sizes differ by at most 1.
        val size = baseSize + if (index < remainder) 1 else 0
        Segment(start = start, end = start + size - 1).also { start += size }
    }
}
