package com.nuvio.app.features.trakt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraktAuthRepositoryTest {

    @Test
    fun `HTTP 400 permanently invalidates a rejected refresh token`() {
        assertEquals(
            TraktTokenRefreshResponseAction.INVALIDATE,
            traktTokenRefreshResponseAction(400),
        )
    }

    @Test
    fun `successful token responses are accepted`() {
        assertEquals(
            TraktTokenRefreshResponseAction.ACCEPT,
            traktTokenRefreshResponseAction(200),
        )
        assertEquals(
            TraktTokenRefreshResponseAction.ACCEPT,
            traktTokenRefreshResponseAction(201),
        )
    }

    @Test
    fun `non-400 failures preserve credentials for a later attempt`() {
        listOf(401, 429, 500, 503).forEach { status ->
            assertEquals(
                TraktTokenRefreshResponseAction.TRANSIENT_FAILURE,
                traktTokenRefreshResponseAction(status),
            )
        }
    }

    @Test
    fun `code challenge is unpadded base64url sha256 of the verifier`() {
        assertEquals(
            "M2y4DqP-18KKq4IT9FxbjghLJsFpgGT4Ib1KC8uicPQ",
            traktCodeChallenge("dBjftJeZ4CVP-mJ0r6Kc8pjAcRsiObl5_CmB3kgvJHQ"),
        )
    }

    @Test
    fun `authorization url carries state and S256 challenge`() {
        val url = buildTraktAuthorizationUrl(
            authorizeUrl = "https://auth.trakt.tv/oauth/authorize",
            clientId = "client",
            redirectUri = "avixnuvio://auth/trakt",
            state = "state-value",
            codeChallenge = "challenge-value",
        )
        assertTrue(url.startsWith("https://auth.trakt.tv/oauth/authorize?response_type=code&"))
        assertTrue("redirect_uri=avixnuvio%3A%2F%2Fauth%2Ftrakt" in url)
        assertTrue("state=state-value" in url)
        assertTrue("code_challenge=challenge-value" in url)
        assertTrue(url.endsWith("&code_challenge_method=S256"))
    }

    @Test
    fun `token exchange sends the code verifier and no client secret`() {
        val body = Json.parseToJsonElement(
            traktAuthorizationCodeRequestBody(
                code = "code",
                clientId = "client",
                redirectUri = "avixnuvio://auth/trakt",
                codeVerifier = "verifier",
            ),
        ).jsonObject
        assertFalse("client_secret" in body)
        assertEquals("verifier", body["code_verifier"]?.jsonPrimitive?.content)
        assertEquals("authorization_code", body["grant_type"]?.jsonPrimitive?.content)
        assertEquals("avixnuvio://auth/trakt", body["redirect_uri"]?.jsonPrimitive?.content)
    }

    @Test
    fun `refresh, device token and revoke bodies never contain a client secret`() {
        val refresh = Json.parseToJsonElement(
            traktRefreshTokenRequestBody("refresh", "client", "avixnuvio://auth/trakt"),
        ).jsonObject
        assertFalse("client_secret" in refresh)
        assertEquals("refresh_token", refresh["grant_type"]?.jsonPrimitive?.content)
        assertEquals("client", refresh["client_id"]?.jsonPrimitive?.content)
        val revoke = Json.parseToJsonElement(traktRevokeRequestBody("token", "client")).jsonObject
        assertFalse("client_secret" in revoke)
        val device = Json.parseToJsonElement(traktDeviceTokenRequestBody("device", "client")).jsonObject
        assertFalse("client_secret" in device)
    }
}
