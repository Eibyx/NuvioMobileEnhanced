package com.nuvio.app.core.diagnostics

import coil3.Extras
import coil3.network.NetworkClient
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArtworkDiagnosticNetworkClientTest {
    @After fun disable() { ArtworkDiagnostics.install(null) }
    @Test fun preservesConditionalRequestAnd304ResponseWithoutLoggingCredentials() = runBlocking {
        val events = mutableListOf<String>()
        ArtworkDiagnostics.install(events::add)
        val request = NetworkRequest(
            url = "https://username:password@example.com/private/secret?api_key=secret",
            headers = NetworkHeaders.Builder().add("If-None-Match", "private-etag").build(),
            extras = Extras.Builder().apply { this[artworkRequestTagKey] = ArtworkRequestTag("abc", "DETAILS_BACKDROP", "METADATA") }.build(),
        )
        val response = NetworkResponse(code = 304, headers = NetworkHeaders.Builder().add("ETag", "private-etag").build())
        val client = ArtworkDiagnosticNetworkClient(object : NetworkClient {
            override suspend fun <T> executeRequest(input: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                assertSame(request, input)
                return block(response)
            }
        })
        assertSame(response, client.executeRequest(request) { it })
        assertTrue(events.any { "status=304" in it })
        assertTrue(events.any { "conditional=true" in it })
        assertFalse(events.any { "secret" in it || "password" in it || "private-etag" in it || "example.com" in it })
        assertTrue(events.last().contains("active=0"))
    }
    @Test fun cancellationIsPropagatedAndActiveCountIsBalanced() = runBlocking {
        val events = mutableListOf<String>()
        ArtworkDiagnostics.install(events::add)
        val client = ArtworkDiagnosticNetworkClient(object : NetworkClient {
            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                throw CancellationException("private reason")
            }
        })
        assertFailsWith<CancellationException> { client.executeRequest(NetworkRequest("https://example.com", extras = Extras.Builder().apply { this[artworkRequestTagKey] = ArtworkRequestTag("abc", "HERO_VISIBLE", "CATALOG") }.build())) { it } }
        assertTrue(events.last().contains("active=0"))
        assertEquals(2, events.size)
        assertFalse(events.any { "private reason" in it })
    }
}
