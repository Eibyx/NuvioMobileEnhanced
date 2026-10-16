package com.nuvio.app.features.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.catalog_filter_all
import nuvio.composeapp.generated.resources.catalog_filter_unwatched
import nuvio.composeapp.generated.resources.catalog_filter_watched
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10 port): All / Watched / Unwatched filter on library catalogs. */
enum class CatalogWatchedFilter { ALL, WATCHED, UNWATCHED }

internal fun <T> filterByWatched(items: List<T>, filter: CatalogWatchedFilter, isWatched: (T) -> Boolean): List<T> =
    when (filter) {
        CatalogWatchedFilter.ALL -> items
        CatalogWatchedFilter.WATCHED -> items.filter(isWatched)
        CatalogWatchedFilter.UNWATCHED -> items.filterNot(isWatched)
    }

@Composable
internal fun CatalogWatchedFilterRow(
    selected: CatalogWatchedFilter,
    watchedCount: Int,
    unwatchedCount: Int,
    onSelected: (CatalogWatchedFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == CatalogWatchedFilter.ALL,
            onClick = { onSelected(CatalogWatchedFilter.ALL) },
            label = { Text(stringResource(Res.string.catalog_filter_all, watchedCount + unwatchedCount)) },
        )
        FilterChip(
            selected = selected == CatalogWatchedFilter.WATCHED,
            onClick = { onSelected(CatalogWatchedFilter.WATCHED) },
            label = { Text(stringResource(Res.string.catalog_filter_watched, watchedCount)) },
        )
        FilterChip(
            selected = selected == CatalogWatchedFilter.UNWATCHED,
            onClick = { onSelected(CatalogWatchedFilter.UNWATCHED) },
            label = { Text(stringResource(Res.string.catalog_filter_unwatched, unwatchedCount)) },
        )
    }
}
