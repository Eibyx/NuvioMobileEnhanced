package com.nuvio.app.features.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogWatchedFilterTest {
    private val items = listOf(1, 2, 3, 4)
    private val isWatched: (Int) -> Boolean = { it % 2 == 0 }

    @Test
    fun filtersKeepOrder() {
        assertEquals(items, filterByWatched(items, CatalogWatchedFilter.ALL, isWatched))
        assertEquals(listOf(2, 4), filterByWatched(items, CatalogWatchedFilter.WATCHED, isWatched))
        assertEquals(listOf(1, 3), filterByWatched(items, CatalogWatchedFilter.UNWATCHED, isWatched))
    }
}
