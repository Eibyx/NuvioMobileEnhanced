@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.nuvio.app.features.tmdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ProgressiveArtworkSeasonsTest {
    @Test fun selectedSeasonIsPublishedBeforeSlowSecondarySeasonsAndAllAreRetained() = runTest {
        val gate = CompletableDeferred<Unit>()
        val published = mutableListOf<Int>()
        val batches = mutableListOf<List<Int>>()
        val job = async {
            visitArtworkSeasonBatches(listOf(0, 1, 2, 3, 4, 5, 6), 4, fetch = { batch ->
                batches += batch
                if (4 !in batch) gate.await()
                batch.associate { (it to 1) to TmdbEpisodeEnrichment(title = "season $it") }
            }, publish = { published += it.keys.map { key -> key.first } })
        }
        runCurrent()
        assertEquals(listOf(4), published)
        assertFalse(job.isCompleted)
        gate.complete(Unit); job.await()
        assertEquals(setOf(0, 1, 2, 3, 4, 5, 6), published.toSet())
        assertTrue(batches.all { it.size <= 2 })
    }
    @Test fun partialFailuresDoNotPublishAnEmptyReplacement() = runTest {
        val published = mutableListOf<Map<Pair<Int, Int>, TmdbEpisodeEnrichment>>()
        visitArtworkSeasonBatches(listOf(1, 2, 3), 1,
            fetch = { if (1 in it) mapOf((1 to 1) to TmdbEpisodeEnrichment(title = "kept")) else emptyMap() },
            publish = { published += it })
        assertEquals(1, published.size)
        assertEquals("kept", published.single()[1 to 1]?.title)
    }
    @Test fun cancellationStopsSecondaryWorkAndNoLatePublicationOccurs() = runTest {
        val gate = CompletableDeferred<Unit>()
        var published = 0
        val job = async {
            visitArtworkSeasonBatches(listOf(1, 2, 3, 4, 5), 1,
                fetch = { if (1 !in it) gate.await(); mapOf((it.first() to 1) to TmdbEpisodeEnrichment()) },
                publish = { published++ })
        }
        runCurrent(); assertEquals(1, published)
        job.cancelAndJoin(); gate.complete(Unit); runCurrent()
        assertEquals(1, published)
    }
}
