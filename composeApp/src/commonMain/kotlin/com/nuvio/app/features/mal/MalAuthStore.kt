// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.mal

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MalAuthPersistence {
    fun read(profileId: Int): String?
    fun write(profileId: Int, value: String?)
    fun clear()
}

internal expect object PlatformMalAuthPersistence : MalAuthPersistence {
    override fun read(profileId: Int): String?
    override fun write(profileId: Int, value: String?)
    override fun clear()
}

/** Per-profile MyAnimeList credentials, mirroring [com.nuvio.app.features.anilist.AniListAuthStore]. */
class MalAuthStore(
    private val persistence: MalAuthPersistence,
    initialProfileId: Int = 1,
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) {
    private val lock = SynchronizedObject()
    private var currentScope = MalAuthScope(initialProfileId, 0)
    private var stored = load(initialProfileId)
    private var busy = false
    private var lastError: MalAuthError? = null
    private val mutableState = MutableStateFlow(stateFor())

    val state: StateFlow<MalAuthState> = mutableState.asStateFlow()

    fun scope(): MalAuthScope = synchronized(lock) { currentScope }

    fun isCurrent(scope: MalAuthScope): Boolean = synchronized(lock) { currentScope == scope }

    fun checkScope(scope: MalAuthScope) {
        if (!isCurrent(scope)) throw CancellationException("MyAnimeList account changed")
    }

    internal fun credentials(): MalCredentials? = synchronized(lock) {
        val token = stored.accessToken?.takeIf(String::isNotBlank) ?: return@synchronized null
        if (!isAuthenticated(stored)) return@synchronized null
        MalCredentials(token, stored.refreshToken?.takeIf(String::isNotBlank), stored.expiresAtEpochMs)
    }

    /** The pending state and verifier, or null when none was started or it is older than [PENDING_WINDOW_MS]. */
    internal fun pendingAuthorization(): MalPendingAuthorization? = synchronized(lock) {
        val state = stored.pendingState?.takeIf(String::isNotBlank) ?: return@synchronized null
        val verifier = stored.pendingVerifier?.takeIf(String::isNotBlank) ?: return@synchronized null
        if (!isPendingFresh(stored)) return@synchronized null
        MalPendingAuthorization(state, verifier)
    }

    fun selectProfile(profileId: Int) = synchronized(lock) {
        if (currentScope.profileId == profileId) return@synchronized
        stored = load(profileId)
        currentScope = MalAuthScope(profileId, currentScope.generation + 1)
        busy = false
        lastError = null
        publish()
    }

    internal fun beginAuthorization(scope: MalAuthScope, pending: MalPendingAuthorization): Boolean =
        mutate(scope, error = null) {
            it.copy(pendingState = pending.state, pendingVerifier = pending.verifier, pendingSinceEpochMs = now())
        }

    fun cancelAuthorization(scope: MalAuthScope = scope(), error: MalAuthError? = null): Boolean =
        mutate(scope, error = error) { it.copy(pendingState = null, pendingVerifier = null, pendingSinceEpochMs = null) }

    internal fun authorize(tokens: MalTokenResponse, scope: MalAuthScope): Boolean {
        val token = tokens.accessToken?.takeIf(String::isNotBlank) ?: return false
        return mutate(scope, advanceGeneration = true, error = null) {
            MalStoredAuth(
                accessToken = token,
                refreshToken = tokens.refreshToken?.takeIf(String::isNotBlank),
                expiresAtEpochMs = expiryFor(tokens.expiresIn),
            )
        }
    }

    /** Stores refreshed tokens without changing the account scope, so in-flight writes stay valid. */
    internal fun updateTokens(tokens: MalTokenResponse, scope: MalAuthScope, expectedRefreshToken: String): Boolean =
        synchronized(lock) {
            if (stored.refreshToken != expectedRefreshToken) return@synchronized false
            val token = tokens.accessToken?.takeIf(String::isNotBlank) ?: return@synchronized false
            mutate(scope, error = null) {
                it.copy(
                    accessToken = token,
                    refreshToken = tokens.refreshToken?.takeIf(String::isNotBlank) ?: it.refreshToken,
                    expiresAtEpochMs = expiryFor(tokens.expiresIn),
                )
            }
        }

    fun saveViewer(viewer: MalViewer, scope: MalAuthScope): Boolean =
        mutate(scope, error = lastError) { it.copy(viewer = viewer) }

    fun setBusy(value: Boolean, scope: MalAuthScope) = synchronized(lock) {
        if (currentScope != scope) return@synchronized
        busy = value
        publish()
    }

    fun clearAuth(
        scope: MalAuthScope = scope(),
        error: MalAuthError? = null,
        expectedAccessToken: String? = null,
    ): Boolean = synchronized(lock) {
        if (expectedAccessToken != null && stored.accessToken != expectedAccessToken) return@synchronized false
        mutate(scope, advanceGeneration = true, error = error) { MalStoredAuth() }
    }

    fun removeProfile(profileId: Int) = synchronized(lock) {
        persistence.write(profileId, null)
        if (currentScope.profileId == profileId) {
            currentScope = currentScope.copy(generation = currentScope.generation + 1)
            stored = MalStoredAuth()
            busy = false
            lastError = null
            publish()
        }
    }

    fun clearAllProfiles() = synchronized(lock) {
        persistence.clear()
        currentScope = currentScope.copy(generation = currentScope.generation + 1)
        stored = MalStoredAuth()
        busy = false
        lastError = null
        publish()
    }

    fun refreshExpiry() = synchronized(lock) {
        if (stored.accessToken != null && !isAuthenticated(stored)) {
            persistence.write(currentScope.profileId, malJson.encodeToString(MalStoredAuth.serializer(), MalStoredAuth()))
            stored = MalStoredAuth()
            currentScope = currentScope.copy(generation = currentScope.generation + 1)
            lastError = MalAuthError.AUTHORIZATION_EXPIRED
            publish()
        } else if (stored.pendingState != null && !isPendingFresh(stored)) {
            val value = stored.copy(pendingState = null, pendingVerifier = null, pendingSinceEpochMs = null)
            persistence.write(currentScope.profileId, malJson.encodeToString(MalStoredAuth.serializer(), value))
            stored = value
            publish()
        }
    }

    private fun mutate(
        scope: MalAuthScope,
        advanceGeneration: Boolean = false,
        error: MalAuthError? = null,
        transform: (MalStoredAuth) -> MalStoredAuth,
    ): Boolean = synchronized(lock) {
        if (currentScope != scope) return@synchronized false
        val value = transform(stored)
        persistence.write(scope.profileId, malJson.encodeToString(MalStoredAuth.serializer(), value))
        stored = value
        if (advanceGeneration) currentScope = currentScope.copy(generation = currentScope.generation + 1)
        lastError = error
        publish()
        true
    }

    private fun load(profileId: Int): MalStoredAuth = persistence.read(profileId)?.let { value ->
        runCatching { malJson.decodeFromString(MalStoredAuth.serializer(), value) }.getOrNull()
    }?.takeIf { value -> value.accessToken == null || value.accessToken.isNotBlank() } ?: MalStoredAuth()

    private fun expiryFor(expiresInSeconds: Long?): Long? =
        expiresInSeconds?.takeIf { it > 0L }?.let { now() + it * 1_000L }

    /** An expired access token still counts while a refresh token can renew it. */
    private fun isAuthenticated(value: MalStoredAuth): Boolean =
        !value.accessToken.isNullOrBlank() && (
            value.expiresAtEpochMs == null ||
                value.expiresAtEpochMs > now() ||
                !value.refreshToken.isNullOrBlank()
            )

    private fun isPendingFresh(value: MalStoredAuth): Boolean =
        value.pendingSinceEpochMs?.let { now() - it in 0..PENDING_WINDOW_MS } == true

    private fun publish() {
        mutableState.value = stateFor()
    }

    private fun stateFor() = MalAuthState(
        scope = currentScope,
        isAuthenticated = isAuthenticated(stored),
        viewer = stored.viewer,
        awaitingApproval = !isAuthenticated(stored) && isPendingFresh(stored) && stored.pendingState != null,
        isBusy = busy,
        error = lastError,
    )

    companion object {
        /** OAuth state and PKCE verifier are only honoured for ten minutes. */
        const val PENDING_WINDOW_MS = 10L * 60L * 1_000L
    }
}
