package com.nuvio.app.features.simkl

internal expect object SimklPlatformClock {
    fun nowEpochMs(): Long
}

internal expect object SimklPkceCrypto {
    fun secureRandomBytes(size: Int): ByteArray
    fun sha256(value: ByteArray): ByteArray
}

internal expect object SimklAuthStorage {
    fun loadMetadataPayload(): String?
    fun saveMetadataPayload(payload: String)
    fun loadAccessToken(): String?
    fun saveAccessToken(value: String?)
    fun loadRefreshToken(): String?
    fun saveRefreshToken(value: String?)
    /** Pending single-use secret: the PKCE verifier (browser flow) or the device_code (device flow). */
    fun loadCodeVerifier(): String?
    fun saveCodeVerifier(value: String?)
    fun removeProfile(profileId: Int)
}
