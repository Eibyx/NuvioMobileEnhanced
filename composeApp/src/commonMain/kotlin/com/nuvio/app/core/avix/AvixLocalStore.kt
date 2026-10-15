package com.nuvio.app.core.avix

/**
 * Device-local key/value storage for Avix-only preferences.
 *
 * Values stored here are never part of the synced Nuvio profile settings blob, so they
 * cannot overwrite (or be overwritten by) settings written by Nuvio and its forks.
 */
internal expect object AvixLocalStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun keysWithPrefix(prefix: String): Set<String>
}

internal fun AvixLocalStore.getBoolean(key: String): Boolean? =
    when (getString(key)) {
        "true" -> true
        "false" -> false
        else -> null
    }

internal fun AvixLocalStore.putBoolean(key: String, value: Boolean?) =
    putString(key, value?.toString())
