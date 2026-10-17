package com.nuvio.app.features.home.components

import co.touchlab.kermit.Logger
import kotlin.time.TimeSource

/** Developer opt-in (debugger): disabled by default, including production. No URLs, headers,
 * tokens or raw cache keys are logged. drawSubmitted measures a draw call, not display scanout.
 */
internal object HeroImageDiagnostics {
    var enabled: Boolean = false
    fun begin(resource: String?): HeroImageTrace? =
        if (enabled) HeroImageTrace(resource?.hashCode()?.toUInt()?.toString(16).orEmpty()) else null
}

internal class HeroImageTrace(private val resource: String) {
    private val start = TimeSource.Monotonic.markNow()
    var ready = false
    private var drawn = false
    fun event(stage: String, details: String = "") {
        Logger.withTag("NuvioHero").d { "resource=$resource stage=$stage ms=${start.elapsedNow().inWholeMilliseconds} $details" }
    }
    fun drawSubmitted() {
        if (ready && !drawn) {
            drawn = true
            event("drawSubmitted")
        }
    }
}
