package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** Avix: WCAG contrast of the light theme for every built-in palette. */
class LightPaletteContrastTest {
    private val palettes = AppTheme.entries.map { theme ->
        theme to LightPalette.of(ThemeColors.getColorPalette(theme, CustomThemeColors.Default))
    }

    @Test
    fun accentIsVisibleOnLightBackgrounds() = palettes.forEach { (theme, p) ->
        listOf(p.background, p.backgroundElevated).forEach { bg ->
            val ratio = LightPalette.contrastRatio(p.secondary, bg)
            assertTrue(ratio >= LightPalette.MIN_ACCENT_CONTRAST - 0.05, "$theme accent contrast $ratio")
        }
    }

    @Test
    fun textMeetsWcagOnEverySurface() = palettes.forEach { (theme, p) ->
        listOf(p.background, p.backgroundElevated, p.backgroundCard).forEach { bg ->
            val primary = LightPalette.contrastRatio(LightPalette.textPrimary, bg)
            val secondary = LightPalette.contrastRatio(LightPalette.textSecondary, bg)
            val muted = LightPalette.contrastRatio(LightPalette.textMuted, bg)
            assertTrue(primary >= LightPalette.MIN_TEXT_CONTRAST, "$theme primary text $primary")
            assertTrue(secondary >= LightPalette.MIN_SECONDARY_TEXT_CONTRAST, "$theme secondary text $secondary")
            assertTrue(muted >= LightPalette.MIN_SECONDARY_TEXT_CONTRAST, "$theme muted text $muted")
        }
    }

    @Test
    fun labelsOnAccentAreReadable() = palettes.forEach { (theme, p) ->
        val ratio = LightPalette.contrastRatio(p.onSecondary, p.secondary)
        assertTrue(ratio >= LightPalette.MIN_ACCENT_CONTRAST, "$theme on-accent contrast $ratio")
    }
}
