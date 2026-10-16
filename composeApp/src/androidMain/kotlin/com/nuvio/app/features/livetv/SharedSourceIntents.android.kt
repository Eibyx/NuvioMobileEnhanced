package com.nuvio.app.features.livetv

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns

/**
 * Avix (Yesnt10 port): routes "Open with" / "Share" intents for M3U playlists, direct video
 * links and magnet links to [IncomingSharedSourceRepository]. App links (avixnuvio://,
 * stremio://) are not handled here. Returns true when the intent was consumed.
 */
internal fun handleSharedSourceIntent(intent: Intent, contentResolver: ContentResolver): Boolean {
    when (intent.action) {
        Intent.ACTION_SEND -> {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                if (IncomingSharedSourceRepository.submitText(text)) return true
            }
            val stream = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            return stream != null && submitPlaylistUri(stream, contentResolver)
        }
        Intent.ACTION_VIEW -> {
            val data = intent.data ?: return false
            val mime = intent.type?.lowercase() ?: contentResolver.getType(data)?.lowercase()
            return when (data.scheme?.lowercase()) {
                "magnet" -> IncomingSharedSourceRepository.submitText(data.toString())
                "http", "https" -> IncomingSharedSourceRepository.submitText(data.toString()) ||
                    (mime?.startsWith("video/") == true &&
                        IncomingSharedSourceRepository.submitDirectStream(data.toString(), data.lastPathSegment)) ||
                    (isM3uMimeType(mime) && IncomingSharedSourceRepository.submitPlaylistUrl(data.toString()))
                "content", "file" -> if (mime?.startsWith("video/") == true) {
                    IncomingSharedSourceRepository.submitDirectStream(data.toString(), displayName(data, contentResolver))
                } else {
                    submitPlaylistUri(data, contentResolver)
                }
                else -> false
            }
        }
    }
    return false
}

private fun submitPlaylistUri(uri: Uri, contentResolver: ContentResolver): Boolean {
    val text = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                if (buffer.size() > MAX_SHARED_PLAYLIST_BYTES) return@use null
            }
            buffer.toByteArray().decodeToString()
        }
    }.getOrNull() ?: return false
    return IncomingSharedSourceRepository.submitPlaylistData(displayName(uri, contentResolver), text)
}

private fun displayName(uri: Uri, contentResolver: ContentResolver): String? =
    if (uri.scheme == "file") {
        uri.lastPathSegment?.substringAfterLast('/')
    } else {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
