package com.nuvio.app.features.player

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_engine_switching_ass_message
import nuvio.composeapp.generated.resources.player_engine_switching_manual_message

/** State captured right before an engine switch and re-applied once the new engine is ready. */
internal data class EngineSwitchRestore(
    val target: AndroidPlaybackEngine,
    val speed: Float,
    val audio: AudioTrack?,
    val subtitle: SubtitleRestore,
)

internal sealed interface SubtitleRestore {
    data object Off : SubtitleRestore
    data class Addon(val url: String) : SubtitleRestore
    data class Internal(val track: SubtitleTrack) : SubtitleRestore
}

private const val EngineSwitchRestoreAttempts = 20
private const val EngineSwitchRestoreRetryMs = 500L

internal val PlayerScreenRuntime.configuredPlaybackEngine: AndroidPlaybackEngine
    get() = playerSettingsUiState.androidPlaybackEngine

/** Key used to remember the engine per series (or movie). */
internal val PlayerScreenRuntime.engineTitleKey: String
    get() = parentMetaId

/**
 * Switches between ExoPlayer and libmpv while keeping position, speed, audio and subtitle
 * selection. [target] defaults to "the other engine". A manual switch is remembered for the
 * current title when that preference is enabled; automatic switches are not.
 */
internal fun PlayerScreenRuntime.switchPlaybackEngine(
    target: AndroidPlaybackEngine? = null,
    manual: Boolean = true,
) {
    val current = playerController?.playbackEngine ?: return
    val resolvedTarget = target
        ?: if (current == AndroidPlaybackEngine.Libmpv) AndroidPlaybackEngine.ExoPlayer else AndroidPlaybackEngine.Libmpv
    if (resolvedTarget == current) return

    pendingEngineSwitchRestore = captureEngineSwitchRestore(resolvedTarget)
    flushWatchProgress()
    if (initialSeekApplied) {
        activeInitialPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
        activeInitialProgressFraction = null
    }
    playbackEngineOverride = resolvedTarget
    if (manual) {
        AvixPlaybackEnginePreferences.rememberEngineForTitle(engineTitleKey, resolvedTarget)
    }
    showGestureFeedback(
        GestureFeedbackState(
            messageRes = if (manual) {
                Res.string.player_engine_switching_manual_message
            } else {
                Res.string.player_engine_switching_ass_message
            },
            messageArgs = listOf(resolvedTarget.label),
        ),
    )
}

private fun PlayerScreenRuntime.captureEngineSwitchRestore(target: AndroidPlaybackEngine): EngineSwitchRestore {
    val subtitle = when {
        useCustomSubtitles -> selectedAddonSubtitle?.url?.let(SubtitleRestore::Addon) ?: SubtitleRestore.Off
        selectedSubtitleIndex >= 0 ->
            subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex }?.let(SubtitleRestore::Internal)
                ?: SubtitleRestore.Off
        else -> SubtitleRestore.Off
    }
    return EngineSwitchRestore(
        target = target,
        speed = playbackSnapshot.playbackSpeed,
        audio = audioTracks.firstOrNull { it.index == selectedAudioIndex },
        subtitle = subtitle,
    )
}

/** Re-applies [PlayerScreenRuntime.pendingEngineSwitchRestore] once the target engine has loaded. */
internal fun PlayerScreenRuntime.applyPendingEngineSwitchRestore() {
    val restore = pendingEngineSwitchRestore ?: return
    val controller = playerController ?: return
    if (controller.playbackEngine != restore.target || playbackSnapshot.isLoading) return
    pendingEngineSwitchRestore = null
    // Runs on the runtime scope so recompositions (loading flips, key changes) cannot cancel it.
    scope.launch { restoreEngineSwitchState(controller, restore) }
}

