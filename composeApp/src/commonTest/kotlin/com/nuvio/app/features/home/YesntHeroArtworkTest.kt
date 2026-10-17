package com.nuvio.app.features.home

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.components.heroArtworkCandidates
import com.nuvio.app.features.home.components.heroArtworkUrl
import com.nuvio.app.features.home.components.resolveHeroTargetPage
import com.nuvio.app.features.tmdb.TmdbPreviewArtwork
import com.nuvio.app.features.tmdb.TmdbSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class YesntHeroArtworkTest {
    private fun items(count: Int) = List(count) { MetaPreview("id-$it", "series", "Title $it") }

    @Test fun snapshotsDoNotRestoreLocalizedHeroArtworkInAnotherLanguage() {
        assertTrue(shouldRestoreHeroArtwork(null, "es"))
        assertTrue(shouldRestoreHeroArtwork("es_ES", "es-ES"))
        assertFalse(shouldRestoreHeroArtwork("en-US", "es-ES"))
    }

    @Test fun activeFirstAndAtMostTwoDistinctNeighboursForAllCarouselSizes() {
        for (count in listOf(1, 2, 3, 8)) {
            val all = items(count)
            for (active in all.indices) {
                val prioritized = heroMetadataPriority(all, active)
                assertEquals(all[active], prioritized.first())
                assertEquals(minOf(count, 3), prioritized.size)
                assertEquals(prioritized.size, prioritized.distinctBy(MetaPreview::stableKey).size)
                assertEquals(count, all.mapIndexed { page, item -> heroPagerKey(item, page) }.distinct().size)
            }
        }
    }

    @Test fun repeatedResourcesHaveDistinctPagerSlotsButOneMetadataRequestIdentity() {
        val item = items(1).single()
        assertNotEquals(heroPagerKey(item, 0), heroPagerKey(item, 1))
        assertEquals(listOf(item), heroMetadataPriority(listOf(item, item), 1))
    }

    @Test fun finitePagerWrapsWithoutVisitingIntermediateItems() {
        for (count in listOf(1, 2, 3, 8)) {
            assertEquals(count - 1, resolveHeroTargetPage(0, count, 200f, 400f, 390f))
            assertEquals(0, resolveHeroTargetPage(count - 1, count, -200f, -400f, 390f))
            assertEquals(0, resolveHeroTargetPage(0, count, 2f, 0f, 390f))
        }
    }

    @Test fun sharedWorkSurvivesOneCancelledWaiterAndEndsWithItsSession() = runTest {
        val coordinator = HeroRequestCoordinator<String>(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val first = async { coordinator.get("profile:en:title") { calls++; gate.await(); "ready" } }
        val second = async { coordinator.get("profile:en:title") { fail("duplicate loader") } }
        runCurrent()
        first.cancel()
        gate.complete(Unit)
        assertEquals("ready", second.await())
        assertEquals(1, calls)
        assertEquals("ready", coordinator.get("profile:en:title") { fail("completed cache missed") })
    }

    @Test fun failuresRetryAndProfileAndLanguageNeverShareWork() = runTest {
        val coordinator = HeroRequestCoordinator<String>(backgroundScope)
        assertNull(coordinator.get("p1:en:title") { null })
        assertEquals("retry", coordinator.get("p1:en:title") { "retry" })
        assertEquals("spanish", coordinator.get("p1:es:title") { "spanish" })
        assertEquals("other profile", coordinator.get("p2:en:title") { "other profile" })
    }

    @Test fun optionalFailuresDoNotFailTheHeroAndCancellationPropagates() = runTest {
        assertNull(heroAttempt<String>(100) { error("network unavailable") })
        assertNull(heroAttempt(100) { kotlinx.coroutines.delay(200); "late" })
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            heroAttempt<String>(100) { throw kotlinx.coroutines.CancellationException("profile changed") }
        }
    }

    @Test fun completedRequestCapacityIsBounded() = runTest {
        val coordinator = HeroRequestCoordinator<String>(backgroundScope, capacity = 2)
        for (key in listOf("a", "b", "c")) assertEquals(key, coordinator.get(key) { key })
        assertEquals("reloaded", coordinator.get("a") { "reloaded" })
    }

    @Test fun artworkUsesOnlyFiniteValidAlternativesFromTheSelectedContent() {
        val item = items(1).single().copy(banner = "https://image/banner", poster = "https://image/poster", rawPosterUrl = "https://image/original")
        assertEquals(listOf("https://image/banner", "https://image/original", "https://image/poster"), heroArtworkCandidates(item, "https://image/banner"))
        assertEquals(listOf("https://image/banner", "https://image/original", "https://image/poster"), heroArtworkCandidates(item, "javascript:bad"))
        assertTrue(heroArtworkCandidates(items(1).single(), " ").isEmpty())
        assertEquals("https://image/poster", item.heroArtworkUrl(HomeHeroArtworkSource.POSTER, MetaDetails("id-0", "series", "Name", poster = "https://remote/poster")))
    }

    @Test fun localizedArtworkPreservesCustomPosterAndNavigationIdentity() {
        val item = items(1).single().copy(poster = "https://custom/poster", rawPosterUrl = "https://old/poster")
        val localized = TmdbPreviewArtwork(localizedTitle = "Local", poster = "https://tmdb/poster", backdrop = "https://tmdb/backdrop").applyTo(item)
        assertEquals(item.stableKey(), localized.stableKey())
        assertEquals("https://custom/poster", localized.poster)
        assertEquals("https://tmdb/poster", localized.rawPosterUrl)
        assertEquals("https://tmdb/backdrop", localized.banner)
    }

    @Test fun heroEnrichmentPreservesEpisodesRatingsAndAddonFields() {
        val preview = MetaDetails("selected", "series", "Localized", background = "https://tmdb/background", externalRatings = listOf(MetaExternalRating("tmdb", 8.0)))
        val addon = MetaDetails("provider-id", "tv", "Addon", imdbId = "tt123", videos = listOf(MetaVideo(id = "episode", title = "Episode", season = 2, episode = 4)), externalRatings = listOf(MetaExternalRating("imdb", 9.0)))
        val merged = mergeHeroMetadata(preview, addon, TmdbSettings(enabled = true))
        assertEquals("selected", merged.id)
        assertEquals("series", merged.type)
        assertEquals(addon.imdbId, merged.imdbId)
        assertEquals(addon.videos, merged.videos)
        assertEquals(setOf("imdb", "tmdb"), merged.externalRatings.map { it.source }.toSet())
        assertEquals("Localized", merged.name)
        assertEquals("Addon", mergeHeroMetadata(preview, addon, TmdbSettings(enabled = false)).name)
    }
}
