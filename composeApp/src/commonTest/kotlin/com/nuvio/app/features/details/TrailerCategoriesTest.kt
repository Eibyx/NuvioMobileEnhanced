package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrailerCategoriesTest {

    @Test
    fun `maps TMDB video types to categories`() {
        assertEquals(TrailerVideoCategory.Trailer, trailerVideoCategoryOf("Trailer"))
        assertEquals(TrailerVideoCategory.Teaser, trailerVideoCategoryOf("teaser"))
        assertEquals(TrailerVideoCategory.BehindTheScenes, trailerVideoCategoryOf("Behind the Scenes"))
        assertEquals(TrailerVideoCategory.BehindTheScenes, trailerVideoCategoryOf("Behind The Scenes"))
        assertEquals(TrailerVideoCategory.Featurette, trailerVideoCategoryOf("Featurette"))
        assertEquals(TrailerVideoCategory.Clip, trailerVideoCategoryOf("Clip"))
        assertEquals(TrailerVideoCategory.Bloopers, trailerVideoCategoryOf("Bloopers"))
        assertEquals(TrailerVideoCategory.OpeningCredits, trailerVideoCategoryOf("Opening Credits"))
        assertEquals(TrailerVideoCategory.Other, trailerVideoCategoryOf("Recap"))
    }

    @Test
    fun `localized fallback label counts as trailer`() {
        assertEquals(TrailerVideoCategory.Trailer, trailerVideoCategoryOf("Tráiler", fallbackLabel = "Tráiler"))
        assertEquals(TrailerVideoCategory.Other, trailerVideoCategoryOf("Tráiler"))
    }

    @Test
    fun `groups are ordered trailer teaser behind the scenes then alphabetically`() {
        val trailers = listOf(
            trailer("f1", "Featurette"),
            trailer("b1", "Behind the Scenes"),
            trailer("c1", "Clip"),
            trailer("t1", "Teaser"),
            trailer("tr1", "Trailer"),
            trailer("b2", "Behind the Scenes"),
        )

        val grouped = groupTrailersByCategory(trailers, fallbackLabel = "Trailer")

        assertEquals(
            listOf("Trailer", "Teaser", "Behind the Scenes", "Clip", "Featurette"),
            grouped.keys.toList(),
        )
        assertEquals(listOf("b1", "b2"), grouped.getValue("Behind the Scenes").map { it.id })
    }

    @Test
    fun `blank types use fallback and case variants merge`() {
        val trailers = listOf(
            trailer("a", "Behind the Scenes"),
            trailer("b", "behind the scenes"),
            trailer("c", " "),
        )

        val grouped = groupTrailersByCategory(trailers, fallbackLabel = "Tráiler")

        assertEquals(listOf("Tráiler", "Behind the Scenes"), grouped.keys.toList())
        assertEquals(listOf("a", "b"), grouped.getValue("Behind the Scenes").map { it.id })
        assertEquals(listOf("c"), grouped.getValue("Tráiler").map { it.id })
    }

    @Test
    fun `default category prefers trailers otherwise first group`() {
        assertEquals("Trailer", defaultTrailerCategory(listOf("Teaser", "Trailer"), "Trailer"))
        assertEquals("Behind the Scenes", defaultTrailerCategory(listOf("Behind the Scenes", "Clip"), "Trailer"))
        assertNull(defaultTrailerCategory(emptyList(), "Trailer"))
    }

    private fun trailer(id: String, type: String) = MetaTrailer(
        id = id,
        key = "key-$id",
        name = id,
        site = "YouTube",
        type = type,
    )
}
