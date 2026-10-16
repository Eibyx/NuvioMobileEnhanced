package com.nuvio.app.features.details

/** Avix (Yesnt10 port): counts of main seasons and episodes (specials excluded). */
data class MetaSeriesStats(val seasonCount: Int, val episodeCount: Int)

fun MetaDetails.mainSeriesStats(): MetaSeriesStats? {
    val mainEpisodes = videos
        .filter { (it.season ?: 0) > 0 && (it.episode ?: 0) > 0 }
        .distinctBy { "${it.season}:${it.episode}" }
    val seasonCount = mainEpisodes.mapNotNull { it.season }.distinct().size
    return if (seasonCount > 0) MetaSeriesStats(seasonCount, mainEpisodes.size) else null
}
