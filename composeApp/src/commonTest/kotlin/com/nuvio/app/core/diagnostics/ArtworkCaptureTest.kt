package com.nuvio.app.core.diagnostics

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ArtworkCaptureTest {
    @AfterTest fun reset() { ArtworkDiagnostics.install(null) }
    @Test fun defaultDisabledAndUnavailableCannotStart() {
        ArtworkDiagnostics.configure(false)
        ArtworkDiagnostics.start()
        assertFalse(ArtworkDiagnostics.enabled)
        ArtworkDiagnostics.configure(true)
        assertFalse(ArtworkDiagnostics.enabled)
    }
    @Test fun stopKeepsSnapshotAndDoesNotEvaluateFutureEvents() {
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        ArtworkDiagnostics.event("hero.test") { "first=true" }
        ArtworkDiagnostics.stop()
        val report = ArtworkDiagnostics.report()
        ArtworkDiagnostics.event("hero.test") { error("evaluated after stop") }
        assertEquals(report, ArtworkDiagnostics.report())
        assertTrue("first=true" in report)
    }
    @Test fun restartPreservesRecordsUntilExplicitlyCleared() {
        ArtworkDiagnostics.install(null); ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        ArtworkDiagnostics.event("hero.test") { "first-session=true" }
        ArtworkDiagnostics.stop(); ArtworkDiagnostics.start()
        assertTrue("first-session=true" in ArtworkDiagnostics.report())
    }
    @Test fun clearRemovesEventsAndRestartsEpochWithoutStoppingCapture() {
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        ArtworkDiagnostics.event("hero.test") { "old=true" }
        val epoch = ArtworkDiagnostics.epoch()
        ArtworkDiagnostics.clear()
        assertTrue(ArtworkDiagnostics.enabled)
        assertTrue(ArtworkDiagnostics.epoch() > epoch)
        assertFalse("old=true" in ArtworkDiagnostics.report())
        assertEquals(0, ArtworkDiagnostics.retainedBytes())
    }
    @Test fun bufferIsBoundedInUtf8AndDropsOldEvents() {
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        repeat(4000) { n -> ArtworkDiagnostics.event("hero.test") { "n=$n " + "色".repeat(600) } }
        assertTrue(ArtworkDiagnostics.retainedBytes() <= ArtworkDiagnostics.MAX_BYTES)
        val report = ArtworkDiagnostics.report()
        assertTrue(report.encodeToByteArray().size <= ArtworkDiagnostics.MAX_BYTES + 8192)
        assertFalse("n=0 " in report)
        assertTrue("n=3999 " in report)
    }
    @Test fun concurrentTimelineNeverGoesBackwards() = runBlocking {
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        (1..4).map { launch(Dispatchers.Default) { repeat(100) { ArtworkDiagnostics.event("hero.test") } } }.joinAll()
        val times = Regex("t=(\\d+)ms").findAll(ArtworkDiagnostics.report()).map { it.groupValues[1].toLong() }.toList()
        assertEquals(401, times.size)
        assertTrue(times.zipWithNext().all { (a,b) -> b >= a })
    }
    @Test fun secretsAndFullUrlsCannotReachReport() {
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        ArtworkDiagnostics.event("hero.test") { "url=https://user:pass@example.org/private/key?secret=abc" }
        ArtworkDiagnostics.event("hero.test") { "Authorization: Bearer sensitive" }
        ArtworkDiagnostics.event("hero.test") { "Cookie: private-cookie" }
        ArtworkDiagnostics.event("hero.test") { "token=oauth-private" }
        val report = ArtworkDiagnostics.report()
        listOf("https://", "example.org", "sensitive", "private-cookie", "oauth-private", "user:pass").forEach { assertFalse(it in report) }
    }
    @Test fun metadataForUnrelatedContentIsExcludedAndAliasesCorrelate() {
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        ArtworkDiagnostics.watch("series:tmdb:1")
        ArtworkDiagnostics.alias("series:tt001", "series:tmdb:1")
        assertEquals(ArtworkDiagnostics.content("series:tt001"), ArtworkDiagnostics.content("series:tmdb:1"))
        ArtworkDiagnostics.event("metadata.basic") { "identity=${ArtworkDiagnostics.content("series:tt001")} related=true" }
        ArtworkDiagnostics.event("metadata.basic") { "identity=${ArtworkDiagnostics.content("movie:other")} unrelated=true" }
        assertTrue("related=true" in ArtworkDiagnostics.report())
        assertFalse("unrelated=true" in ArtworkDiagnostics.report())
    }
    @Test fun selectedOperationRemainsStableUntilContentChanges() {
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        ArtworkDiagnostics.selected("hero", "movie:first")
        val op = ArtworkDiagnostics.operation(ArtworkDiagnostics.content("movie:first"))
        ArtworkDiagnostics.selected("hero", "movie:first")
        assertEquals(op, ArtworkDiagnostics.operation(ArtworkDiagnostics.content("movie:first")))
        ArtworkDiagnostics.heroPage(2)
        val tag = ArtworkRequestTag("abc", "HERO_LAYER_2", "CATALOG")
        assertEquals("HERO_VISIBLE", ArtworkDiagnostics.purpose(tag))
        ArtworkDiagnostics.heroPage(3)
        assertEquals("HERO_NEIGHBOUR_LAYER", ArtworkDiagnostics.purpose(tag))
    }
    @Test fun provenanceDoesNotGuessTmdbFromUrlAlone() {
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        ArtworkDiagnostics.watch("series:1")
        ArtworkDiagnostics.resolved("series:1", "image-url", "ADDON")
        assertEquals("ADDON", ArtworkDiagnostics.source("image-url", "UNKNOWN"))
        ArtworkDiagnostics.resolved("series:1", "image-url", "TMDB")
        assertEquals("TMDB", ArtworkDiagnostics.source("image-url", "UNKNOWN"))
        assertEquals("UNKNOWN", ArtworkDiagnostics.source("other-url", "UNKNOWN"))
    }
}
