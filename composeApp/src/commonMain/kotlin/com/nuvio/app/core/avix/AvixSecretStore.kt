package com.nuvio.app.core.avix

/**
 * Device-local storage for secrets the user types in (their own API keys). Android encrypts the
 * values with an Android Keystore AES-GCM key; values are never synced, logged or exported in
 * the local settings backup. Not available on iOS in v1 (features that need it are hidden).
 */
internal expect object AvixSecretStore {
    val isSupported: Boolean
    fun get(key: String): String?
    fun put(key: String, value: String?)
}
