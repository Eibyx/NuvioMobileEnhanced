package com.nuvio.app.core.avix

import android.content.Context
import android.content.SharedPreferences

internal actual object AvixLocalStore {
    private const val preferencesName = "avix_local_preferences"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun getString(key: String): String? = preferences?.getString(key, null)

    actual fun putString(key: String, value: String?) {
        preferences?.edit()?.apply {
            if (value == null) remove(key) else putString(key, value)
        }?.apply()
    }

    actual fun keysWithPrefix(prefix: String): Set<String> =
        preferences?.all?.keys?.filterTo(mutableSetOf()) { it.startsWith(prefix) }.orEmpty()
}
