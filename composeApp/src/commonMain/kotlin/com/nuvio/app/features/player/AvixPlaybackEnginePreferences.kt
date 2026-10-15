package com.nuvio.app.features.player

import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.getBoolean
import com.nuvio.app.core.avix.putBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avix-only playback engine preferences. Device-local by design: the official
 * Nuvio profile blob has no field for them (docs/integration/DECISIONS.md D-5).
 */
object AvixPlaybackEnginePreferences {
    private const val rememberPerTitleKey = "player.engine.remember_per_title"
    private const val preferLibmpvForAssKey = "player.engine.prefer_libmpv_for_ass"
    private const val titleEnginePrefix = "player.engine.title."

    private val _rememberPerTitle = MutableStateFlow(true)
    val rememberPerTitle: StateFlow<Boolean> = _rememberPerTitle.asStateFlow()

    private val _preferLibmpvForAss = MutableStateFlow(true)
    val preferLibmpvForAss: StateFlow<Boolean> = _preferLibmpvForAss.asStateFlow()

    private val _rememberedTitleCount = MutableStateFlow(0)
    val rememberedTitleCount: StateFlow<Int> = _rememberedTitleCount.asStateFlow()

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _rememberPerTitle.value = AvixLocalStore.getBoolean(rememberPerTitleKey) ?: true
        _preferLibmpvForAss.value = AvixLocalStore.getBoolean(preferLibmpvForAssKey) ?: true
        _rememberedTitleCount.value = AvixLocalStore.keysWithPrefix(titleEnginePrefix).size
    }

    fun setRememberPerTitle(enabled: Boolean) {
        ensureLoaded()
        _rememberPerTitle.value = enabled
        AvixLocalStore.putBoolean(rememberPerTitleKey, enabled)
    }

    fun setPreferLibmpvForAss(enabled: Boolean) {
        ensureLoaded()
        _preferLibmpvForAss.value = enabled
        AvixLocalStore.putBoolean(preferLibmpvForAssKey, enabled)
    }

    /** Engine the user picked manually for [titleKey] (a series or movie id), if remembering is on. */
    fun engineForTitle(titleKey: String?): AndroidPlaybackEngine? {
        ensureLoaded()
        if (!_rememberPerTitle.value) return null
        val key = titleKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val stored = AvixLocalStore.getString(titleEnginePrefix + key) ?: return null
        return AndroidPlaybackEngine.entries.firstOrNull { it.name == stored }
            ?.takeIf { it != AndroidPlaybackEngine.Auto }
    }

    fun rememberEngineForTitle(titleKey: String?, engine: AndroidPlaybackEngine) {
        ensureLoaded()
        if (!_rememberPerTitle.value || engine == AndroidPlaybackEngine.Auto) return
        val key = titleKey?.trim()?.takeIf { it.isNotEmpty() } ?: return
        AvixLocalStore.putString(titleEnginePrefix + key, engine.name)
        _rememberedTitleCount.value = AvixLocalStore.keysWithPrefix(titleEnginePrefix).size
    }

    fun forgetTitle(titleKey: String?) {
        val key = titleKey?.trim()?.takeIf { it.isNotEmpty() } ?: return
        AvixLocalStore.putString(titleEnginePrefix + key, null)
        _rememberedTitleCount.value = AvixLocalStore.keysWithPrefix(titleEnginePrefix).size
    }

    fun forgetAllTitles() {
        AvixLocalStore.keysWithPrefix(titleEnginePrefix).forEach { AvixLocalStore.putString(it, null) }
        _rememberedTitleCount.value = 0
    }
}

/** True when a subtitle URL/label/codec looks like ASS/SSA, which renders faithfully only through libass. */
internal fun isAssSubtitle(urlOrName: String?, codec: String? = null): Boolean {
    val normalizedCodec = codec?.lowercase().orEmpty()
    if (normalizedCodec.contains("ssa") || normalizedCodec == "ass" || normalizedCodec.contains("x-ass")) return true
    val path = urlOrName
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.trimEnd('/')
        ?.lowercase()
        ?: return false
    return path.endsWith(".ass") || path.endsWith(".ssa")
}
