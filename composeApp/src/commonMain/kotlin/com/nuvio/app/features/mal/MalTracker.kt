// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.mal

import com.nuvio.app.features.anilist.AniListTracker
import com.nuvio.app.features.player.skip.SkipIntroApi
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MyAnimeList account connection and automatic episode tracking (scrobble on finish).
 * Structured like [AniListTracker]; MAL is write-only here (no library, progress or ratings reads).
 */
object MalTracker : TrackingAuthProvider {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeProfile = MutableStateFlow(ProfileRepository.activeProfileId)
    internal val store = MalAuthStore(PlatformMalAuthPersistence, activeProfile.value)
    internal val api = MalApiClient(store)
    private val resolver = MalIdResolver(
        resolveAniList = { reference ->
            AniListTracker.resolver.resolve(reference, AniListTracker.sync.currentSnapshot())
        },
        aniListToMal = { mediaId -> aniListToMal(mediaId) },
        kitsuToMal = { kitsu -> SkipIntroApi.resolveKitsuToMal(kitsu.toString())?.myanimelist?.toLong() },
    )
    val writes = MalTrackingWrites(store, api, resolver)
    val authState: StateFlow<MalAuthState> = store.state
    private val authenticated = MutableStateFlow(store.state.value.isAuthenticated)
    override val isAuthenticated: StateFlow<Boolean> = authenticated.asStateFlow()
    override val accountGeneration: Long get() = store.scope().generation
    override val descriptor = TrackingProviderDescriptor(
        TrackingProviderId.MAL,
        "MyAnimeList",
        setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.WATCHED_WRITE,
            TrackingCapability.SCROBBLE,
        ),
    )

    init {
        coroutineScope.launch {
            store.state.collectLatest { state ->
                authenticated.value = state.scope.profileId == activeProfile.value && state.isAuthenticated
            }
        }
    }

    fun register() {
        if (TrackingProviderRegistry.authProvider(providerId) === this) return
        TrackingProviderRegistry.register(this)
        TrackingProviderRegistry.registerHistoryWriter(writes)
        TrackingProviderRegistry.registerScrobbler(writes)
    }

    fun hasRequiredCredentials(): Boolean = MalConfig.isConfigured

    fun connect(): String? {
        ensureLoaded()
        if (!hasRequiredCredentials()) return null
        if (store.state.value.isAuthenticated) return null
        val pending = MalPendingAuthorization(state = generateMalState(), verifier = generateMalCodeVerifier())
        if (!store.beginAuthorization(store.scope(), pending)) return null
        return authorizationUrl(pending)
    }

    fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        if (!hasRequiredCredentials()) return null
        val pending = store.pendingAuthorization()
        return if (store.state.value.awaitingApproval && pending != null) authorizationUrl(pending) else connect()
    }

    fun cancelAuthorization() {
        store.cancelAuthorization(store.scope())
    }

    fun disconnect() {
        store.clearAuth(store.scope())
        authenticated.value = false
    }

    override fun handleAuthCallback(url: String): Boolean {
        val callback = parseMalCallback(url) ?: return false
        ensureLoaded()
        val scope = store.scope()
        val pending = store.pendingAuthorization()
        if (pending == null) {
            // Nothing in flight, or the ten-minute window has passed: drop any stale state.
            store.cancelAuthorization(scope, if (callback.code != null) MalAuthError.AUTHORIZATION_EXPIRED else null)
            return true
        }
        if (callback.error != null) {
            store.cancelAuthorization(scope, MalAuthError.ACCESS_DENIED)
            return true
        }
        val code = callback.code
        if (callback.state != pending.state || code == null) {
            store.cancelAuthorization(scope, MalAuthError.INVALID_CALLBACK)
            return true
        }
        // The state and verifier are single-use: clear them before exchanging the code.
        if (!store.cancelAuthorization(scope)) return true
        store.setBusy(true, scope)
        coroutineScope.launch {
            try {
                val tokens = api.exchangeCode(code, pending.verifier)
                if (!store.authorize(tokens, scope)) return@launch
                val authorizedScope = store.scope()
                try {
                    store.saveViewer(api.viewer(authorizedScope), authorizedScope)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                store.cancelAuthorization(scope, MalAuthError.TOKEN_EXCHANGE_FAILED)
            } finally {
                store.setBusy(false, store.scope())
            }
        }
        return true
    }

    override fun ensureLoaded() {
        if (activeProfile.value != ProfileRepository.activeProfileId) onProfileChanged()
        store.refreshExpiry()
        authenticated.value = store.state.value.isAuthenticated
    }

    override fun onProfileChanged() {
        activeProfile.value = ProfileRepository.activeProfileId
        store.selectProfile(activeProfile.value)
        authenticated.value = store.state.value.isAuthenticated
    }

    override fun clearLocalState() {
        store.clearAllProfiles()
        authenticated.value = false
    }

    override fun removeStoredProfile(profileId: Int) {
        store.removeProfile(profileId)
    }

    private fun authorizationUrl(pending: MalPendingAuthorization): String =
        buildMalAuthorizeUrl(
            clientId = MalConfig.CLIENT_ID,
            verifier = pending.verifier,
            state = pending.state,
            redirectUri = MalConfig.REDIRECT_URI,
        )

    /** AniList media id to MAL id: AniList's cached mapping first, then AniList's `idMal`. */
    private suspend fun aniListToMal(mediaId: Long): Long? {
        AniListTracker.sync.currentSnapshot()?.let { snapshot ->
            snapshot.mappings[mediaId]?.mal?.let { return it }
            snapshot.entries.firstOrNull { it.mediaId == mediaId }?.media?.idMal?.let { return it }
        }
        return AniListTracker.api.mediaById(null, mediaId)?.first?.idMal
    }
}
