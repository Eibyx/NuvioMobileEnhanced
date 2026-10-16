package com.nuvio.app.core.avix

/** iOS: no Keychain-backed store in v1; features that need user secrets are unavailable. */
internal actual object AvixSecretStore {
    actual val isSupported: Boolean = false
    actual fun get(key: String): String? = null
    actual fun put(key: String, value: String?) = Unit
}
