package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamPrefetchTargetTest {
    @Test
    fun movieUsesMetaIdWithoutEpisode() {
        assertEquals(
            StreamPrefetchTarget("movie", "tt1", "tt1", null, null),
            streamPrefetchTarget("movie", "tt1", isSeriesLike = false, seriesVideoId = null, seasonNumber = null, episodeNumber = null, canPlay = true),
        )
    }

    @Test
    fun seriesUsesNextEpisode() {
        assertEquals(
            StreamPrefetchTarget("series", "tt2:1:3", "tt2", 1, 3),
            streamPrefetchTarget("series", "tt2", isSeriesLike = true, seriesVideoId = "tt2:1:3", seasonNumber = 1, episodeNumber = 3, canPlay = true),
        )
    }

    @Test
    fun nothingWhenNotPlayableOrNoEpisode() {
        assertNull(streamPrefetchTarget("movie", "tt1", false, null, null, null, canPlay = false))
        assertNull(streamPrefetchTarget("series", "tt2", true, seriesVideoId = null, seasonNumber = null, episodeNumber = null, canPlay = true))
    }
}
