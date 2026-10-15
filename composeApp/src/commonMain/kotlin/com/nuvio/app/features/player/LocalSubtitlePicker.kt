package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

/** Opens the system file picker and imports the chosen subtitle into the app's private cache. */
internal class LocalSubtitlePicker(
    val isSupported: Boolean,
    private val onLaunch: () -> Unit,
) {
    fun launch() = onLaunch()
}

/**
 * [onImported] receives the imported subtitle as an [AddonSubtitle] whose `addonName` is
 * [LOCAL_SUBTITLE_SOURCE] and whose `url` is a private `file://` URI. [onError] receives the
 * validation error, or null for an unexpected read failure.
 */
@Composable
internal expect fun rememberLocalSubtitlePicker(
    onImported: (AddonSubtitle) -> Unit,
    onError: (LocalSubtitleError?) -> Unit,
): LocalSubtitlePicker
