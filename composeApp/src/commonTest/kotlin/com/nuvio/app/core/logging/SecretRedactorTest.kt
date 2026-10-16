package com.nuvio.app.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretRedactorTest {
    // Synthetic values only: not real keys or tokens.
    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXVzZXIifQ.c2lnbmF0dXJlLXRlc3Q"

    @Test
    fun supabaseExceptionHeadersAreRedacted() {
        val message = """
            invalid_credentials
            URL: https://api.example.test/auth/v1/token?grant_type=password
            Headers: [apikey=[$jwt], Authorization=[Bearer $jwt], X-Client-Info=[supabase-kt/3.4.1]]
            Http Method: POST
        """.trimIndent()
        val redacted = SecretRedactor.redact(message)
        assertFalse(redacted.contains(jwt), redacted)
        assertTrue(redacted.contains("invalid_credentials"))
        assertTrue(redacted.contains("X-Client-Info=[supabase-kt/3.4.1]"))
        assertTrue(redacted.contains("grant_type=password"))
    }

    @Test
    fun tokensInBodiesAndQueriesAreRedacted() {
        val text = """{"access_token":"abc123def","refresh_token":"r3fr3sh","password":"hunter2"} ?apikey=sb_publishable_abcdefghijkl&x=1"""
        val redacted = SecretRedactor.redact(text)
        listOf("abc123def", "r3fr3sh", "hunter2", "sb_publishable_abcdefghijkl").forEach {
            assertFalse(redacted.contains(it), redacted)
        }
        assertTrue(redacted.contains("x=1"))
    }

    @Test
    fun apiKeyQueryParametersAreRedacted() {
        val text = "GET https://api.giphy.com/v1/gifs/search?api_key=Gk9xSecretValue&q=cat"
        val redacted = SecretRedactor.redact(text)
        assertFalse(redacted.contains("Gk9xSecretValue"), redacted)
        assertTrue(redacted.contains("q=cat"), redacted)
    }

    @Test
    fun plainTextIsUnchanged() {
        val text = "Email sign-in failed: Network (timeout after 15000 ms)"
        assertEquals(text, SecretRedactor.redact(text))
    }

    @Test
    fun throwablesAreSanitizedIncludingCauses() {
        val error = IllegalStateException("outer Authorization: Bearer $jwt", RuntimeException("inner apikey=$jwt"))
        val sanitized = SecretRedactor.sanitize(error)
        assertFalse(sanitized.message.orEmpty().contains(jwt))
        assertFalse(sanitized.cause?.message.orEmpty().contains(jwt))
        assertTrue(sanitized.message.orEmpty().startsWith("IllegalStateException"))
    }
}
