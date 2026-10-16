// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.mal

import com.nuvio.app.features.simkl.SimklPkceCrypto
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter

/** RFC 7636 "unreserved" characters, the alphabet MyAnimeList accepts for a PKCE verifier. */
internal const val MAL_VERIFIER_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
internal const val MAL_VERIFIER_LENGTH = 96
internal const val MAL_STATE_LENGTH = 32

/**
 * Generates a random string over [MAL_VERIFIER_ALPHABET]. Bytes are drawn from [randomBytes]
 * (a secure source by default) and mapped with rejection sampling so every character is equally likely.
 */
internal fun generateMalRandomToken(
    length: Int,
    randomBytes: (Int) -> ByteArray = SimklPkceCrypto::secureRandomBytes,
): String {
    require(length > 0)
    val targetLength = length
    val alphabetSize = MAL_VERIFIER_ALPHABET.length
    val limit = 256 - (256 % alphabetSize)
    val builder = StringBuilder(targetLength)
    while (builder.length < targetLength) {
        val chunk = randomBytes(targetLength * 2)
        for (byte in chunk) {
            val value = byte.toInt() and 0xff
            if (value >= limit) continue
            builder.append(MAL_VERIFIER_ALPHABET[value % alphabetSize])
            if (builder.length == targetLength) break
        }
    }
    return builder.toString()
}

internal fun generateMalCodeVerifier(
    randomBytes: (Int) -> ByteArray = SimklPkceCrypto::secureRandomBytes,
): String = generateMalRandomToken(MAL_VERIFIER_LENGTH, randomBytes)

internal fun generateMalState(
    randomBytes: (Int) -> ByteArray = SimklPkceCrypto::secureRandomBytes,
): String = generateMalRandomToken(MAL_STATE_LENGTH, randomBytes)

/**
 * MyAnimeList only supports the `plain` PKCE method, so the challenge is the verifier itself.
 */
internal fun buildMalAuthorizeUrl(
    clientId: String,
    verifier: String,
    state: String,
    redirectUri: String,
    authorizeUrl: String = MalConfig.AUTHORIZE_URL,
): String = buildString {
    append(authorizeUrl)
    append("?response_type=code")
    append("&client_id=").append(clientId.encodeURLParameter())
    append("&code_challenge=").append(verifier.encodeURLParameter())
    append("&code_challenge_method=plain")
    append("&state=").append(state.encodeURLParameter())
    append("&redirect_uri=").append(redirectUri.encodeURLParameter())
}

internal data class MalCallback(
    val code: String?,
    val state: String?,
    val error: String?,
) {
    override fun toString(): String = "MalCallback(error=$error)"
}

/** Returns the callback parameters when [url] targets [redirectUri], or null for any other URL. */
internal fun parseMalCallback(url: String, redirectUri: String = MalConfig.REDIRECT_URI): MalCallback? {
    val trimmed = url.trim()
    if (!trimmed.startsWith(redirectUri, ignoreCase = true)) return null
    val remainder = trimmed.substring(redirectUri.length)
    if (remainder.isNotEmpty() && remainder.first() !in setOf('#', '?', '/')) return null
    val parts = buildList {
        remainder.substringAfter('?', "").substringBefore('#').takeIf(String::isNotBlank)?.let(::add)
        remainder.substringAfter('#', "").takeIf(String::isNotBlank)?.let(::add)
    }
    val parameters = parts.flatMap { it.split('&') }
        .mapNotNull { pair ->
            val key = pair.substringBefore('=').trim()
            if (key.isBlank()) return@mapNotNull null
            val value = pair.substringAfter('=', "")
            key to runCatching { value.decodeURLQueryComponent(plusIsSpace = true) }.getOrDefault(value)
        }
        .toMap()
    return MalCallback(
        code = parameters["code"]?.takeIf(String::isNotBlank),
        state = parameters["state"]?.takeIf(String::isNotBlank),
        error = parameters["error"]?.takeIf(String::isNotBlank),
    )
}
