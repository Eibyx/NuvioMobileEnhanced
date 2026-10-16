package com.nuvio.app.core.auth

import com.nuvio.app.core.network.ServerCapabilities
import com.nuvio.app.core.network.ServerConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthFailureTest {
    private fun config(url: String, key: String, custom: Boolean = false) = ServerConfiguration(
        backendUrl = url,
        publishableKey = key,
        capabilities = ServerCapabilities(emailPasswordAuth = true, tvLogin = true),
        isCustom = custom,
    )

    @Test
    fun missingBuildConfigurationIsNotUsable() {
        assertFalse(config("", "").isUsable)
        assertFalse(config("https://api.example.test", "").isUsable)
        assertFalse(config("", "key").isUsable)
        assertFalse(config("http://api.example.test", "key").isUsable)
        assertTrue(config("https://api.example.test", "key").isUsable)
        assertTrue(config("http://192.168.1.2:8000", "key", custom = true).isUsable)
    }

    private class UnknownHostException(message: String) : Exception(message)
    private class SocketTimeoutException(message: String) : Exception(message)

    @Test
    fun connectivityFailuresAreClassifiedAsNetwork() {
        assertEquals(AuthFailureKind.Network, classifyAuthFailure(UnknownHostException("api.example.test")))
        assertEquals(
            AuthFailureKind.Network,
            classifyAuthFailure(IllegalStateException("wrapped", SocketTimeoutException("read timed out"))),
        )
        assertEquals(AuthFailureKind.Unknown, classifyAuthFailure(IllegalArgumentException("other")))
    }
}
