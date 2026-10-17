package com.nuvio.app.core.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import coil3.compose.AsyncImagePainter

/** Returns the original modifier and null callback when disabled. draw.submitted is a CPU draw
 * observation; it is NOT proof of GPU presentation, visibility, alpha or crossfade completion. */
internal class ArtworkCompositionProbe(private val role: String, private val resource: String?) {
    private val instance = ArtworkDiagnostics.nextId()
    private var successful by mutableStateOf(false)
    private var submitted = false
    fun event(name: String) = ArtworkDiagnostics.event(name) {
        "instance=$instance role=$role resource=${ArtworkDiagnostics.token(resource)}"
    }
    fun state(state: AsyncImagePainter.State) {
        successful = state is AsyncImagePainter.State.Success
        submitted = false
        event("compose.image.${state::class.simpleName}")
    }
    fun draw() {
        if (successful && !submitted) { submitted = true; event("draw.submitted") }
    }
    fun modifier(modifier: Modifier): Modifier = modifier.drawWithContent { drawContent(); draw() }
}

@Composable
internal fun rememberArtworkProbe(role: String, resource: String?): ArtworkCompositionProbe? {
    if (!ArtworkDiagnostics.enabled) return null
    val probe = remember(role, resource) { ArtworkCompositionProbe(role, resource) }
    DisposableEffect(probe) {
        probe.event("compose.image.enter")
        onDispose { probe.event("compose.image.dispose") }
    }
    return probe
}

@Composable
internal fun ArtworkScreenProbe(screen: String, identity: String? = null) {
    if (!ArtworkDiagnostics.enabled) return
    DisposableEffect(screen, identity) {
        ArtworkDiagnostics.event("screen.enter") { "screen=$screen identity=${ArtworkDiagnostics.token(identity)}" }
        onDispose { ArtworkDiagnostics.event("screen.dispose") { "screen=$screen identity=${ArtworkDiagnostics.token(identity)}" } }
    }
    LaunchedEffect(screen, identity) {
        androidx.compose.runtime.withFrameNanos { }
        ArtworkDiagnostics.event("screen.frame.callback") { "screen=$screen" }
    }
}
