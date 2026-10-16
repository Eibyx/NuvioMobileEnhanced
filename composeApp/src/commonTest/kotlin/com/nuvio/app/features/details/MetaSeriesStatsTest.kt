package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetaSeriesStatsTest {
    private fun video(season: Int?, episode: Int?) = MetaVideo(id = "v$season-$episode", title = "", season = season, episode = episode)

    @Test
    fun countsMainEpisodesAndSkipsSpecialsAndDuplicates() {
        val meta = MetaDetails(
            id = "tt1", type = "series", name = "Show",
            videos = listOf(video(0, 1), video(1, 1), video(1, 2), video(1, 2), video(2, 1)),
        )
        assertEquals(MetaSeriesStats(seasonCount = 2, episodeCount = 3), meta.mainSeriesStats())
    }

    @Test
    fun moviesHaveNoStats() {
        assertNull(MetaDetails(id = "tt2", type = "movie", name = "Movie").mainSeriesStats())
    }
}
