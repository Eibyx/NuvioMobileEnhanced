package com.nuvio.app.features.livetv

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Avix (Yesnt10 port of LiveTvIncomingSource): content opened in or shared to the app from
 * another app — an M3U playlist (link or file), a direct video link or a magnet link.
 */
sealed interface IncomingSharedSource {
    data class PlaylistUrl(val url: String) : IncomingSharedSource
    data class PlaylistData(val fileName: String, val data: String) : IncomingSharedSource
    data class DirectStream(val url: String, val title: String) : IncomingSharedSource
    data class Magnet(val infoHash: String, val name: String?, val trackers: List<String>) : IncomingSharedSource
}

object IncomingSharedSourceRepository {
    private val channel = Channel<IncomingSharedSource>(capacity = Channel.BUFFERED)
    val requests: Flow<IncomingSharedSource> = channel.receiveAsFlow()

    /** Text shared or opened from another app; returns false when nothing usable was found. */
    fun submitText(text: String): Boolean = parseSharedText(text)?.let(channel::trySend)?.isSuccess == true

    fun submitDirectStream(url: String, title: String?): Boolean =
        channel.trySend(IncomingSharedSource.DirectStream(url, title?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/'))).isSuccess

    fun submitPlaylistUrl(url: String): Boolean = channel.trySend(IncomingSharedSource.PlaylistUrl(url)).isSuccess

    fun submitPlaylistData(fileName: String?, data: String): Boolean {
        val trimmed = data.trim()
        if (!looksLikeM3u(trimmed)) return false
        return channel.trySend(
            IncomingSharedSource.PlaylistData(fileName?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_PLAYLIST_NAME, trimmed),
        ).isSuccess
    }
}

internal const val DEFAULT_PLAYLIST_NAME = "M3U"
internal const val MAX_SHARED_PLAYLIST_BYTES = 20 * 1024 * 1024

private val sharedUrlRegex = Regex("""(?i)(magnet:\?[^\s"'<>]+|https?://[^\s"'<>]+)""")
private val videoExtensions = listOf(".mp4", ".mkv", ".webm", ".mov", ".ts", ".avi", ".m4v")

/** MIME types used for M3U playlists (HLS uses some of them too; links are disambiguated by extension). */
internal fun isM3uMimeType(mime: String?): Boolean = mime?.lowercase()?.let {
    it == "audio/x-mpegurl" || it == "audio/mpegurl" || it == "application/x-mpegurl" || it == "application/vnd.apple.mpegurl"
} == true

internal fun looksLikeM3u(text: String): Boolean = text.trimStart().startsWith("#EXTM3U", ignoreCase = true)

internal fun parseSharedText(text: String): IncomingSharedSource? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (looksLikeM3u(trimmed)) return IncomingSharedSource.PlaylistData(DEFAULT_PLAYLIST_NAME, trimmed)
    val url = sharedUrlRegex.find(trimmed)?.value?.trimEnd('.', ',', ')', ']') ?: return null
    if (url.startsWith("magnet:", ignoreCase = true)) return parseMagnet(url)
    val path = url.substringBefore('#').substringBefore('?').lowercase()
    return when {
        path.endsWith(".m3u") -> IncomingSharedSource.PlaylistUrl(url)
        // .m3u8 is usually an HLS stream rather than a channel list: play it directly.
        path.endsWith(".m3u8") || videoExtensions.any(path::endsWith) ->
            IncomingSharedSource.DirectStream(url, url.substringBefore('?').substringAfterLast('/').ifBlank { url })
        else -> null
    }
}

/** Parses `magnet:?xt=urn:btih:<hex40|base32>&dn=..&tr=..`; the hash is returned as lowercase hex. */
internal fun parseMagnet(uri: String): IncomingSharedSource.Magnet? {
    if (!uri.startsWith("magnet:?", ignoreCase = true)) return null
    val params = uri.substring("magnet:?".length).split('&').mapNotNull { part ->
        val key = part.substringBefore('=', "").lowercase()
        val value = part.substringAfter('=', "")
        if (key.isEmpty() || value.isEmpty()) null else key to percentDecode(value)
    }
    val btih = params.firstOrNull { (key, value) -> key == "xt" && value.startsWith("urn:btih:", ignoreCase = true) }
        ?.second?.substring("urn:btih:".length) ?: return null
    val hash = when {
        btih.length == 40 && btih.all { it.isHexDigitChar() } -> btih.lowercase()
        btih.length == 32 -> base32ToHex(btih) ?: return null
        else -> return null
    }
    return IncomingSharedSource.Magnet(
        infoHash = hash,
        name = params.firstOrNull { it.first == "dn" }?.second?.takeIf { it.isNotBlank() },
        trackers = params.filter { it.first == "tr" }.map { it.second }
            .filter { it.startsWith("udp://") || it.startsWith("http://") || it.startsWith("https://") || it.startsWith("wss://") }
            .distinct()
            .take(MAX_TRACKERS),
    )
}

private const val MAX_TRACKERS = 30

private fun Char.isHexDigitChar(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

internal fun base32ToHex(value: String): String? {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    var buffer = 0L
    var bits = 0
    val out = StringBuilder()
    for (c in value.uppercase()) {
        val v = alphabet.indexOf(c)
        if (v < 0) return null
        buffer = (buffer shl 5) or v.toLong()
        bits += 5
        if (bits >= 8) {
            bits -= 8
            val byte = ((buffer shr bits) and 0xFF).toInt()
            out.append(byte.toString(16).padStart(2, '0'))
        }
    }
    return out.toString().takeIf { it.length == 40 }
}

private fun percentDecode(value: String): String {
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '%' && i + 2 <= value.lastIndex -> {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.add(hex.toByte())
                    i += 3
                    continue
                }
                bytes.addAll("%".encodeToByteArray().toList())
            }
            c == '+' -> bytes.add(' '.code.toByte())
            else -> bytes.addAll(c.toString().encodeToByteArray().toList())
        }
        i++
    }
    return bytes.toByteArray().decodeToString()
}
