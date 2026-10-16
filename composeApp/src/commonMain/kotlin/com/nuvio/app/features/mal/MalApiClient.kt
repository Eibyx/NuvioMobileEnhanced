// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.mal

import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.core.network.readBoundedResponseBody
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.contentLength
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MyAnimeList API v2 client. Tokens are only ever sent as headers or form fields and are never logged.
 */
internal class MalApiClient(
    private val store: MalAuthStore,
    private val client: HttpClient = createApiHttpClient(),
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) {
    private val refreshLock = Mutex()

    suspend fun exchangeCode(code: String, verifier: String): MalTokenResponse {
        val response = send(
            HttpMethod.Post,
            MalConfig.TOKEN_URL,
            token = null,
            form = buildMalTokenExchangeForm(MalConfig.CLIENT_ID, code, verifier, MalConfig.REDIRECT_URI),
        )
        if (response.status !in 200..299) throw MalApiException(response.status)
        return parseMalTokenResponse(response.body) ?: throw MalApiException(response.status, "Invalid token response")
    }

    suspend fun viewer(scope: MalAuthScope): MalViewer {
        val response = authorized(scope, HttpMethod.Get, "${MalConfig.API_BASE_URL}/users/@me")
        if (response.status !in 200..299) throw MalApiException(response.status)
        return parseMalViewer(response.body) ?: throw MalApiException(response.status, "Invalid viewer response")
    }

    /** Returns the anime with the user's `my_list_status`, or null when MyAnimeList does not know [malId]. */
    suspend fun anime(scope: MalAuthScope, malId: Long): MalAnime? {
        val response = authorized(
            scope,
            HttpMethod.Get,
            "${MalConfig.API_BASE_URL}/anime/$malId?fields=my_list_status,num_episodes",
        )
        if (response.status == 404) return null
        if (response.status !in 200..299) throw MalApiException(response.status)
        return parseMalAnime(response.body) ?: throw MalApiException(response.status, "Invalid anime response")
    }

    suspend fun updateListStatus(scope: MalAuthScope, malId: Long, update: MalProgressUpdate): MalMyListStatus? {
        val response = authorized(
            scope,
            HttpMethod.Patch,
            "${MalConfig.API_BASE_URL}/anime/$malId/my_list_status",
            form = buildMalProgressForm(update),
        )
        if (response.status !in 200..299) throw MalApiException(response.status)
        return parseMalMyListStatus(response.body)
    }

    private suspend fun authorized(
        scope: MalAuthScope,
        method: HttpMethod,
        url: String,
        form: String? = null,
    ): MalRawResponse {
        var forceRefresh = false
        while (true) {
            val token = validAccessToken(scope, forceRefresh)
            val response = send(method, url, token, form)
            store.checkScope(scope)
            if (response.status != 401) return response
            val canRefresh = store.credentials()?.refreshToken != null
            if (!forceRefresh && canRefresh) {
                forceRefresh = true
                continue
            }
            store.clearAuth(scope, MalAuthError.AUTHORIZATION_REVOKED, token)
            throw MalAuthException(MalAuthError.AUTHORIZATION_REVOKED)
        }
    }

    /** Returns a usable access token, refreshing it first when it expires within [REFRESH_MARGIN_MS]. */
    private suspend fun validAccessToken(scope: MalAuthScope, forceRefresh: Boolean): String {
        store.checkScope(scope)
        val current = store.credentials() ?: throw MalAuthException(MalAuthError.AUTHORIZATION_REVOKED)
        if (!forceRefresh && !needsRefresh(current)) return current.accessToken
        return refreshLock.withLock {
            store.checkScope(scope)
            val latest = store.credentials() ?: throw MalAuthException(MalAuthError.AUTHORIZATION_REVOKED)
            // Another caller may already have refreshed while this one waited for the lock.
            if (latest.accessToken != current.accessToken && !needsRefresh(latest)) return@withLock latest.accessToken
            val refreshToken = latest.refreshToken
            if (refreshToken == null) {
                if (!needsRefresh(latest) || (latest.expiresAtEpochMs ?: Long.MAX_VALUE) > now()) {
                    return@withLock latest.accessToken
                }
                store.clearAuth(scope, MalAuthError.AUTHORIZATION_EXPIRED, latest.accessToken)
                throw MalAuthException(MalAuthError.AUTHORIZATION_EXPIRED)
            }
            val response = send(
                HttpMethod.Post,
                MalConfig.TOKEN_URL,
                token = null,
                form = buildMalRefreshForm(MalConfig.CLIENT_ID, refreshToken),
            )
            store.checkScope(scope)
            if (response.status == 400 || response.status == 401) {
                store.clearAuth(scope, MalAuthError.AUTHORIZATION_REVOKED, latest.accessToken)
                throw MalAuthException(MalAuthError.AUTHORIZATION_REVOKED)
            }
            if (response.status !in 200..299) throw MalApiException(response.status)
            val tokens = parseMalTokenResponse(response.body) ?: throw MalApiException(response.status, "Invalid token response")
            store.updateTokens(tokens, scope, refreshToken)
            store.credentials()?.accessToken ?: throw MalAuthException(MalAuthError.AUTHORIZATION_REVOKED)
        }
    }

    private fun needsRefresh(credentials: MalCredentials): Boolean =
        credentials.expiresAtEpochMs?.let { it - now() <= REFRESH_MARGIN_MS } == true

    private suspend fun send(method: HttpMethod, url: String, token: String?, form: String?): MalRawResponse =
        client.prepareRequest(url) {
            this.method = method
            header("Accept", "application/json")
            token?.let { header("Authorization", "Bearer $it") }
            form?.let { setBody(TextContent(it, ContentType.Application.FormUrlEncoded)) }
        }.execute { response ->
            MalRawResponse(
                status = response.status.value,
                body = readBoundedResponseBody(response.bodyAsChannel(), response.contentLength()),
            )
        }

    private class MalRawResponse(
        val status: Int,
        val body: String,
    ) {
        override fun toString(): String = "MalRawResponse(status=$status)"
    }

    companion object {
        const val REFRESH_MARGIN_MS = 60_000L
    }
}
