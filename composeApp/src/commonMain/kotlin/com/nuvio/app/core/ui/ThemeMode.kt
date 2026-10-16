package com.nuvio.app.core.ui

import com.nuvio.app.core.avix.AvixLocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Avix: light / dark / follow-system appearance. */
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

/**
 * Device-local (docs/integration/DECISIONS.md D-5): the official Nuvio profile blob has no field
 * for it, so it is never synced. Default DARK keeps the existing look for current users.
 */
object ThemeModeRepository {
    private const val KEY = "ui.theme_mode"
    private val _mode = MutableStateFlow(ThemeMode.DARK)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()
    private var loaded = false

    init {
        com.nuvio.app.core.avix.AvixPreferencesReload.register {
            loaded = false
            ensureLoaded()
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _mode.value = AvixLocalStore.getString(KEY)
            ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.DARK
    }

    fun setMode(mode: ThemeMode) {
        ensureLoaded()
        _mode.value = mode
        AvixLocalStore.putString(KEY, mode.name)
    }
}
