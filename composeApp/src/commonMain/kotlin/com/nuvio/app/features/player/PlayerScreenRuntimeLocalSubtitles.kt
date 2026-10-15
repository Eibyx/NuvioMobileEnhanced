package com.nuvio.app.features.player

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_local_subtitle_error_empty
import nuvio.composeapp.generated.resources.player_local_subtitle_error_generic
import nuvio.composeapp.generated.resources.player_local_subtitle_error_no_cues
import nuvio.composeapp.generated.resources.player_local_subtitle_error_too_large
import nuvio.composeapp.generated.resources.player_local_subtitle_error_unsupported
import nuvio.composeapp.generated.resources.player_local_subtitle_imported

/** Adds a subtitle imported from a device file (newest first) and activates it immediately. */
internal fun PlayerScreenRuntime.onLocalSubtitleImported(subtitle: AddonSubtitle) {
    importedSubtitles = listOf(subtitle) + importedSubtitles.filterNot { it.id == subtitle.id }
    selectLocalSubtitle(subtitle)
    showGestureFeedback(
        GestureFeedbackState(
            messageRes = Res.string.player_local_subtitle_imported,
            messageArgs = listOf(subtitle.display),
        ),
    )
}

/**
 * Activates an imported subtitle without persisting it as the language preference for future
 * episodes (the file belongs to this item only). Playback position is kept by the engine.
 */
internal fun PlayerScreenRuntime.selectLocalSubtitle(subtitle: AddonSubtitle) {
    clearAutoSubtitleState()
    isUserExplicitSubtitleSelection = true
    selectedAddonSubtitleId = subtitle.selectionKey
    selectedSubtitleIndex = -1
    useCustomSubtitles = true
    preferredSubtitleSelectionApplied = true
    playerController?.setSubtitleUri(subtitle.url)
    if (hasBurnedInServerSubtitle) clearServerSubtitleTrack()
}

internal fun PlayerScreenRuntime.onLocalSubtitleImportFailed(error: LocalSubtitleError?) {
    showGestureFeedback(
        GestureFeedbackState(
            messageRes = when (error) {
                LocalSubtitleError.EMPTY -> Res.string.player_local_subtitle_error_empty
                LocalSubtitleError.TOO_LARGE -> Res.string.player_local_subtitle_error_too_large
                LocalSubtitleError.UNSUPPORTED_FORMAT -> Res.string.player_local_subtitle_error_unsupported
                LocalSubtitleError.NO_CUES -> Res.string.player_local_subtitle_error_no_cues
                null -> Res.string.player_local_subtitle_error_generic
            },
            isDanger = true,
        ),
    )
}
