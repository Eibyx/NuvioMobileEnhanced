package com.nuvio.app.core.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberArtworkReportSharing(): ArtworkReportSharing = remember {
    ArtworkReportSharing(supported = false, share = { false }, clearFiles = {})
}

// Capture UI is unavailable on iOS; keep the shared observer compilable.
internal actual fun artworkObjectIdentity(value: Any): Int = value.hashCode()
