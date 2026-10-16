package com.nuvio.app.core.auth

import com.nuvio.app.core.network.ServerConfiguration
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException

/** Avix: why a sign-in attempt failed, so the UI can tell configuration, network and credentials apart. */
enum class AuthFailureKind {
    /** This build has no Nuvio backend URL / publishable key (CI secrets not set). */
    NotConfigured,
    /** The server could not be reached (offline, DNS, timeout, TLS). */
    Network,
    /** Wrong email or password. */
    InvalidCredentials,
    EmailNotConfirmed,
    RateLimited,
    /** The server answered with a 5xx error. */
    Server,
    Unknown,
}

/** True when the configuration can reach a backend: an https URL and a publishable key. */
val ServerConfiguration.isUsable: Boolean
    get() = backendUrl.isNotBlank() && publishableKey.isNotBlank() &&
        (backendUrl.startsWith("https://", ignoreCase = true) || isCustom)

internal fun classifyAuthFailure(error: Throwable): AuthFailureKind {
    error.findCauseOf<AuthRestException>()?.let { auth ->
        when (auth.errorCode) {
            AuthErrorCode.InvalidCredentials, AuthErrorCode.UserNotFound -> return AuthFailureKind.InvalidCredentials
            AuthErrorCode.EmailNotConfirmed -> return AuthFailureKind.EmailNotConfirmed
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> return AuthFailureKind.RateLimited
            else -> Unit
        }
        if (auth.error.equals("invalid_grant", ignoreCase = true)) return AuthFailureKind.InvalidCredentials
    }
    error.findCauseOf<RestException>()?.let { rest ->
        val text = "${rest.error} ${rest.description.orEmpty()}".lowercase()
        return when {
            // A wrong/missing publishable key is a build configuration problem, not the user's.
            rest.statusCode == 401 && ("api key" in text || "apikey" in text) -> AuthFailureKind.NotConfigured
            rest.statusCode == 429 -> AuthFailureKind.RateLimited
            rest.statusCode >= 500 -> AuthFailureKind.Server
            rest is AuthRestException && (rest.statusCode == 400 || rest.statusCode == 401) ->
                AuthFailureKind.InvalidCredentials
            else -> AuthFailureKind.Unknown
        }
    }
    if (error.findCauseOf<HttpRequestException>() != null || error.looksLikeNetworkFailure()) {
        return AuthFailureKind.Network
    }
    return AuthFailureKind.Unknown
}

private val networkErrorNames = listOf(
    "Timeout", "UnknownHost", "UnresolvedAddress", "ConnectException", "NoRouteToHost",
    "SocketException", "SSLException", "SSLHandshake", "IOException", "EOFException", "NetworkOnMainThread",
)

private fun Throwable.looksLikeNetworkFailure(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 8) {
        val name = current::class.simpleName.orEmpty()
        if (networkErrorNames.any { name.contains(it, ignoreCase = true) }) return true
        current = current.cause
        depth++
    }
    return false
}

private inline fun <reified T : Throwable> Throwable.findCauseOf(): T? {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 8) {
        if (current is T) return current
        current = current.cause
        depth++
    }
    return null
}
