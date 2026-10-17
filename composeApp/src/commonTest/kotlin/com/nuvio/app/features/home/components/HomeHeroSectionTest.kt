package com.nuvio.app.features.home.components

import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeHeroSectionTest {
    @Test fun `visual layers have unique identities for one two three and eight items`() {
        for (count in listOf(1, 2, 3, 8)) {
            val pages = heroVisiblePages(if (count == 1) 0 else 800, if (count == 1) 1 else Int.MAX_VALUE)
            assertEquals(pages.size, pages.toSet().size)
            assertTrue(pages.all { it >= 0 })
            assertTrue(pages.map { it % count }.all { it in 0 until count })
            if (count == 2) assertTrue(pages.map { it % count }.toSet().size < pages.size)
        }
    }
    @Test fun `blank artwork falls back without discarding custom poster selection`() {
        val item = com.nuvio.app.features.home.MetaPreview("id", "movie", "name", poster = "custom", banner = " ")
        assertEquals("custom", heroArtworkUrl(item, com.nuvio.app.features.home.HomeHeroArtworkSource.BACKDROP))
        assertEquals("custom", heroArtworkUrl(item, com.nuvio.app.features.home.HomeHeroArtworkSource.POSTER))
    }


    @Test
    fun `first and last indicators select adjacent pages across the loop`() {
        assertEquals(79, heroPageForItem(currentPage = 80, itemIndex = 7, itemCount = 8))
        assertEquals(88, heroPageForItem(currentPage = 87, itemIndex = 0, itemCount = 8))
    }

    @Test
    fun `indicators keep the current page when selecting the active title`() {
        assertEquals(83, heroPageForItem(currentPage = 83, itemIndex = 3, itemCount = 8))
    }

    @Test
    fun `indicators select the closest occurrence of a title`() {
        assertEquals(85, heroPageForItem(currentPage = 83, itemIndex = 5, itemCount = 8))
        assertEquals(81, heroPageForItem(currentPage = 83, itemIndex = 1, itemCount = 8))
    }

    @Test
    fun `mobile portrait hero height is width driven without continue watching`() {
        // Luqman's portrait hero design: width x 1.5 (upstream used 53.6% of the viewport).
        val layout = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = 844f,
        )

        assertEquals(false, layout.isTablet)
        assertEquals(585f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `tablet hero height remains width driven even with viewport height`() {
        val layout = homeHeroLayout(
            maxWidthDp = 840f,
            viewportHeightDp = 1200f,
        )

        assertEquals(true, layout.isTablet)
        assertEquals(386.4f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `mobile hero height leaves room for continue watching card section`() {
        val viewportHeight = 844f
        val continueWatchingLayout = rememberContinueWatchingLayout(maxWidthDp = 390f)
        val continueWatchingHeight = continueWatchingSectionHeightEstimate(
            style = ContinueWatchingSectionStyle.Card,
            layout = continueWatchingLayout,
            basePosterWidthDp = 110,
        )
        val reserveHeight = continueWatchingHeroViewportReserveHeight(
            style = ContinueWatchingSectionStyle.Card,
            layout = continueWatchingLayout,
            basePosterWidthDp = 110,
        )
        val layout = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = viewportHeight,
            mobileBelowSectionHeightHintDp = reserveHeight.value,
        )

        // The portrait hero keeps its width-driven height when the continue watching section
        // still fits in the first screen with at least the 24 dp gap upstream reserves.
        assertEquals(585f, layout.heroHeight.value, 0.001f)
        assertTrue(viewportHeight - layout.heroHeight.value - continueWatchingHeight.value >= 24f)
    }

    @Test
    fun `mobile portrait hero shrinks to keep continue watching on screen`() {
        val viewportHeight = 700f
        val continueWatchingLayout = rememberContinueWatchingLayout(maxWidthDp = 390f)
        val continueWatchingHeight = continueWatchingSectionHeightEstimate(
            style = ContinueWatchingSectionStyle.Card,
            layout = continueWatchingLayout,
            basePosterWidthDp = 110,
        )
        val reserveHeight = continueWatchingHeroViewportReserveHeight(
            style = ContinueWatchingSectionStyle.Card,
            layout = continueWatchingLayout,
            basePosterWidthDp = 110,
        )
        val layout = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = viewportHeight,
            mobileBelowSectionHeightHintDp = reserveHeight.value,
        )

        assertEquals(viewportHeight - reserveHeight.value, layout.heroHeight.value, 0.001f)
        assertEquals(24f, viewportHeight - layout.heroHeight.value - continueWatchingHeight.value, 0.001f)
    }

    @Test
    fun `mobile hero can shrink below default minimum to fit short viewport`() {
        val layout = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = 568f,
            mobileBelowSectionHeightHintDp = 300f,
        )

        assertEquals(false, layout.isTablet)
        assertEquals(268f, layout.heroHeight.value, 0.001f)
    }
}
