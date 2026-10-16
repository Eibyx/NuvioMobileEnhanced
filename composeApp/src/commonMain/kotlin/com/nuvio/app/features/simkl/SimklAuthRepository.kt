package com.nuvio.app.features.simkl

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SimklAuthRepository : TrackingAuthProvider {
    private val log = Logger.withTag("SimklAuth")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val authorizationMutex = Mutex()
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(SimklAuthUiState())
    val uiState: StateFlow<SimklAuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.SIMKL,
        displayName = "Simkl",
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
            TrackingCapability.WATCHED_READ,
            TrackingCapability.WATCHED_WRITE,
            TrackingCapability.PROGRESS_READ,
            TrackingCapability.PROGRESS_WRITE,
            TrackingCapability.SCROBBLE,
            TrackingCapability.RATINGS,
        ),
    )

    private var hasLoaded = false
    private var storedState = SimklStoredAuthState()
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var authenticationMethod = SimklAuthenticationMethod.BROWSER_REDIRECT
    private var pinPollingJob: Job? = null

    init {
        TrackingProviderRegistry.register(this)
    }

    override fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    override fun onProfileChanged() {
        loadFromDisk()
    }

    override fun clearLocalState() {
        hasLoaded = false
        pinPollingJob?.cancel()
        pinPollingJob = null
        storedState = SimklStoredAuthState()
        accessToken = null
        refreshToken = null
        authenticationMethod = SimklAuthenticationMethod.BROWSER_REDIRECT
        publish()
    }

    fun selectedAuthenticationMethod(): SimklAuthenticationMethod {
        ensureLoaded()
        return authenticationMethod
    }

    fun setAuthenticationMethod(method: SimklAuthenticationMethod) {
        ensureLoaded()
        if (authenticationMethod == method) return
        pinPollingJob?.cancel()
        pinPollingJob = null
        authenticationMethod = method
        storedState = storedState.copy(authenticationMethod = method.storageValue)
        clearPendingAuthorization()
        persistMetadata()
        publish(isLoading = false, error = null)
    }

    fun pendingDeviceUserCode(): String? {
        ensureLoaded()
        return pendingPinUserCode(storedState.pendingAuthorizationState)
    }

    override fun removeStoredProfile(profileId: Int) {
        SimklAuthStorage.removeProfile(profileId)
    }

    fun snapshot(): SimklAuthUiState {
        ensureLoaded()
        return uiState.value
    }

    fun hasRequiredCredentials(): Boolean = SimklConfig.CLIENT_ID.isNotBlank()

    fun onConnectRequested(): String? {
        ensureLoaded()
        if (!hasRequiredCredentials()) {
            publish(error = SimklAuthError.MISSING_CLIENT_ID)
            return null
        }

        pinPollingJob?.cancel()
        pinPollingJob = null
        clearPendingAuthorization()

        if (authenticationMethod == SimklAuthenticationMethod.DEVICE_CODE) {
            publish(isLoading = true, error = null)
            scope.launch { startDeviceAuthorization() }
            return null
        }

        val material = generateSimklPkceMaterial()
        SimklAuthStorage.saveCodeVerifier(material.verifier)
        storedState = storedState.copy(
            pendingAuthorizationState = material.state,
            pendingAuthorizationStartedAtEpochMs = SimklPlatformClock.nowEpochMs(),
        )
        persistMetadata()
        publish(error = null)
        return authorizationUrl(material)
    }

    /** AUTH V2 device flow: `POST /oauth2/device`, then poll `POST /oauth2/token` (device_code grant). */
    private suspend fun startDeviceAuthorization() {
        val response = try {
            postOAuthForm(SIMKL_OAUTH2_DEVICE_URL, simklDeviceAuthorizationForm(SimklConfig.CLIENT_ID))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Simkl device authorization request failed: ${error.message}" }
            failPinAuthorization()
            return
        }

        val parsed = response.takeIf { it.status in 200..299 }
            ?.let { runCatching { json.decodeFromString<SimklDeviceAuthorizationResponse>(it.body) }.getOrNull() }
            ?.takeIf { it.userCode.isNotBlank() && it.deviceCode.isNotBlank() }
        if (parsed == null) {
            log.w { "Simkl device authorization response was not usable (HTTP ${response.status})" }
            failPinAuthorization()
            return
        }

        // The device_code is a credential: keep it in the encrypted single-use slot, never in metadata.
        SimklAuthStorage.saveCodeVerifier(parsed.deviceCode)
        storedState = storedState.copy(
            pendingAuthorizationState = buildPendingPinState(parsed.userCode),
            pendingAuthorizationStartedAtEpochMs = SimklPlatformClock.nowEpochMs(),
        )
        persistMetadata()
        publish(isLoading = false, error = null)
        startPinPolling(
            userCode = parsed.userCode,
            intervalSeconds = parsed.interval,
            expiresInSeconds = parsed.expiresIn,
        )
    }

    private fun failPinAuthorization() {
        clearPendingAuthorization()
        persistMetadata()
        publish(isLoading = false, error = SimklAuthError.TOKEN_EXCHANGE_FAILED)
    }

    private fun startPinPolling(userCode: String, intervalSeconds: Int?, expiresInSeconds: Int?) {
        pinPollingJob?.cancel()
        pinPollingJob = scope.launch {
            var pollSeconds = (intervalSeconds ?: SIMKL_DEFAULT_PIN_POLL_INTERVAL_SECONDS)
                .coerceAtLeast(SIMKL_DEFAULT_PIN_POLL_INTERVAL_SECONDS)
            val expiresInMillis = (expiresInSeconds ?: SIMKL_DEFAULT_PIN_EXPIRES_IN_SECONDS)
                .coerceAtLeast(pollSeconds) * 1_000L
            val startedAt = SimklPlatformClock.nowEpochMs()

            while (
                pendingPinUserCode(storedState.pendingAuthorizationState) == userCode &&
                SimklPlatformClock.nowEpochMs() - startedAt < expiresInMillis
            ) {
                delay(pollSeconds * 1_000L)
                when (pollDeviceToken(userCode)) {
                    SimklDevicePollOutcome.AUTHORIZED -> return@launch
                    SimklDevicePollOutcome.PENDING -> Unit
                    SimklDevicePollOutcome.SLOW_DOWN -> pollSeconds += 5
                    SimklDevicePollOutcome.FAILED -> {
                        if (pendingPinUserCode(storedState.pendingAuthorizationState) == userCode) {
                            failPinAuthorization()
                        }
                        return@launch
                    }
                }
            }

            // A declined approval never returns access_denied: it stays pending until this timeout.
            if (pendingPinUserCode(storedState.pendingAuthorizationState) == userCode) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.AUTHORIZATION_EXPIRED)
            }
        }
    }

    private suspend fun pollDeviceToken(userCode: String): SimklDevicePollOutcome {
        if (pendingPinUserCode(storedState.pendingAuthorizationState) != userCode) {
            return SimklDevicePollOutcome.FAILED
        }
        val deviceCode = SimklAuthStorage.loadCodeVerifier()?.takeIf(String::isNotBlank)
            ?: return SimklDevicePollOutcome.FAILED
        val response = try {
            postOAuthForm(SIMKL_OAUTH2_TOKEN_URL, simklDeviceTokenForm(SimklConfig.CLIENT_ID, deviceCode))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.d { "Simkl device token poll failed: ${error.message}" }
            return SimklDevicePollOutcome.PENDING
        }

        val outcome = simklDevicePollOutcome(response.status, response.oauthErrorCode())
        if (outcome != SimklDevicePollOutcome.AUTHORIZED) return outcome
        val token = parseTokenResponse(response.body) ?: return SimklDevicePollOutcome.FAILED
        if (pendingPinUserCode(storedState.pendingAuthorizationState) != userCode) {
            return SimklDevicePollOutcome.FAILED
        }
        applyTokens(token)
        return SimklDevicePollOutcome.AUTHORIZED
    }

    fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        val state = storedState.pendingAuthorizationState?.takeIf(String::isNotBlank) ?: return null
        if (pendingPinUserCode(state) != null) return SIMKL_PIN_VERIFICATION_URL
        val verifier = SimklAuthStorage.loadCodeVerifier()?.takeIf(String::isNotBlank) ?: run {
            clearPendingAuthorization()
            persistMetadata()
            publish(error = SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        if (isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = SimklPlatformClock.nowEpochMs(),
            )
        ) {
            clearPendingAuthorization()
            persistMetadata()
            publish(error = SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return authorizationUrl(
            SimklPkceMaterial(
                verifier = verifier,
                challenge = SimklPkceCrypto.sha256(verifier.encodeToByteArray()).base64UrlWithoutPadding(),
                state = state,
            ),
        )
    }

    fun onCancelAuthorization() {
        ensureLoaded()
        pinPollingJob?.cancel()
        pinPollingJob = null
        clearPendingAuthorization()
        persistMetadata()
        publish(isLoading = false, error = null)
    }

    override fun handleAuthCallback(url: String): Boolean {
        ensureLoaded()
        if (pendingPinUserCode(storedState.pendingAuthorizationState) != null) return false
        return when (val callback = parseSimklAuthCallback(url, SimklConfig.REDIRECT_URI)) {
            SimklAuthCallback.NotSimkl -> false
            SimklAuthCallback.Invalid -> {
                clearPendingAuthorization()
                persistMetadata()
                publish(error = SimklAuthError.INVALID_CALLBACK)
                true
            }
            is SimklAuthCallback.AuthorizationCode -> {
                scope.launch { completeAuthorization(callback) }
                true
            }
        }
    }

    fun onDisconnectRequested() {
        ensureLoaded()
        // "Revoking either token revokes both" (AUTH V2): revoke best effort, then clear locally.
        val tokenToRevoke = refreshToken?.takeIf(String::isNotBlank) ?: accessToken?.takeIf(String::isNotBlank)
        if (tokenToRevoke != null && hasRequiredCredentials()) {
            scope.launch {
                try {
                    postOAuthForm(SIMKL_OAUTH2_REVOKE_URL, simklRevokeForm(SimklConfig.CLIENT_ID, tokenToRevoke))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    log.w { "Simkl token revocation failed: ${error.message}" }
                }
            }
        }
        accessToken = null
        refreshToken = null
        SimklAuthStorage.saveAccessToken(null)
        SimklAuthStorage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState()
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(error = null)
    }

    internal suspend fun authorizedAccessToken(): String? {
        ensureLoaded()
        val token = accessToken?.takeIf(String::isNotBlank) ?: return null
        if (!isAccessTokenExpiring(storedState.tokenExpiresAtEpochMs)) return token
        if (refreshToken.isNullOrBlank()) {
            invalidateCredentials(SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return refreshAccessToken(token)
    }

    /** AUTH V2 access tokens last 7 days; the (non-rotating, 180-day sliding) refresh token renews them. */
    private suspend fun refreshAccessToken(expiringToken: String): String? = refreshMutex.withLock {
        val current = accessToken?.takeIf(String::isNotBlank) ?: return@withLock null
        // Another caller refreshed while this one waited for the lock.
        if (current != expiringToken && !isAccessTokenExpiring(storedState.tokenExpiresAtEpochMs)) {
            return@withLock current
        }
        val currentRefreshToken = refreshToken?.takeIf(String::isNotBlank) ?: run {
            invalidateCredentials(SimklAuthError.AUTHORIZATION_EXPIRED)
            return@withLock null
        }
        val response = try {
            postOAuthForm(SIMKL_OAUTH2_TOKEN_URL, simklRefreshTokenForm(SimklConfig.CLIENT_ID, currentRefreshToken))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Simkl token refresh transport failure: ${error.message}" }
            return@withLock current.takeUnless { isAccessTokenExpired(storedState.tokenExpiresAtEpochMs) }
        }
        if (refreshToken != currentRefreshToken) return@withLock accessToken
        if (response.status == 400 || response.status == 401) {
            log.w { "Simkl rejected the refresh token (HTTP ${response.status}); clearing credentials" }
            invalidateCredentials(SimklAuthError.AUTHORIZATION_EXPIRED)
            return@withLock null
        }
        val parsed = response.takeIf { it.status in 200..299 }?.let { parseTokenResponse(it.body) }
        if (parsed == null) {
            log.w { "Simkl token refresh failed with HTTP ${response.status}" }
            return@withLock current.takeUnless { isAccessTokenExpired(storedState.tokenExpiresAtEpochMs) }
        }
        storeTokens(parsed)
        persistMetadata()
        publish(error = null)
        parsed.accessToken
    }

    internal fun onUnauthorizedResponse() {
        invalidateCredentials(SimklAuthError.AUTHORIZATION_REVOKED)
    }

    suspend fun refreshUserSettings(): String? {
        authorizedAccessToken() ?: return null
        return if (fetchAndStoreUserSettings()) storedState.username else null
    }

    internal suspend fun synchronizeUserSettings(activityWatermark: String?) {
        authorizedAccessToken() ?: return
        when (simklSettingsRefreshAction(storedState, activityWatermark)) {
            SimklSettingsRefreshAction.NONE -> Unit
            SimklSettingsRefreshAction.RECORD_WATERMARK -> {
                storedState = storedState.copy(settingsActivityWatermark = activityWatermark)
                persistMetadata()
            }
            SimklSettingsRefreshAction.FETCH -> {
                fetchAndStoreUserSettings(activityWatermark)
            }
        }
    }

    private suspend fun completeAuthorization(callback: SimklAuthCallback.AuthorizationCode) =
        authorizationMutex.withLock {
            publish(isLoading = true, error = null)
            val expectedState = storedState.pendingAuthorizationState
            val verifier = SimklAuthStorage.loadCodeVerifier()
            val isExpired = isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = SimklPlatformClock.nowEpochMs(),
            )
            if (expectedState.isNullOrBlank() || verifier.isNullOrBlank() || isExpired) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.AUTHORIZATION_EXPIRED)
                return@withLock
            }
            if (!constantTimeEquals(callback.state, expectedState)) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INVALID_CALLBACK_STATE)
                return@withLock
            }

            // State and verifier are single-use; the code is consumed even by a failed exchange.
            SimklAuthStorage.saveCodeVerifier(null)
            val response = try {
                postOAuthForm(
                    SIMKL_OAUTH2_TOKEN_URL,
                    simklAuthorizationCodeForm(
                        clientId = SimklConfig.CLIENT_ID,
                        code = callback.code,
                        redirectUri = SimklConfig.REDIRECT_URI,
                        codeVerifier = verifier,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w { "Simkl token exchange failed: ${error.message}" }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.TOKEN_EXCHANGE_FAILED)
                return@withLock
            }
            if (response.status !in 200..299) {
                log.w { "Simkl token exchange failed with HTTP ${response.status} (${response.oauthErrorCode()})" }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.TOKEN_EXCHANGE_FAILED)
                return@withLock
            }
            val token = parseTokenResponse(response.body)
            if (token == null) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INVALID_TOKEN_RESPONSE)
                return@withLock
            }

            applyTokens(token)
        }

    private fun storeTokens(token: SimklTokenResponse) {
        accessToken = token.accessToken
        SimklAuthStorage.saveAccessToken(token.accessToken)
        // Refresh is non-rotating: keep the current refresh token when none is returned.
        token.refreshToken?.takeIf(String::isNotBlank)?.let { value ->
            refreshToken = value
            SimklAuthStorage.saveRefreshToken(value)
        }
        if (token.scope != null && "media:write" !in token.scope.split(' ')) {
            log.w { "Simkl granted a read-only scope; watch history writes will be rejected" }
        }
        storedState = storedState.copy(
            tokenExpiresAtEpochMs = token.expiresIn
                ?.takeIf { seconds -> seconds > 0L }
                ?.let { seconds -> SimklPlatformClock.nowEpochMs() + seconds * 1_000L },
        )
    }

    private suspend fun applyTokens(token: SimklTokenResponse) {
        refreshToken = null
        SimklAuthStorage.saveRefreshToken(null)
        storeTokens(token)
        clearPendingAuthorization()
        persistMetadata()
        publish(isLoading = false, error = null)
        fetchAndStoreUserSettings()
        SimklSyncRepository.refreshAsync(
            intent = TrackingRefreshIntent.INVALIDATED,
            origin = SimklRefreshOrigin.AUTHORIZATION,
        )
    }

    private suspend fun fetchAndStoreUserSettings(activityWatermark: String? = null): Boolean {
        val response = try {
            SimklApi.client.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = "/users/settings",
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Failed to fetch Simkl user settings: ${error.message}" }
            return false
        }
        val settings = runCatching { json.decodeFromString<SimklUserSettingsResponse>(response.body) }
            .getOrNull() ?: return false
        storedState = storedState.copy(
            username = settings.user?.name,
            accountId = settings.account?.id,
            hasFetchedUserSettings = true,
            settingsActivityWatermark = activityWatermark ?: storedState.settingsActivityWatermark,
        )
        persistMetadata()
        publish(error = null)
        return true
    }

    private fun loadFromDisk() {
        hasLoaded = true
        storedState = SimklAuthStorage.loadMetadataPayload()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { payload ->
                runCatching { json.decodeFromString<SimklStoredAuthState>(payload) }
                    .onFailure { error -> log.w { "Failed to parse Simkl auth metadata: ${error.message}" } }
                    .getOrNull()
            }
            ?: SimklStoredAuthState()
        authenticationMethod = SimklAuthenticationMethod.fromStorageValue(storedState.authenticationMethod)
        accessToken = SimklAuthStorage.loadAccessToken()?.takeIf(String::isNotBlank)
        refreshToken = SimklAuthStorage.loadRefreshToken()?.takeIf(String::isNotBlank)
        // An expiring access token is kept while a refresh token can renew it on first use.
        if (accessToken != null && refreshToken == null && isAccessTokenExpiring(storedState.tokenExpiresAtEpochMs)) {
            accessToken = null
            SimklAuthStorage.saveAccessToken(null)
            storedState = SimklStoredAuthState()
            persistMetadata()
        }
        if (storedState.hasPendingAuthorization && isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = SimklPlatformClock.nowEpochMs(),
            )
        ) {
            clearPendingAuthorization()
            persistMetadata()
        }
        publish(error = null)
    }

    private fun invalidateCredentials(error: SimklAuthError) {
        accessToken = null
        refreshToken = null
        SimklAuthStorage.saveAccessToken(null)
        SimklAuthStorage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState()
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(isLoading = false, error = error)
    }

    private fun clearPendingAuthorization() {
        SimklAuthStorage.saveCodeVerifier(null)
        storedState = storedState.copy(
            pendingAuthorizationState = null,
            pendingAuthorizationStartedAtEpochMs = null,
        )
    }

    private fun persistMetadata() {
        SimklAuthStorage.saveMetadataPayload(json.encodeToString(storedState))
    }

    private fun publish(
        isLoading: Boolean = _uiState.value.isLoading,
        error: SimklAuthError? = _uiState.value.error,
    ) {
        val authenticated = !accessToken.isNullOrBlank()
        _isAuthenticated.value = authenticated
        _uiState.value = SimklAuthUiState(
            mode = when {
                authenticated -> SimklConnectionMode.CONNECTED
                storedState.hasPendingAuthorization -> SimklConnectionMode.AWAITING_APPROVAL
                else -> SimklConnectionMode.DISCONNECTED
            },
            credentialsConfigured = hasRequiredCredentials(),
            isLoading = isLoading,
            username = storedState.username,
            accountId = storedState.accountId,
            tokenExpiresAtEpochMs = storedState.tokenExpiresAtEpochMs,
            pendingAuthorizationStartedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
            error = error,
        )
    }

    private fun authorizationUrl(material: SimklPkceMaterial): String =
        buildSimklAuthorizationUrl(
            clientId = SimklConfig.CLIENT_ID,
            redirectUri = SimklConfig.REDIRECT_URI,
            appName = SimklConfig.APP_NAME,
            appVersion = simklAppVersion,
            material = material,
        )

    private fun isAccessTokenExpiring(expiresAtEpochMs: Long?): Boolean =
        expiresAtEpochMs != null && SimklPlatformClock.nowEpochMs() >= expiresAtEpochMs - TOKEN_EXPIRY_SKEW_MS

    private fun isAccessTokenExpired(expiresAtEpochMs: Long?): Boolean =
        expiresAtEpochMs != null && SimklPlatformClock.nowEpochMs() >= expiresAtEpochMs

    private fun parseTokenResponse(body: String): SimklTokenResponse? =
        runCatching { json.decodeFromString<SimklTokenResponse>(body) }
            .getOrNull()
            ?.takeIf { it.accessToken.isNotBlank() && (it.tokenType == null || it.tokenType.equals("Bearer", ignoreCase = true)) }

    private suspend fun postOAuthForm(url: String, form: String): RawHttpResponse =
        httpRequestRaw(
            method = "POST",
            url = url,
            headers = simklOAuthFormHeaders(),
            body = form,
        )

    private fun RawHttpResponse.oauthErrorCode(): String? =
        runCatching { json.decodeFromString<SimklOAuthErrorResponse>(body).error }
            .getOrNull()
            ?.takeIf { it.matches(Regex("[a-z_]{1,64}")) }

    private const val TOKEN_EXPIRY_SKEW_MS = 60_000L
}

@Serializable
internal data class SimklTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
) {
    override fun toString(): String = "SimklTokenResponse(expiresIn=$expiresIn, scope=$scope)"
}

@Serializable
private data class SimklOAuthErrorResponse(
    val error: String? = null,
)

@Serializable
private data class SimklUserSettingsResponse(
    val user: SimklUser? = null,
    val account: SimklAccount? = null,
)

@Serializable
private data class SimklUser(
    val name: String? = null,
)

@Serializable
private data class SimklAccount(
    val id: Long? = null,
)
