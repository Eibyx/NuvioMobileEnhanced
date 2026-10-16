package com.nuvio.app.features.streams

import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.getBoolean
import com.nuvio.app.core.avix.putBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avix (Yesnt10 port 5c14bc53/7369457c): fetch the streams of the details page's Play target in
 * the background so the stream list (or auto-play) is ready when Play is tapped. Off by default
 * because it sends requests to every enabled addon for titles that may never be played.
 * Device-local (DECISIONS D-5).
 */
object StreamPrefetchPreference {
    private const val KEY = "player.stream_prefetch"
    /** Time the details page must stay open before prefetching, so quick browsing costs nothing. */
    const val DELAY_MS = 1_500L

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private var loaded = false

    init {
        com.nuvio.app.core.avix.AvixPreferencesReload.register {
            loaded = false
            ensureLoaded()
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _enabled.value = AvixLocalStore.getBoolean(KEY) ?: false
    }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        _enabled.value = value
        AvixLocalStore.putBoolean(KEY, value)
    }
}

/** Arguments of the stream request the details page's primary Play button would make. */
data class StreamPrefetchTarget(
    val type: String,
    val videoId: String,
    val parentMetaId: String,
    val season: Int?,
    val episode: Int?,
)

/**
 * Mirrors the primary Play action of the details page (manual selection off), so a later tap on
 * Play reuses the prefetched request instead of starting a new one. Null when nothing should run.
 */
fun streamPrefetchTarget(
    metaType: String,
    metaId: String,
    isSeriesLike: Boolean,
    seriesVideoId: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    canPlay: Boolean,
): StreamPrefetchTarget? {
    if (!canPlay || metaId.isBlank()) return null
    return if (isSeriesLike) {
        val videoId = seriesVideoId?.takeIf { it.isNotBlank() } ?: return null
        StreamPrefetchTarget(metaType, videoId, metaId, seasonNumber, episodeNumber)
    } else {
        StreamPrefetchTarget(metaType, metaId, metaId, null, null)
    }
}
