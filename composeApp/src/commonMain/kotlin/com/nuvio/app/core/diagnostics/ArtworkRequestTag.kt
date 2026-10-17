package com.nuvio.app.core.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.Extras
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

internal data class ArtworkRequestTag(val identity: String, val purpose: String, val source: String, val loadId: Long = ArtworkDiagnostics.nextId())
internal val artworkRequestTagKey = Extras.Key<ArtworkRequestTag?>(null)

/** Extras, NOT memoryCacheKeyExtras: observations cannot change cache identity or image options.
 * Installed once for the diagnostic-capable build, independent of capture start/stop. */
internal fun ImageRequest.Builder.artworkTag(identity: String, purpose: String, source: String): ImageRequest.Builder = apply {
    if (ArtworkDiagnostics.available) extras[artworkRequestTagKey] = ArtworkRequestTag(ArtworkDiagnostics.content(identity), purpose, source)
}

@Composable
internal fun artworkDiagnosticModel(url: String?, identity: String, purpose: String, source: String): Any? {
    if (!ArtworkDiagnostics.available) return url
    val context = LocalPlatformContext.current
    return remember(url, context, identity, purpose, source) {
        ImageRequest.Builder(context).data(url).artworkTag(identity, purpose, source).build()
    }
}

internal class ArtworkMetadataContext(val identity: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ArtworkMetadataContext>
}
internal suspend fun <T> traceArtworkMetadata(identity: String, block: suspend () -> T): T {
    if (!ArtworkDiagnostics.enabled) return block()
    val key = ArtworkDiagnostics.content(identity)
    return if (ArtworkDiagnostics.operation(key) != null) {
        withContext(ArtworkMetadataContext(key)) { block() }
    } else block()
}
