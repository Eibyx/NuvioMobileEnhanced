package com.nuvio.app.features.home

import kotlin.test.Test
import kotlin.test.assertEquals

class AvixHomeLayoutPreferencesTest {
    @Test
    fun shelfRowsAreClampedToOneToFour() {
        assertEquals(1, normalizeShelfRows(null))
        assertEquals(1, normalizeShelfRows(0))
        assertEquals(3, normalizeShelfRows(3))
        assertEquals(4, normalizeShelfRows(9))
    }

    @Test
    fun catalogColumnsAreAutomaticOrTwoToSix() {
        assertEquals(0, normalizeCatalogColumns(null))
        assertEquals(0, normalizeCatalogColumns(0))
        assertEquals(2, normalizeCatalogColumns(1))
        assertEquals(5, normalizeCatalogColumns(5))
        assertEquals(6, normalizeCatalogColumns(12))
    }

    @Test
    fun automaticColumnsAreUsedWhenNoneChosen() {
        assertEquals(4, resolveCatalogColumns(preferred = 0, automatic = 4))
        assertEquals(2, resolveCatalogColumns(preferred = 2, automatic = 4))
    }

    @Test
    fun previewLimitScalesWithRows() {
        assertEquals(18, homeShelfPreviewLimit(18, 1))
        assertEquals(54, homeShelfPreviewLimit(18, 3))
        assertEquals(72, homeShelfPreviewLimit(18, 10))
    }

    @Test
    fun balancedHeroKeepsTheOriginalOverlay() {
        assertEquals(listOf(0.02f, 0.12f, 0.34f, 0.78f), heroOverlayAlphas(AvixHeroDisplayMode.BALANCED))
        assertEquals(AvixHeroDisplayMode.BALANCED, AvixHeroDisplayMode.fromStorageValue("unknown"))
    }

    @Test
    fun overviewLinesFollowModeAndToggle() {
        assertEquals(2, heroOverviewMaxLines(AvixHeroOptions()))
        assertEquals(1, heroOverviewMaxLines(AvixHeroOptions(displayMode = AvixHeroDisplayMode.CINEMATIC)))
        assertEquals(4, heroOverviewMaxLines(AvixHeroOptions(displayMode = AvixHeroDisplayMode.INFO_RICH)))
        assertEquals(0, heroOverviewMaxLines(AvixHeroOptions(showOverview = false)))
    }
}
