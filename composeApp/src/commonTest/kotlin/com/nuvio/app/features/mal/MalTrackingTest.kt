// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.mal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MalTrackingTest {
    private fun sequentialBytes(start: Int = 0): (Int) -> ByteArray {
        var next = start
        return { size -> ByteArray(size) { (next++ and 0xff).toByte() } }
    }

    @Test
    fun `authorize url carries every parameter with the plain challenge method`() {
        val url = buildMalAuthorizeUrl(
            clientId = "client id",
            verifier = "abc-._~XYZ",
            state = "st&ate=1",
            redirectUri = "avixnuvio://auth/mal",
        )
        assertTrue(url.startsWith("https://myanimelist.net/v1/oauth2/authorize?"))
        val query = url.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals("code", query["response_type"])
        assertEquals("client%20id", query["client_id"])
        assertEquals("abc-._~XYZ", query["code_challenge"])
        assertEquals("plain", query["code_challenge_method"])
        assertEquals("st%26ate%3D1", query["state"])
        assertEquals("avixnuvio%3A%2F%2Fauth%2Fmal", query["redirect_uri"])
        assertEquals(6, query.size)
    }

    @Test
    fun `redirect uri uses the app scheme`() {
        assertEquals("avixnuvio://auth/mal", MalConfig.REDIRECT_URI)
    }

    @Test
    fun `verifier is 96 unreserved characters`() {
        val verifier = generateMalCodeVerifier(sequentialBytes())
        assertEquals(96, verifier.length)
        assertTrue(verifier.all { it in MAL_VERIFIER_ALPHABET })
        assertEquals(66, MAL_VERIFIER_ALPHABET.length)
        assertTrue(MAL_VERIFIER_ALPHABET.all { it.isLetterOrDigit() || it in "-._~" })
    }

    @Test
    fun `verifier generation rejects biased bytes`() {
        // 198..255 would bias the first characters, so they must be skipped rather than wrapped.
        val verifier = generateMalRandomToken(4, sequentialBytes(start = 198))
        assertEquals(4, verifier.length)
        assertEquals("ABCD", verifier)
    }

    @Test
    fun `state and verifier differ between calls`() {
        val source = sequentialBytes()
        assertNotEquals(generateMalState(source), generateMalState(source))
        assertEquals(MAL_STATE_LENGTH, generateMalState(source).length)
    }

    @Test
    fun `progress form uses the write field name`() {
        assertEquals(
            "num_watched_episodes=3&status=watching",
            buildMalProgressForm(MalProgressUpdate(3, MalListStatus.WATCHING)),
        )
        assertEquals("num_watched_episodes=0", buildMalProgressForm(MalProgressUpdate(0, null)))
    }

    @Test
    fun `token forms are url encoded`() {
        assertEquals(
            "client_id=abc&code=c%2Fd&code_verifier=v~1&grant_type=authorization_code&" +
                "redirect_uri=avixnuvio%3A%2F%2Fauth%2Fmal",
            buildMalTokenExchangeForm("abc", "c/d", "v~1", "avixnuvio://auth/mal"),
        )
        assertEquals(
            "client_id=abc&grant_type=refresh_token&refresh_token=r%2Bt",
            buildMalRefreshForm("abc", "r+t"),
        )
    }

    @Test
    fun `parses my_list_status with the read field name`() {
        val anime = parseMalAnime(
            """{"id":5114,"title":"FMA","num_episodes":64,
               "my_list_status":{"status":"watching","score":0,"num_episodes_watched":12,"is_rewatching":false,
               "updated_at":"2024-01-01T00:00:00+00:00"}}""",
        )
        assertNotNull(anime)
        assertEquals(5114L, anime.id)
        assertEquals(64, anime.numEpisodes)
        assertEquals(MalListStatus.WATCHING, anime.myListStatus?.status)
        assertEquals(12, anime.myListStatus?.numEpisodesWatched)
    }

    @Test
    fun `parses anime that is not on the list`() {
        val anime = parseMalAnime("""{"id":1,"num_episodes":0}""")
        assertNotNull(anime)
        assertNull(anime.myListStatus)
        assertNull(parseMalAnime("not json"))
    }

    @Test
    fun `parses token response and rejects one without access token`() {
        val tokens = parseMalTokenResponse(
            """{"token_type":"Bearer","expires_in":2678400,"access_token":"a","refresh_token":"r"}""",
        )
        assertEquals("a", tokens?.accessToken)
        assertEquals("r", tokens?.refreshToken)
        assertEquals(2678400L, tokens?.expiresIn)
        assertNull(parseMalTokenResponse("""{"error":"invalid_grant"}"""))
        assertEquals("MalTokenResponse()", tokens.toString())
    }

    @Test
    fun `progress only increases`() {
        val anime = MalAnime(id = 1, numEpisodes = 24, myListStatus = MalMyListStatus(MalListStatus.WATCHING, 5))
        assertNull(malProgressUpdateFor(anime, 5))
        assertNull(malProgressUpdateFor(anime, 3))
        assertEquals(MalProgressUpdate(6, MalListStatus.WATCHING), malProgressUpdateFor(anime, 6))
    }

    @Test
    fun `first episode adds an unlisted anime as watching`() {
        val anime = MalAnime(id = 1, numEpisodes = 12)
        assertEquals(MalProgressUpdate(1, MalListStatus.WATCHING), malProgressUpdateFor(anime, 1))
    }

    @Test
    fun `final episode marks completed`() {
        val anime = MalAnime(id = 1, numEpisodes = 12, myListStatus = MalMyListStatus(MalListStatus.WATCHING, 11))
        assertEquals(MalProgressUpdate(12, MalListStatus.COMPLETED), malProgressUpdateFor(anime, 12))
        // Episodes past the known total are clamped to it.
        val unlisted = MalAnime(id = 1, numEpisodes = 12)
        assertEquals(MalProgressUpdate(12, MalListStatus.COMPLETED), malProgressUpdateFor(unlisted, 15))
    }

    @Test
    fun `unknown episode total never completes`() {
        assertEquals(MalListStatus.WATCHING, malStatusForProgress(500, 0))
        assertEquals(MalListStatus.COMPLETED, malStatusForProgress(12, 12))
        assertEquals(MalListStatus.COMPLETED, malStatusForProgress(13, 12))
        assertEquals(MalListStatus.WATCHING, malStatusForProgress(11, 12))
    }

    @Test
    fun `history removal rolls back completed entries`() {
        val completed = MalAnime(id = 1, numEpisodes = 12, myListStatus = MalMyListStatus(MalListStatus.COMPLETED, 12))
        assertEquals(MalProgressUpdate(11, MalListStatus.WATCHING), malProgressRemovalFor(completed, 12))
        assertEquals(MalProgressUpdate(0, MalListStatus.PLAN_TO_WATCH), malProgressRemovalFor(completed, 1))
        val watching = MalAnime(id = 1, numEpisodes = 12, myListStatus = MalMyListStatus(MalListStatus.WATCHING, 4))
        assertNull(malProgressRemovalFor(watching, 6))
        assertEquals(MalProgressUpdate(2, null), malProgressRemovalFor(watching, 3))
    }

    @Test
    fun `callback parsing only accepts the mal redirect`() {
        val callback = parseMalCallback("avixnuvio://auth/mal?code=abc%2B1&state=xyz")
        assertEquals("abc+1", callback?.code)
        assertEquals("xyz", callback?.state)
        assertNull(callback?.error)
        assertEquals("access_denied", parseMalCallback("avixnuvio://auth/mal?error=access_denied&state=s")?.error)
        assertNull(parseMalCallback("avixnuvio://auth/anilist#access_token=t"))
        assertNull(parseMalCallback("avixnuvio://auth/malicious?code=x"))
    }

    @Test
    fun `pending authorization expires after ten minutes and is single use`() {
        var now = 1_000_000L
        val store = MalAuthStore(InMemoryPersistence(), now = { now })
        val pending = MalPendingAuthorization("state", "verifier")
        assertTrue(store.beginAuthorization(store.scope(), pending))
        assertEquals(pending, store.pendingAuthorization())
        assertTrue(store.state.value.awaitingApproval)
        now += MalAuthStore.PENDING_WINDOW_MS + 1
        assertNull(store.pendingAuthorization())
        store.refreshExpiry()
        assertFalse(store.state.value.awaitingApproval)

        assertTrue(store.beginAuthorization(store.scope(), pending))
        assertTrue(store.cancelAuthorization(store.scope()))
        assertNull(store.pendingAuthorization())
    }

    @Test
    fun `expired access token stays connected while a refresh token exists`() {
        var now = 0L
        val store = MalAuthStore(InMemoryPersistence(), now = { now })
        assertTrue(store.authorize(MalTokenResponse("a", "r", 3600), store.scope()))
        now += 7_200_000L
        store.refreshExpiry()
        assertTrue(store.state.value.isAuthenticated)
        assertEquals("r", store.credentials()?.refreshToken)

        val noRefresh = MalAuthStore(InMemoryPersistence(), now = { now })
        assertTrue(noRefresh.authorize(MalTokenResponse("a", null, 1), noRefresh.scope()))
        now += 2_000L
        noRefresh.refreshExpiry()
        assertFalse(noRefresh.state.value.isAuthenticated)
        assertEquals(MalAuthError.AUTHORIZATION_EXPIRED, noRefresh.state.value.error)
    }

    private class InMemoryPersistence : MalAuthPersistence {
        private val values = mutableMapOf<Int, String>()
        override fun read(profileId: Int): String? = values[profileId]
        override fun write(profileId: Int, value: String?) {
            if (value == null) values.remove(profileId) else values[profileId] = value
        }
        override fun clear() = values.clear()
    }
}
