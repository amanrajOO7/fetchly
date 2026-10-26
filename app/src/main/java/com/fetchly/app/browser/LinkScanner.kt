package com.fetchly.app.browser

import java.io.IOException
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class FoundLink(val url: String, val fileName: String)

/**
 * Finds downloadable files referenced by a page, without rendering it: no JavaScript, no images,
 * no stylesheets, no cookies kept (the shared OkHttpClient has no CookieJar configured). Only the
 * page's own HTML is fetched, read up to [MAX_SCAN_BYTES], and scanned for href values whose file
 * extension isn't one normally used for navigation (html, css, js, and the like).
 */
class LinkScanner(private val client: OkHttpClient) {

    suspend fun scan(pageUrl: String): List<FoundLink> = withContext(Dispatchers.IO) {
        val url = normalize(pageUrl).toHttpUrlOrNull() ?: throw IOException("Not a valid URL")
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Server returned HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val contentType = response.header("Content-Type").orEmpty()
            val finalUrl = response.request.url

            if (!contentType.contains("html", ignoreCase = true)) {
                // The page itself is already a file, not a page to scan.
                val name = finalUrl.pathSegments.lastOrNull { it.isNotBlank() } ?: "download"
                return@withContext listOf(FoundLink(finalUrl.toString(), decodeName(name)))
            }

            // Cap how much HTML is read, so a mislabeled or huge response can't be pulled
            // entirely into memory.
            val source = body.source()
            source.request(MAX_SCAN_BYTES)
            val html = source.buffer.readUtf8(minOf(source.buffer.size, MAX_SCAN_BYTES))

            extractDownloadLinks(html, finalUrl)
        }
    }

    private fun extractDownloadLinks(html: String, base: HttpUrl): List<FoundLink> {
        val seen = LinkedHashSet<String>()
        val results = mutableListOf<FoundLink>()
        for (match in HREF_REGEX.findAll(html)) {
            val raw = match.groupValues[1].trim()
            if (raw.isEmpty() || BLOCKED_SCHEME_PREFIXES.any { raw.startsWith(it, ignoreCase = true) }) continue

            // HttpUrl only represents http/https URLs, so resolve() already returns null for
            // any other scheme (javascript:, data:, intent:, and so on); the prefix check above
            // is just a cheap early exit.
            val resolved = base.resolve(raw) ?: continue
            val fileName = resolved.pathSegments.lastOrNull { it.isNotBlank() } ?: continue
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty() || extension in NAVIGATION_EXTENSIONS) continue

            val urlString = resolved.toString()
            if (seen.add(urlString)) {
                results += FoundLink(urlString, decodeName(fileName))
            }
        }
        return results
    }

    private fun decodeName(name: String): String =
        runCatching { URLDecoder.decode(name, Charsets.UTF_8.name()) }.getOrDefault(name)

    private fun normalize(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    companion object {
        private const val MAX_SCAN_BYTES = 2_000_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11) Fetchly/1.0 (+link-scanner; no-js, no-cookies)"
        private val HREF_REGEX = Regex("""href\s*=\s*["']([^"'#]+)["']""", RegexOption.IGNORE_CASE)
        private val BLOCKED_SCHEME_PREFIXES = listOf(
            "javascript:", "mailto:", "tel:", "sms:", "data:", "intent:", "file:", "ftp:",
        )
        private val NAVIGATION_EXTENSIONS = setOf(
            "html", "htm", "php", "aspx", "jsp", "css", "js", "json", "xml", "asp",
        )
    }
}
