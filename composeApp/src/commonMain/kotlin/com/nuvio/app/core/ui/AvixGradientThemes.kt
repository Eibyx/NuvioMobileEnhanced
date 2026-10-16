package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.AvixPreferencesReload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avix (ported from Yesnt10): extra gradient palettes. The synced Nuvio theme (`AppTheme`, part of
 * the profile settings blob) is left untouched so the original app never receives unknown values;
 * these palettes are a device-local override (docs/integration/DECISIONS.md D-5).
 */
enum class AvixGradientTheme(val label: String, val palette: ThemeColorPalette) {
    MESSENGER(
        "Messenger",
        ThemeColorPalette(
            secondary = Color(0xFF168AFF),
            secondaryVariant = Color(0xFF0072FF),
            accentGradient = listOf(Color(0xFF006DFF), Color(0xFF168AFF), Color(0xFF00A8FF), Color(0xFF38BDF8), Color(0xFF5CC8FF), Color(0xFF8AD8FF)),
            nativeAccentHex = "#168AFF",
            focusRing = Color(0xFF38BDF8),
            focusBackground = Color(0xFF132B45),
            background = Color(0xFF090E15),
            backgroundElevated = Color(0xFF131A23),
            backgroundCard = Color(0xFF192432),
        ),
    ),
    AMETHYST(
        "Amethyst",
        ThemeColorPalette(
            secondary = Color(0xFF8B5CF6),
            secondaryVariant = Color(0xFF7C3AED),
            accentGradient = listOf(Color(0xFF6D28D9), Color(0xFF7C3AED), Color(0xFF9333EA), Color(0xFFA855F7), Color(0xFFC084FC), Color(0xFFD8B4FE)),
            nativeAccentHex = "#8B5CF6",
            focusRing = Color(0xFFA855F7),
            focusBackground = Color(0xFF281A3D),
            background = Color(0xFF0F0B14),
            backgroundElevated = Color(0xFF1B1522),
            backgroundCard = Color(0xFF251B2F),
        ),
    ),
    BLOSSOM(
        "Blossom",
        ThemeColorPalette(
            secondary = Color(0xFFEC4899),
            secondaryVariant = Color(0xFFDB2777),
            accentGradient = listOf(Color(0xFFDB2777), Color(0xFFEC4899), Color(0xFFF472B6), Color(0xFFFDA4AF), Color(0xFFF43F7D), Color(0xFFFB7185)),
            nativeAccentHex = "#EC4899",
            focusRing = Color(0xFFF472B6),
            focusBackground = Color(0xFF3D1830),
            background = Color(0xFF120A10),
            backgroundElevated = Color(0xFF21141D),
            backgroundCard = Color(0xFF2D1926),
        ),
    ),
    LAGOON(
        "Lagoon",
        ThemeColorPalette(
            secondary = Color(0xFF14B8A6),
            secondaryVariant = Color(0xFF0891B2),
            accentGradient = listOf(Color(0xFF0891B2), Color(0xFF06B6D4), Color(0xFF22D3EE), Color(0xFF14B8A6), Color(0xFF2DD4BF), Color(0xFF5EEAD4)),
            nativeAccentHex = "#14B8A6",
            focusRing = Color(0xFF2DD4BF),
            focusBackground = Color(0xFF123A3A),
            background = Color(0xFF081110),
            backgroundElevated = Color(0xFF121E1D),
            backgroundCard = Color(0xFF182A28),
        ),
    ),
    SUNSET(
        "Sunset",
        ThemeColorPalette(
            secondary = Color(0xFFF97316),
            secondaryVariant = Color(0xFFF43F5E),
            accentGradient = listOf(Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFFFDA4AF), Color(0xFFF97316), Color(0xFFFB923C), Color(0xFFFDBA74)),
            nativeAccentHex = "#F97316",
            focusRing = Color(0xFFFB7185),
            focusBackground = Color(0xFF402019),
            background = Color(0xFF120C09),
            backgroundElevated = Color(0xFF211713),
            backgroundCard = Color(0xFF2D1E18),
        ),
    ),
}

object AvixGradientThemeRepository {
    private const val KEY = "ui.gradient_theme"
    private val _selected = MutableStateFlow<AvixGradientTheme?>(null)
    val selected: StateFlow<AvixGradientTheme?> = _selected.asStateFlow()
    private var loaded = false

    init {
        AvixPreferencesReload.register {
            loaded = false
            ensureLoaded()
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _selected.value = AvixLocalStore.getString(KEY)?.let { stored -> AvixGradientTheme.entries.firstOrNull { it.name == stored } }
    }

    fun select(theme: AvixGradientTheme?) {
        ensureLoaded()
        _selected.value = theme
        AvixLocalStore.putString(KEY, theme?.name)
    }
}
