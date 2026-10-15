package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Avix: light-mode counterpart of a [ThemeColorPalette]. Backgrounds become light neutrals with a
 * hint of the theme accent, and the accent colours are darkened just enough to stay legible on them
 * (WCAG 2.x contrast ≥ [MIN_ACCENT_CONTRAST] for UI components). Gradients are kept as authored.
 */
internal object LightPalette {
    const val MIN_ACCENT_CONTRAST = 3.0
    const val MIN_TEXT_CONTRAST = 7.0
    const val MIN_SECONDARY_TEXT_CONTRAST = 4.5

    private val baseBackground = Color(0xFFF4F5F7)
    private val baseSurface = Color(0xFFFFFFFF)
    private val baseCard = Color(0xFFE9EBEF)

    val textPrimary = Color(0xFF14171A)
    val textSecondary = Color(0xFF3F464D)
    val textMuted = Color(0xFF5B636B)
    val borderDefault = Color(0xFFD3D7DC)
    val borderStrong = Color(0xFFB4BAC1)

    fun of(palette: ThemeColorPalette): ThemeColorPalette {
        val background = lerp(baseBackground, palette.secondary, 0.035f)
        val surface = lerp(baseSurface, palette.secondary, 0.015f)
        val card = lerp(baseCard, palette.secondary, 0.05f)
        val accent = ensureContrast(palette.secondary, background, MIN_ACCENT_CONTRAST)
        val accentVariant = ensureContrast(palette.secondaryVariant, background, MIN_ACCENT_CONTRAST)
        return palette.copy(
            secondary = accent,
            secondaryVariant = accentVariant,
            onSecondary = readableOn(accent),
            onSecondaryVariant = readableOn(accentVariant),
            focusRing = accent,
            focusBackground = lerp(surface, accent, 0.16f),
            background = background,
            backgroundElevated = surface,
            backgroundCard = card,
        )
    }

    /** Darkens [color] towards black until its contrast against [background] reaches [minRatio]. */
    fun ensureContrast(color: Color, background: Color, minRatio: Double): Color {
        var candidate = color
        var step = 0
        while (contrastRatio(candidate, background) < minRatio && step < 40) {
            step++
            candidate = lerp(color, Color.Black, step * 0.025f)
        }
        return candidate
    }

    fun readableOn(background: Color): Color =
        if (contrastRatio(Color.White, background) >= contrastRatio(textPrimary, background)) Color.White else textPrimary

    /** WCAG 2.x contrast ratio between two opaque colours (1.0 – 21.0). */
    fun contrastRatio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
