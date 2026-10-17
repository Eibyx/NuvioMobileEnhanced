package com.nuvio.app.core.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import coil3.compose.AsyncImagePainter

/** No painter/image retained here: just readiness and numeric identity observations. */
internal class ArtworkCompositionProbe(
    private val role: String, private val resource: String?, private val identity: String, private val source: String,
) {
    private val instance = ArtworkDiagnostics.nextId()
    private var successful by mutableStateOf(false)
    private var lastPainter: Int? = null // An identity token, NEVER a Painter/bitmap reference.
    private var resultDescription = ""
    private var submittedEpoch = -1L
    fun event(name: String, extra: String = "") = ArtworkDiagnostics.event(name) {
        "instance=$instance role=$role identity=${ArtworkDiagnostics.content(identity)} resource=${ArtworkDiagnostics.token(resource)} $extra"
    }
    fun entered() {
        ArtworkDiagnostics.observe(identity, resource, source)
        event("compose.image.enter", "origin=${ArtworkDiagnostics.source(resource, source)} available=${resource != null} ready=$successful $resultDescription")
    }
    fun state(state: AsyncImagePainter.State) {
        successful = state is AsyncImagePainter.State.Success
        resultDescription = when (state) {
            is AsyncImagePainter.State.Success -> "source=${state.result.dataSource} decoded=${state.result.image.width}x${state.result.image.height}"
            is AsyncImagePainter.State.Error -> "kind=${state.result.throwable::class.simpleName}"
            else -> ""
        }
        val painterToken = state.painter?.let(::artworkObjectIdentity)
        if (lastPainter != painterToken) { lastPainter = painterToken; event("compose.painter.changed", "painter=$painterToken") }
        submittedEpoch = -1L
        event("compose.image.${state::class.simpleName}", resultDescription)
    }
    fun draw() {
        if (ArtworkDiagnostics.enabled && successful && submittedEpoch != ArtworkDiagnostics.epoch()) {
            submittedEpoch = ArtworkDiagnostics.epoch(); event("draw.submitted")
        }
    }
    fun modifier(modifier: Modifier): Modifier = modifier.drawWithContent { drawContent(); draw() }
}

@Composable
internal fun rememberArtworkProbe(role: String, resource: String?, identity: String = role, source: String = "UNKNOWN"): ArtworkCompositionProbe? {
    if (!ArtworkDiagnostics.available) return null
    val epoch by ArtworkDiagnostics.generation.collectAsState()
    ArtworkDiagnostics.observe(identity, resource, source)
    val probe = remember(role, resource, identity, source) { ArtworkCompositionProbe(role, resource, identity, source) }
    DisposableEffect(probe) { onDispose { probe.event("compose.image.dispose") } }
    LaunchedEffect(probe, epoch) { probe.entered() }
    return probe
}

@Composable
internal fun ArtworkScreenProbe(screen: String, identity: String? = null) {
    if (!ArtworkDiagnostics.available) return
    val epoch by ArtworkDiagnostics.generation.collectAsState()
    DisposableEffect(screen, identity) {
        onDispose { ArtworkDiagnostics.event("screen.dispose") { "screen=$screen identity=${identity?.let(ArtworkDiagnostics::content)}" } }
    }
    LaunchedEffect(screen, identity, epoch) {
        if (!ArtworkDiagnostics.enabled) return@LaunchedEffect
        if (identity != null) ArtworkDiagnostics.selected(screen, identity)
        ArtworkDiagnostics.event("screen.enter") { "screen=$screen identity=${identity?.let(ArtworkDiagnostics::content)}" }
        androidx.compose.runtime.withFrameNanos { }
        ArtworkDiagnostics.event("screen.frame.callback") { "screen=$screen" }
    }
}
