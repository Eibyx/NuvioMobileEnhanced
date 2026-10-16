package com.nuvio.app.features.downloads

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.catalog_filter_all
import nuvio.composeapp.generated.resources.downloads_filter_movies
import nuvio.composeapp.generated.resources.downloads_filter_shows
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10 port f434ea60): filter completed downloads by type. */
enum class DownloadsTypeFilter { ALL, MOVIES, SHOWS }

@Composable
internal fun DownloadsTypeFilterRow(
    selected: DownloadsTypeFilter,
    movieCount: Int,
    showCount: Int,
    onSelected: (DownloadsTypeFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == DownloadsTypeFilter.ALL,
            onClick = { onSelected(DownloadsTypeFilter.ALL) },
            label = { Text(stringResource(Res.string.catalog_filter_all, movieCount + showCount)) },
        )
        FilterChip(
            selected = selected == DownloadsTypeFilter.MOVIES,
            onClick = { onSelected(DownloadsTypeFilter.MOVIES) },
            label = { Text(stringResource(Res.string.downloads_filter_movies, movieCount)) },
        )
        FilterChip(
            selected = selected == DownloadsTypeFilter.SHOWS,
            onClick = { onSelected(DownloadsTypeFilter.SHOWS) },
            label = { Text(stringResource(Res.string.downloads_filter_shows, showCount)) },
        )
    }
}
