package com.nuvio.app.features.player

import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.getBoolean
import com.nuvio.app.core.avix.putBoolean
import androidx.compose.runtime.collectAsState
import nuvio.composeapp.generated.resources.player_ends_at
import androidx.compose.runtime.getValue
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Avix (ported from Yesnt10 49f3bf7e): wall-clock time at which the current item ends, taking the
 * playback speed into account. Null for live streams, unknown durations or finished items.
 */
internal fun playbackEndClockLabel(
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String? {
    if (durationMs <= 0L) return null
    val remainingMs = (durationMs - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    if (remainingMs < 30_000L) return null
    val speed = playbackSpeed.takeIf { it > 0.05f } ?: 1f
    val endEpochMs = nowEpochMs + (remainingMs / speed).roundToLong()
    val end = Instant.fromEpochMilliseconds(endEpochMs).toLocalDateTime(timeZone)
    return "${end.hour.toString().padStart(2, '0')}:${end.minute.toString().padStart(2, '0')}"
}

/** Device-local switch for the end-time label (docs/integration/DECISIONS.md D-5). */
object PlaybackEndTimePreference {
    private const val KEY = "player.show_end_time"
    private val _enabled = MutableStateFlow(true)
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
        _enabled.value = AvixLocalStore.getBoolean(KEY) ?: true
    }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        _enabled.value = value
        AvixLocalStore.putBoolean(KEY, value)
    }
}

/** Runtime label ("12:03 / 45:10" or "−33:07") followed by "· ends 22:45" when enabled. */
@androidx.compose.runtime.Composable
internal fun playbackRuntimeWithEndLabel(
    displayedPositionMs: Long,
    snapshot: PlayerPlaybackSnapshot,
    showRemainingTime: Boolean,
): String {
    val runtime = formatPlaybackRuntime(displayedPositionMs, snapshot.durationMs, showRemainingTime)
    val showEnd by androidx.compose.runtime.remember {
        PlaybackEndTimePreference.ensureLoaded()
        PlaybackEndTimePreference.enabled
    }.collectAsState()
    val end = if (showEnd) {
        playbackEndClockLabel(displayedPositionMs, snapshot.durationMs, snapshot.playbackSpeed)
    } else {
        null
    }
    return if (end == null) {
        runtime
    } else {
        runtime + " · " + org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_ends_at,
            end,
        )
    }
}

/**
 * Avix (Yesnt10 "Remember my choices"): when off, the player neither saves nor reapplies the
 * per-title audio/subtitle choice nor the image format. Device-local; default on (Luqman behaviour).
 */
object RememberPlayerChoicesPreference {
    private const val KEY = "player.remember_choices"
    private val _enabled = MutableStateFlow(true)
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
        _enabled.value = AvixLocalStore.getBoolean(KEY) ?: true
    }

    val isEnabled: Boolean
        get() {
            ensureLoaded()
            return _enabled.value
        }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        _enabled.value = value
        AvixLocalStore.putBoolean(KEY, value)
    }
}
