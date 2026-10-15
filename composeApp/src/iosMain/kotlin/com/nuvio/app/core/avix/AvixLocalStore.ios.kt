package com.nuvio.app.core.avix

import platform.Foundation.NSUserDefaults

internal actual object AvixLocalStore {
    private const val namespace = "avix.local."

    private val defaults: NSUserDefaults
        get() = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(namespace + key)

    actual fun putString(key: String, value: String?) {
        if (value == null) {
            defaults.removeObjectForKey(namespace + key)
        } else {
            defaults.setObject(value, forKey = namespace + key)
        }
    }

    actual fun keysWithPrefix(prefix: String): Set<String> =
        defaults.dictionaryRepresentation().keys
            .mapNotNull { it as? String }
            .filter { it.startsWith(namespace + prefix) }
            .mapTo(mutableSetOf()) { it.removePrefix(namespace) }
}
