package com.nuvio.app.features.downloads

import com.nuvio.app.features.player.SubtitleAddonRequest
import com.nuvio.app.features.streams.StreamSubtitle
import kotlinx.serialization.Serializable
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.downloads_enqueue_missing_url
import nuvio.composeapp.generated.resources.downloads_enqueue_replaced
import nuvio.composeapp.generated.resources.downloads_enqueue_started
import nuvio.composeapp.generated.resources.downloads_enqueue_unsupported_format
import nuvio.composeapp.generated.resources.downloads_enqueue_requires_wifi
import org.jetbrains.compose.resources.getString

@Serializable
enum class DownloadStatus {
    Downloading,
    Paused,
    Completed,
    Failed,
}

@Serializable
data class DownloadItem(
    val id: String,
    val contentType: String,
    val parentMetaId: String,
    val parentMetaType: String,
    val videoId: String,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val streamTitle: String,
    val streamSubtitle: String? = null,
    val providerName: String,
    val providerAddonId: String? = null,
    val sourceUrl: String,
    val sourceHeaders: Map<String, String> = emptyMap(),
    val sourceResponseHeaders: Map<String, String> = emptyMap(),
    val subtitleRequests: List<SubtitleAddonRequest> = emptyList(),
    val sourceSubtitles: List<StreamSubtitle> = emptyList(),
    val localFileUri: String? = null,
    val fileName: String,
    val status: DownloadStatus,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    /** Avix (ported from Yesnt10 402a1599): smoothed transfer rate; runtime-only, never persisted. */
    @kotlinx.serialization.Transient
    val downloadSpeedBytesPerSecond: Long = 0L,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    val isEpisode: Boolean
        get() = seasonNumber != null && episodeNumber != null

    val isPlayable: Boolean
        get() = status == DownloadStatus.Completed && !localFileUri.isNullOrBlank()

    val displaySubtitle: String
        get() = episodeTitle.orEmpty()

    /** Seconds left at the current speed, or null when unknown. */
    val estimatedRemainingSeconds: Long?
        get() {
            if (status != DownloadStatus.Downloading) return null
            val total = totalBytes?.takeIf { it > 0L } ?: return null
            val speed = downloadSpeedBytesPerSecond.takeIf { it > 0L } ?: return null
            val remainingBytes = (total - downloadedBytes).coerceAtLeast(0L)
            if (remainingBytes <= 0L) return 0L
            return (remainingBytes + speed - 1L) / speed
        }

    val progressFraction: Float
        get() {
            val total = totalBytes?.takeIf { it > 0L } ?: return 0f
            return (downloadedBytes.toDouble() / total.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        }

    val logicalContentKey: String
        get() = if (isEpisode) {
            "${parentMetaId.trim()}|${seasonNumber ?: -1}|${episodeNumber ?: -1}"
        } else {
            "${parentMetaId.trim()}|movie"
        }
}

data class DownloadsUiState(
    val items: List<DownloadItem> = emptyList(),
) {
    val activeItems: List<DownloadItem>
        get() = items.filter { it.status != DownloadStatus.Completed }

    val completedItems: List<DownloadItem>
        get() = items.filter { it.status == DownloadStatus.Completed }
}

enum class DownloadEnqueueResult {
    Started,
    Replaced,
    MissingUrl,
    UnsupportedFormat,
    RequiresWifi;

    fun toastMessage(): String = runBlocking {
        when (this@DownloadEnqueueResult) {
            Started -> getString(Res.string.downloads_enqueue_started)
            Replaced -> getString(Res.string.downloads_enqueue_replaced)
            MissingUrl -> getString(Res.string.downloads_enqueue_missing_url)
            UnsupportedFormat -> getString(Res.string.downloads_enqueue_unsupported_format)
            RequiresWifi -> getString(Res.string.downloads_enqueue_requires_wifi)
        }
    }
}

internal fun List<DownloadItem>.sortedForSeriesDownloads(): List<DownloadItem> =
    sortedWith(downloadSeriesEpisodeComparator)

internal val downloadSeriesEpisodeComparator: Comparator<DownloadItem> =
    compareBy<DownloadItem> { it.seasonNumber ?: Int.MAX_VALUE }
        .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
        .thenBy { it.episodeTitle?.trim().orEmpty().lowercase() }
        .thenBy { it.title.trim().lowercase() }
        .thenBy { it.id }

/**
 * Avix (Yesnt10 402a1599): exponential moving average of the transfer rate (65 % previous,
 * 35 % latest sample) so the speed and ETA do not jump on every progress callback.
 */
internal fun nextDownloadSpeed(
    previousSpeed: Long,
    previousBytes: Long,
    previousUpdatedAtMs: Long,
    nextBytes: Long,
    nowMs: Long,
): Long {
    val byteDelta = (nextBytes - previousBytes).coerceAtLeast(0L)
    val elapsedMs = (nowMs - previousUpdatedAtMs).coerceAtLeast(0L)
    val instant = if (byteDelta > 0L && elapsedMs > 0L) byteDelta * 1_000L / elapsedMs else return previousSpeed
    return if (previousSpeed > 0L) (previousSpeed * 0.65 + instant * 0.35).toLong() else instant
}

/** "45s", "8m 20s", "1h 5m". */
internal fun formatDownloadEta(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    if (safe < 60L) return "${safe}s"
    val minutes = safe / 60L
    val restSeconds = safe % 60L
    if (minutes < 60L) return if (restSeconds > 0L && minutes < 10L) "${minutes}m ${restSeconds}s" else "${minutes}m"
    val hours = minutes / 60L
    val restMinutes = minutes % 60L
    return if (restMinutes > 0L) "${hours}h ${restMinutes}m" else "${hours}h"
}
