package com.nuvio.app.core.diagnostics

import kotlinx.atomicfu.atomic
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.TimeSource

/** Observations only: no requests, caches, retries or retained images. Disabled until installed
 * by a debuggable Android application with the explicit diagnostic manifest flag. */
internal object ArtworkDiagnostics {
    private val output = atomic<((String) -> Unit)?>(null)
    private val sequence = atomic(0L)
    private val origin = TimeSource.Monotonic.markNow()
    val enabled: Boolean get() = output.value != null

    fun install(sink: ((String) -> Unit)?) { output.value = sink }
    fun nextId(): Long = sequence.incrementAndGet()

    // Never emit the original input, including paths, tokens, signed URLs or exception messages.
    fun token(value: Any?): String = value?.toString()?.encodeUtf8()?.sha256()?.hex()?.take(16) ?: "none"

    fun event(name: String, fields: () -> String = { "" }) {
        val sink = output.value ?: return
        sink("t=${origin.elapsedNow().inWholeMilliseconds}ms event=$name ${fields()}")
    }
}
