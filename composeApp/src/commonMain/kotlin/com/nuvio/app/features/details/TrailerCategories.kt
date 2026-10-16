package com.nuvio.app.features.details

/**
 * Avix: TMDB video categories shown by the details "Behind the Scenes" selector
 * (ported from the Yesnt10 fork). TMDB reports `type` as an English label such as
 * "Trailer", "Teaser", "Behind the Scenes", "Featurette", "Clip", "Bloopers" or
 * "Opening Credits"; anything else falls back to [Other] and keeps its raw label.
 */
enum class TrailerVideoCategory {
    Trailer,
    Teaser,
    BehindTheScenes,
    Featurette,
    Clip,
    Bloopers,
    OpeningCredits,
    Other,
}

private fun normalizeTrailerType(type: String): String =
    type.lowercase().filter(Char::isLetterOrDigit)

/** Maps a raw TMDB/addon video type to a known [TrailerVideoCategory]. */
fun trailerVideoCategoryOf(type: String, fallbackLabel: String? = null): TrailerVideoCategory {
    if (fallbackLabel != null && type.trim().equals(fallbackLabel.trim(), ignoreCase = true)) {
        return TrailerVideoCategory.Trailer
    }
    return when (normalizeTrailerType(type)) {
        "trailer", "trailers" -> TrailerVideoCategory.Trailer
        "teaser", "teasers" -> TrailerVideoCategory.Teaser
        "behindthescenes", "bts" -> TrailerVideoCategory.BehindTheScenes
        "featurette", "featurettes" -> TrailerVideoCategory.Featurette
        "clip", "clips" -> TrailerVideoCategory.Clip
        "blooper", "bloopers" -> TrailerVideoCategory.Bloopers
        "openingcredits" -> TrailerVideoCategory.OpeningCredits
        else -> TrailerVideoCategory.Other
    }
}

/**
 * Sort priority used by Yesnt10: trailers first, then teasers, then behind the scenes,
 * then every other category alphabetically.
 */
fun trailerCategoryPriority(type: String, fallbackLabel: String? = null): Int =
    when (trailerVideoCategoryOf(type, fallbackLabel)) {
        TrailerVideoCategory.Trailer -> 0
        TrailerVideoCategory.Teaser -> 1
        TrailerVideoCategory.BehindTheScenes -> 2
        else -> 3
    }

/**
 * Groups [trailers] by their video type (blank types use [fallbackLabel]), merging types
 * that only differ by case/spacing, and orders the groups by [trailerCategoryPriority]
 * then label. Order inside each group is preserved.
 */
fun groupTrailersByCategory(
    trailers: List<MetaTrailer>,
    fallbackLabel: String,
): LinkedHashMap<String, List<MetaTrailer>> {
    val labels = linkedMapOf<String, String>()
    val groups = linkedMapOf<String, MutableList<MetaTrailer>>()
    trailers.forEach { trailer ->
        val label = trailer.type.trim().ifBlank { fallbackLabel }
        val normalized = normalizeTrailerType(label).ifBlank { label.lowercase() }
        labels.getOrPut(normalized) { label }
        groups.getOrPut(normalized) { mutableListOf() }.add(trailer)
    }
    return groups.keys
        .sortedWith(
            compareBy<String> { key -> trailerCategoryPriority(labels.getValue(key), fallbackLabel) }
                .thenBy { key -> labels.getValue(key).lowercase() },
        )
        .associateTo(linkedMapOf()) { key -> labels.getValue(key) to groups.getValue(key).toList() }
}

/** Initial category: the trailer group when present, otherwise the first group. */
fun defaultTrailerCategory(categories: Collection<String>, fallbackLabel: String): String? =
    categories.firstOrNull { trailerVideoCategoryOf(it, fallbackLabel) == TrailerVideoCategory.Trailer }
        ?: categories.firstOrNull()
