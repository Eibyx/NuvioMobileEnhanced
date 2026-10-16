package com.nuvio.app.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.getBoolean
import com.nuvio.app.core.avix.putBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avix (Yesnt10 port): appearance extras kept on this device only (DECISIONS D-5).
 * - animatedAccent: the accent gradient of primary buttons and the selected navigation item
 *   slowly flows (Yesnt10 "animated gradient themes"; off by default; only with gradient themes).
 * - statusBarHidden: hide the Android status bar across the app (swipe to reveal it).
 * - systemFont: Settings → Appearance → Typography. false = original Nuvio font (default),
 *   true = the device's system font, following the system text size (accessibility).
 */
object AvixAppearancePreferences {
    private const val KEY_ANIMATED_ACCENT = "ui.animated_accent"
    private const val KEY_STATUS_BAR_HIDDEN = "ui.status_bar_hidden"
    private const val KEY_SYSTEM_FONT = "ui.system_font"

    private val _animatedAccent = MutableStateFlow(false)
    val animatedAccent: StateFlow<Boolean> = _animatedAccent.asStateFlow()
    private val _statusBarHidden = MutableStateFlow(false)
    val statusBarHidden: StateFlow<Boolean> = _statusBarHidden.asStateFlow()
    private val _systemFont = MutableStateFlow(false)
    val systemFont: StateFlow<Boolean> = _systemFont.asStateFlow()
    private var loaded = false

    init {
        com.nuvio.app.core.avix.AvixPreferencesReload.register {
            loaded = false
            ensureLoaded()
            AvixSystemUiController.setStatusBarHidden(_statusBarHidden.value)
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _animatedAccent.value = AvixLocalStore.getBoolean(KEY_ANIMATED_ACCENT) ?: false
        _statusBarHidden.value = AvixLocalStore.getBoolean(KEY_STATUS_BAR_HIDDEN) ?: false
        _systemFont.value = AvixLocalStore.getBoolean(KEY_SYSTEM_FONT) ?: false
    }

    fun setSystemFont(value: Boolean) {
        ensureLoaded()
        _systemFont.value = value
        AvixLocalStore.putBoolean(KEY_SYSTEM_FONT, value)
    }

    fun setAnimatedAccent(value: Boolean) {
        ensureLoaded()
        _animatedAccent.value = value
        AvixLocalStore.putBoolean(KEY_ANIMATED_ACCENT, value)
    }

    fun setStatusBarHidden(value: Boolean) {
        ensureLoaded()
        _statusBarHidden.value = value
        AvixLocalStore.putBoolean(KEY_STATUS_BAR_HIDDEN, value)
        AvixSystemUiController.setStatusBarHidden(value)
    }
}

/** Shows or hides the status bar for the whole app (no-op where unsupported). */
internal expect object AvixSystemUiController {
    val isSupported: Boolean
    fun setStatusBarHidden(hidden: Boolean)
    /** Re-applies the preference, e.g. after the player restored the system bars. */
    fun reapply()
}

/**
 * Accent brush for primary buttons and selected navigation items. When the animated accent is
 * on and the theme has a gradient, the gradient slides back and forth; only the caller
 * recomposes, never the whole theme.
 */
@Composable
fun ThemeColorPalette.rememberAccentBrush(): Brush {
    val animated by AvixAppearancePreferences.animatedAccent.collectAsState()
    if (!animated || accentGradient.size < 2) return accentBrush()
    val transition = rememberInfiniteTransition(label = "avix_accent")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "avix_accent_phase",
    )
    return animatedAccentBrush(accentGradient, phase)
}

internal fun animatedAccentBrush(colors: List<androidx.compose.ui.graphics.Color>, phase: Float): Brush {
    val shift = ACCENT_SPAN * phase
    return Brush.linearGradient(
        colors = colors,
        start = Offset(-shift, 0f),
        end = Offset(ACCENT_SPAN - shift, ACCENT_SPAN / 3f),
        tileMode = TileMode.Mirror,
    )
}

private const val ACCENT_SPAN = 420f

/**
 * Font scale applied by the theme. The original Nuvio font keeps the fixed scale of upstream
 * (1x); the system font follows the system text size, bounded so the fixed-size layouts (poster
 * rows, player controls, navigation bar) keep fitting.
 */
internal fun avixFontScale(useSystemFont: Boolean, deviceFontScale: Float): Float =
    if (useSystemFont) deviceFontScale.coerceIn(MinSystemFontScale, MaxSystemFontScale) else 1f

internal const val MinSystemFontScale = 0.85f
internal const val MaxSystemFontScale = 1.3f