private suspend fun PlayerScreenRuntime.restoreEngineSwitchState(
    controller: PlayerEngineController,
    restore: EngineSwitchRestore,
) {
    if (restore.speed > 0f && restore.speed != 1f) {
        controller.setPlaybackSpeed(restore.speed)
    }

    var audioPending = restore.audio != null
    var subtitlePending = restore.subtitle !is SubtitleRestore.Off || selectedSubtitleIndex >= 0
    repeat(EngineSwitchRestoreAttempts) {
        if (playerController !== controller) return
        refreshTracks()
        if (audioPending) {
            val match = matchAudioTrack(audioTracks, restore.audio)
            if (match != null) {
                controller.selectAudioTrack(match.index)
                selectedAudioIndex = match.index
                isUserExplicitAudioSelection = true
                preferredAudioSelectionApplied = true
                audioPending = false
            }
        }
        if (subtitlePending) {
            subtitlePending = !restoreSubtitleAfterEngineSwitch(controller, restore.subtitle)
        }
        if (!audioPending && !subtitlePending) return
        delay(EngineSwitchRestoreRetryMs)
    }
}

/** Returns true when the subtitle state has been applied (or nothing is left to apply). */
private fun PlayerScreenRuntime.restoreSubtitleAfterEngineSwitch(
    controller: PlayerEngineController,
    subtitle: SubtitleRestore,
): Boolean = when (subtitle) {
    SubtitleRestore.Off -> {
        controller.selectSubtitleTrack(-1)
        selectedSubtitleIndex = -1
        true
    }
    is SubtitleRestore.Addon -> {
        useCustomSubtitles = true
        preferredSubtitleSelectionApplied = true
        controller.setSubtitleUri(subtitle.url)
        true
    }
    is SubtitleRestore.Internal -> {
        val match = matchSubtitleTrack(subtitleTracks, subtitle.track)
        if (match != null) {
            useCustomSubtitles = false
            selectedSubtitleIndex = match.index
            isUserExplicitSubtitleSelection = true
            preferredSubtitleSelectionApplied = true
            controller.selectSubtitleTrack(match.index)
            true
        } else {
            false
        }
    }
}

internal fun matchAudioTrack(tracks: List<AudioTrack>, wanted: AudioTrack?): AudioTrack? {
    if (wanted == null || tracks.isEmpty()) return null
    val language = normalizeLanguageCode(wanted.language)
    return tracks.firstOrNull { it.label == wanted.label && normalizeLanguageCode(it.language) == language }
        ?: language?.let { lang -> tracks.firstOrNull { normalizeLanguageCode(it.language) == lang } }
}

internal fun matchSubtitleTrack(tracks: List<SubtitleTrack>, wanted: SubtitleTrack): SubtitleTrack? {
    if (tracks.isEmpty()) return null
    val language = normalizeLanguageCode(wanted.language)
    return tracks.firstOrNull {
        it.label == wanted.label && normalizeLanguageCode(it.language) == language && it.isForced == wanted.isForced
    } ?: tracks.firstOrNull {
        normalizeLanguageCode(it.language) == language && it.isForced == wanted.isForced
    } ?: language?.let { lang -> tracks.firstOrNull { normalizeLanguageCode(it.language) == lang } }
}

/** Whether the currently selected subtitle (embedded track or addon/external file) is ASS/SSA. */
internal val PlayerScreenRuntime.selectedSubtitleIsAss: Boolean
    get() = if (useCustomSubtitles) {
        isAssSubtitle(selectedAddonSubtitle?.url)
    } else {
        subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex }
            ?.let { isAssSubtitle(it.label, it.codec) }
            ?: false
    }

/**
 * Auto mode only: when an ASS/SSA subtitle is selected while ExoPlayer is playing, switch once
 * to libmpv (libass renders styling, positioning and animations as authored). Never runs when
 * the user picked an engine (globally, per title or in the player), and at most once per
 * playback item, so it cannot loop with the ExoPlayer→libmpv error fallback.
 */
internal fun PlayerScreenRuntime.maybeSwitchToLibmpvForAss() {
    if (configuredPlaybackEngine != AndroidPlaybackEngine.Auto) return
    if (playbackEngineOverride != null) return
    if (!AvixPlaybackEnginePreferences.preferLibmpvForAss.value) return
    val controller = playerController ?: return
    if (controller.playbackEngine != AndroidPlaybackEngine.ExoPlayer) return
    if (playbackSnapshot.isLoading || !selectedSubtitleIsAss) return
    val itemKey = activePlaybackIdentity
    if (autoAssEngineSwitchItemKey == itemKey) return
    autoAssEngineSwitchItemKey = itemKey
    switchPlaybackEngine(target = AndroidPlaybackEngine.Libmpv, manual = false)
}
