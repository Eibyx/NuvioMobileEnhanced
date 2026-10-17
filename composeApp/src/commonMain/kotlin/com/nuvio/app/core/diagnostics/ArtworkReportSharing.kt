package com.nuvio.app.core.diagnostics

import androidx.compose.runtime.Composable

internal class ArtworkReportSharing(
    val supported: Boolean,
    val share: suspend (String) -> Boolean,
    val clearFiles: suspend () -> Unit,
)
@Composable internal expect fun rememberArtworkReportSharing(): ArtworkReportSharing

internal expect fun artworkObjectIdentity(value: Any): Int
