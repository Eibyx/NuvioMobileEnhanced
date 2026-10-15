package com.nuvio.app.features.player

import android.content.Context
import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest

/**
 * Avix: subtitle fidelity helpers for the bundled libmpv.
 *
 * The bundled libmpv (io.github.abdallahmehiz:mpv-android-lib 0.1.12) is built with
 * `-Diconv=disabled` and without uchardet, so `sub-codepage` cannot convert anything: an external
 * subtitle that is not UTF-8 (UTF-16 ASS from Aegisub, Windows-1252/Shift-JIS SRT…) reaches libass
 * as mojibake or fails to parse. External subtitles are therefore re-encoded to UTF-8 before
 * `sub-add`. UTF-8 files are passed through untouched, so ASS content is never rewritten.
 */
internal object LibmpvSubtitleSupport {
    private const val REMOTE_CACHE_DIR = "libmpv-subtitles"
    private const val DOWNLOAD_TIMEOUT_MS = 15_000L
    private const val CACHE_MAX_AGE_MS = 2L * 24 * 60 * 60 * 1000

    /** Directory passed to mpv as `sub-fonts-dir`; holds fonts imported by the user. */
    fun fontsDirectory(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    /**
     * Returns the source mpv should load for [url]: the original URL when it is already UTF-8 (or
     * cannot be inspected), or a private UTF-8 copy when the subtitle used another encoding.
     */
    suspend fun prepareExternalSubtitle(
        context: Context,
        url: String,
        headers: Map<String, String>,
    ): String {
        if (url.isLocalSubtitleUri() || !url.startsWith("http", ignoreCase = true)) return url
        return try {
            val bytes = withContext(Dispatchers.IO) {
                withTimeout(DOWNLOAD_TIMEOUT_MS) {
                    httpRequestRaw(
                        method = "GET",
                        url = url,
                        headers = headers,
                        body = "",
                        followRedirects = true,
                        maxResponseBodyBytes = LOCAL_SUBTITLE_MAX_BYTES,
                        bodyBytes = null,
                    )
                }.takeIf { it.status in 200..299 }?.bodyBytes
            } ?: return url
            val normalized = normalizeLocalSubtitle(bytes, url.substringBefore('?').substringAfterLast('/'))
                .getOrNull() ?: return url
            if (normalized.sourceEncoding == LocalSubtitleEncoding.UTF8) return url
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, REMOTE_CACHE_DIR).apply { mkdirs() }
                val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
                dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
                File(dir, "${sha256(url).take(16)}.${normalized.format.extension}").apply {
                    writeText(normalized.text, Charsets.UTF_8)
                }
            }
            InAppLogger.info(
                "MPV/Android",
                "re-encoded external subtitle from ${normalized.sourceEncoding} to UTF-8 format=${normalized.format}",
            )
            file.absolutePath
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            InAppLogger.warn("MPV/Android", "external subtitle inspection failed; loading as-is: ${error.message}")
            url
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
