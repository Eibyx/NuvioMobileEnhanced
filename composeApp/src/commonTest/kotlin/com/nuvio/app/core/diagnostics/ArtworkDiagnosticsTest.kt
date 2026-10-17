package com.nuvio.app.core.diagnostics

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ArtworkDiagnosticsTest {
    @AfterTest fun disable() { ArtworkDiagnostics.install(null) }
    @Test fun disabledProbeDoesNotEvaluateFields() {
        ArtworkDiagnostics.install(null)
        ArtworkDiagnostics.event("test") { error("disabled diagnostics evaluated fields") }
        assertFalse(ArtworkDiagnostics.enabled)
    }
    @Test fun identifiersAreStableWithoutRevealingSecrets() {
        val signedUrl = "https://user:password@example.com/private/api-key/image?token=secret"
        val key = ArtworkDiagnostics.token(signedUrl)
        assertEquals(16, key.length)
        assertEquals(key, ArtworkDiagnostics.token(signedUrl))
        assertNotEquals(key, ArtworkDiagnostics.token(signedUrl + "2"))
        assertTrue(key.all { it in "0123456789abcdef" })
        assertEquals("none", ArtworkDiagnostics.token(null))
    }
    @Test fun emittedEventsCarryMonotonicTimeAndSanitizedIdentity() {
        val messages = mutableListOf<String>()
        ArtworkDiagnostics.install(messages::add)
        ArtworkDiagnostics.event("test") { "resource=${ArtworkDiagnostics.token("https://example.com/?api_key=secret")}" }
        assertEquals(1, messages.size)
        assertTrue(messages.single().matches(Regex("t=\\d+ms event=test resource=[0-9a-f]{16}")))
        assertFalse(messages.single().contains("secret"))
    }
}
