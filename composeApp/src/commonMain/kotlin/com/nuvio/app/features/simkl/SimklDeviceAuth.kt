package com.nuvio.app.features.simkl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SimklAuthenticationMethod(val storageValue: String) {
    BROWSER_REDIRECT("browser_redirect"),
    DEVICE_CODE("device_code"),
    ;

    companion object {
        fun fromStorageValue(value: String?): SimklAuthenticationMethod =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: BROWSER_REDIRECT
    }
}

internal const val SIMKL_PIN_VERIFICATION_URL = "https://simkl.com/pin"

internal const val SIMKL_DEFAULT_PIN_POLL_INTERVAL_SECONDS = 5
internal const val SIMKL_DEFAULT_PIN_EXPIRES_IN_SECONDS = 900

internal const val SIMKL_PIN_STATE_PREFIX = "pin:"

internal fun buildPendingPinState(userCode: String): String = "$SIMKL_PIN_STATE_PREFIX$userCode"

internal fun pendingPinUserCode(state: String?): String? =
    state?.takeIf { it.startsWith(SIMKL_PIN_STATE_PREFIX) }?.removePrefix(SIMKL_PIN_STATE_PREFIX)

/** `POST /oauth2/device` response (https://api.simkl.org/api-reference/oauth2-device). */
@Serializable
internal data class SimklDeviceAuthorizationResponse(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val interval: Int? = null,
) {
    override fun toString(): String = "SimklDeviceAuthorizationResponse(expiresIn=$expiresIn, interval=$interval)"
}

internal enum class SimklDevicePollOutcome {
    AUTHORIZED,
    PENDING,
    SLOW_DOWN,
    FAILED,
}

/** Maps a `/oauth2/token` device-grant error code to the polling action the docs prescribe. */
internal fun simklDevicePollOutcome(status: Int, errorCode: String?): SimklDevicePollOutcome = when {
    status in 200..299 -> SimklDevicePollOutcome.AUTHORIZED
    errorCode == "authorization_pending" -> SimklDevicePollOutcome.PENDING
    errorCode == "slow_down" -> SimklDevicePollOutcome.SLOW_DOWN
    status == 429 || status >= 500 -> SimklDevicePollOutcome.PENDING
    else -> SimklDevicePollOutcome.FAILED
}
