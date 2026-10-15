package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// iOS is not part of Nuvio Avix v1; the import action is hidden when unsupported.
@Composable
internal actual fun rememberLocalSubtitlePicker(
    onImported: (AddonSubtitle) -> Unit,
    onError: (LocalSubtitleError?) -> Unit,
): LocalSubtitlePicker = remember { LocalSubtitlePicker(isSupported = false) {} }
