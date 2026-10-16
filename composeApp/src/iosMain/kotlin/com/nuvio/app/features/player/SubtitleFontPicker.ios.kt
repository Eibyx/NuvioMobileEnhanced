package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// iOS is not part of Nuvio Avix v1.
@Composable
actual fun rememberSubtitleFontPicker(
    onImported: (CustomSubtitleFont) -> Unit,
    onError: (SubtitleFontImportError?) -> Unit,
): SubtitleFontPicker = remember { SubtitleFontPicker(isSupported = false) {} }
