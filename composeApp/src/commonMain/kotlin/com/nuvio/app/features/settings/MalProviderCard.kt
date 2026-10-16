// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.mal.MalAuthError
import com.nuvio.app.features.mal.MalConfig
import com.nuvio.app.features.mal.MalTracker
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MalProviderCard(modifier: Modifier = Modifier) {
    val auth by remember { MalTracker.ensureLoaded(); MalTracker.authState }.collectAsStateWithLifecycle()
    TrackingProviderCard(
        brand = TrackingBrand.MAL,
        mode = when {
            auth.isAuthenticated -> TrackingConnectionCardMode.CONNECTED
            auth.awaitingApproval -> TrackingConnectionCardMode.AWAITING_APPROVAL
            else -> TrackingConnectionCardMode.DISCONNECTED
        },
        credentialsConfigured = MalTracker.hasRequiredCredentials(),
        isLoading = auth.isBusy,
        connectedLabel = stringResource(
            Res.string.mal_connected_as,
            auth.viewer?.name ?: stringResource(Res.string.mal_account_fallback),
        ),
        connectedDescription = stringResource(Res.string.mal_connected_description),
        signInDescription = stringResource(Res.string.mal_sign_in_description),
        finishSignInLabel = stringResource(Res.string.mal_finish_sign_in),
        approvalDescription = stringResource(Res.string.mal_approval_description),
        connectLabel = stringResource(Res.string.mal_connect),
        openLoginLabel = stringResource(Res.string.mal_open_login),
        disconnectLabel = stringResource(Res.string.mal_disconnect),
        missingCredentialsMessage = stringResource(Res.string.mal_missing_credentials),
        errorMessage = malAccountError(auth.error),
        websiteLabel = stringResource(Res.string.mal_visit),
        websiteUrl = MalConfig.WEBSITE_URL,
        onConnectRequested = { MalTracker.connect() },
        onResumeAuthorization = { MalTracker.pendingAuthorizationUrl() },
        onCancelAuthorization = { MalTracker.cancelAuthorization() },
        onDisconnect = { MalTracker.disconnect() },
        modifier = modifier,
    )
}

@Composable
private fun malAccountError(error: MalAuthError?): String? = when (error) {
    null, MalAuthError.MISSING_CLIENT_ID -> null
    MalAuthError.ACCESS_DENIED -> stringResource(Res.string.mal_auth_denied)
    MalAuthError.INVALID_CALLBACK -> stringResource(Res.string.mal_auth_invalid)
    MalAuthError.TOKEN_EXCHANGE_FAILED -> stringResource(Res.string.mal_sign_in_failed)
    MalAuthError.AUTHORIZATION_EXPIRED -> stringResource(Res.string.mal_auth_expired)
    MalAuthError.AUTHORIZATION_REVOKED -> stringResource(Res.string.mal_auth_revoked)
}
