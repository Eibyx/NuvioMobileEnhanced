package com.nuvio.app.features.streams

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nuvio.app.core.ui.LightPalette
import com.nuvio.app.core.ui.NuvioTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for the streams episode header (EpisodeHeroBlock): it renders the real
 * composable with Robolectric's native graphics over a pure white and a pure black "thumbnail",
 * in light and dark mode, and measures the pixels behind and inside the texts. The title must be
 * white on a dark scrim; dark theme text directly on the image (the light-mode bug) fails here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EpisodeHeroContrastTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightThemeOverWhiteImage() = verifyHero(darkTheme = false, backdrop = Color.White)

    @Test
    fun lightThemeOverBlackImage() = verifyHero(darkTheme = false, backdrop = Color.Black)

    @Test
    fun darkThemeOverWhiteImage() = verifyHero(darkTheme = true, backdrop = Color.White)

    @Test
    fun darkThemeOverBlackImage() = verifyHero(darkTheme = true, backdrop = Color.Black)

    @Test
    fun badgeAccentKeepsContrastOnTheScrim() {
        val accents = listOf(
            Color(0xFF1E3A8A), // dark blue (light-mode accents are darkened)
            Color(0xFF7C3AED),
            Color(0xFFE11D48),
            Color(0xFF14171A), // near black
            Color.White,
        )
        accents.forEach { accent ->
            val badge = EpisodeHeroColors.badge(accent)
            assertTrue(
                LightPalette.contrastRatio(badge, EpisodeHeroColors.worstCaseBackdrop) >= 4.5,
                "badge for $accent has insufficient contrast",
            )
        }
        // An accent that is already readable is kept as is.
        assertEquals(Color.White, EpisodeHeroColors.badge(Color.White))
    }

    private fun verifyHero(darkTheme: Boolean, backdrop: Color) {
        compose.setContent {
            NuvioTheme(darkTheme = darkTheme) {
                // The backdrop stands in for the (blurred) episode thumbnail behind the scrim.
                Box(Modifier.fillMaxWidth().background(backdrop)) {
                    EpisodeHeroBlock(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        episodeTitle = EPISODE_TITLE,
                        thumbnail = null,
                        blurred = true,
                        showTitle = SHOW_TITLE,
                    )
                }
            }
        }
        compose.waitForIdle()

        val title = measure(EPISODE_TITLE)
        assertTrue(title.background.luminance() <= 0.2f, "title backdrop is not dark: $title")
        assertTrue(title.text.luminance() >= 0.9f, "title is not white: $title")
        assertTrue(title.contrast >= 7.0, "title contrast too low: $title")

        val show = measure(SHOW_TITLE)
        assertTrue(show.background.luminance() <= 0.2f, "show title backdrop is not dark: $show")
        assertTrue(show.text.luminance() >= 0.5f, "show title is not light: $show")
        assertTrue(show.contrast >= 4.5, "show title contrast too low: $show")

        val badge = measure("S1 E1")
        assertTrue(badge.contrast >= 4.5, "S1 E1 badge contrast too low: $badge")
        println("EpisodeHero dark=$darkTheme backdrop=$backdrop title=$title show=$show badge=$badge")
    }

    private data class Measured(val background: Color, val text: Color, val contrast: Double)

    /** Background = most frequent colour in the text's bounds; text = the pixel farthest from it. */
    private fun measure(text: String): Measured {
        val pixels: PixelMap = compose.onNodeWithText(text).captureToImage().toPixelMap()
        val counts = HashMap<Int, Int>()
        val colors = ArrayList<Color>(pixels.width * pixels.height)
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y].copy(alpha = 1f)
                colors += color
                val key = quantize(color)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        val backgroundKey = counts.maxBy { it.value }.key
        val background = colors.first { quantize(it) == backgroundKey }
        val textColor = colors.maxBy { LightPalette.contrastRatio(it, background) }
        return Measured(background, textColor, LightPalette.contrastRatio(textColor, background))
    }

    private fun quantize(color: Color): Int {
        val r = (color.red * 31).toInt()
        val g = (color.green * 31).toInt()
        val b = (color.blue * 31).toInt()
        return (r shl 10) or (g shl 5) or b
    }

    private companion object {
        const val EPISODE_TITLE = "The Episode Title"
        const val SHOW_TITLE = "Show Name"
    }
}
