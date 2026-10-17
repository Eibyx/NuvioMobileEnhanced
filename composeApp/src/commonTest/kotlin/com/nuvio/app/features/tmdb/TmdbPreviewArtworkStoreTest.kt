package com.nuvio.app.features.tmdb

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TmdbPreviewArtworkStoreTest {
    @Test fun cacheRestartsWithProfileAndLanguageIsolationAndExpiry() = runTest {
        var disk: String? = null
        var time = 1000L
        fun store() = PreviewArtworkStore({ disk }, { disk = it }, { time }, capacity = 2, ttlMs = 100)
        val key = previewArtworkKey("account-a:profile-1", "tv", "1", "es_ES")
        val artwork = TmdbPreviewArtwork(backdrop = "https://tmdb/backdrop")
        store().save(key, artwork)
        val restarted = store()
        assertEquals(artwork, restarted.load(previewArtworkKey("account-a:profile-1", "tv", "1", "es-ES")))
        assertNull(restarted.load(previewArtworkKey("account-a:profile-2", "tv", "1", "es-ES")))
        assertNull(restarted.load(previewArtworkKey("account-b:profile-1", "tv", "1", "es-ES")))
        assertNull(restarted.load(previewArtworkKey("account-a:profile-1", "tv", "1", "en")))
        time += 100
        assertNull(restarted.load(key))
    }

    @Test fun capacityCorruptionAndClockRollbackAreHandled() = runTest {
        var disk: String? = "interrupted or corrupt write"
        var time = 1000L
        val store = PreviewArtworkStore({ disk }, { disk = it }, { time }, capacity = 2)
        assertNull(store.load("missing"))
        for (id in listOf("a", "b", "c")) store.save(id, TmdbPreviewArtwork(backdrop = id))
        assertNull(store.load("a"))
        assertEquals("b", store.load("b")?.backdrop)
        assertEquals("c", store.load("c")?.backdrop)
        val restarted = PreviewArtworkStore({ disk }, {}, { time }, capacity = 2)
        assertNull(restarted.load("a"))
        time = 900
        assertNull(restarted.load("c"))
    }

    @Test fun failedPersistenceDoesNotLoseTheResolvedArtwork() = runTest {
        val store = PreviewArtworkStore({ null }, { error("storage unavailable") }, { 1000L })
        store.save("a", TmdbPreviewArtwork(backdrop = "https://tmdb/a"))
        assertEquals("https://tmdb/a", store.load("a")?.backdrop)
    }

    @Test fun textlessBackdropSelectionDoesNotChangeLocalizedLogoSelection() {
        val images = listOf(TmdbImage("/english", "en"), TmdbImage("/spanish", "es"), TmdbImage("/textless", null))
        assertEquals("/textless", images.selectBestTextlessImagePath("es-ES"))
        assertEquals("/spanish", images.selectBestLocalizedImagePath("es-ES"))
        assertNull(listOf(TmdbImage(" ", null)).selectBestTextlessImagePath("es-ES"))
    }
}
