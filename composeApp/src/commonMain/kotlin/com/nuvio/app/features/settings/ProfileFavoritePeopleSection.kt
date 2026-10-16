package com.nuvio.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.FavoritePeopleRepository
import com.nuvio.app.features.details.FavoritePerson
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.profile_insights_favorite_people_hint
import nuvio.composeapp.generated.resources.profile_insights_favorite_people_pin
import nuvio.composeapp.generated.resources.profile_insights_favorite_people_see_all
import nuvio.composeapp.generated.resources.profile_insights_favorite_people_title
import nuvio.composeapp.generated.resources.profile_insights_favorite_people_unpin
import org.jetbrains.compose.resources.stringResource

/**
 * Avix (ported from Yesnt10): favorite people of the active profile on Profile Insights.
 * A rail (pinned first) plus a "See all" sheet where favorites can be pinned/unpinned.
 * Hidden while the profile has no favorites.
 */
@Composable
internal fun ProfileFavoritePeopleSection(
    profileId: Int,
    isTablet: Boolean,
    onPersonClick: (FavoritePerson) -> Unit,
) {
    val state by remember(profileId) {
        FavoritePeopleRepository.ensureLoaded()
        FavoritePeopleRepository.uiState
    }.collectAsStateWithLifecycle()
    var showAll by remember(profileId) { mutableStateOf(false) }
    val people = state.displayPeople
    if (people.isEmpty()) return

    SettingsSection(
        title = stringResource(Res.string.profile_insights_favorite_people_title),
        isTablet = isTablet,
        actions = {
            TextButton(onClick = { showAll = true }) {
                Text(
                    text = stringResource(Res.string.profile_insights_favorite_people_see_all),
                    color = MaterialTheme.nuvio.colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(if (isTablet) 16.dp else 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            people.forEach { person ->
                FavoritePersonItem(
                    person = person,
                    isTablet = isTablet,
                    onClick = { onPersonClick(person) },
                    modifier = Modifier.width(if (isTablet) 96.dp else 82.dp),
                )
            }
        }
    }

    if (showAll) {
        FavoritePeopleSheet(
            people = people,
            pinnedIds = state.pinnedPersonIds.toSet(),
            isTablet = isTablet,
            onDismiss = { showAll = false },
            onPersonClick = { person ->
                showAll = false
                onPersonClick(person)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritePeopleSheet(
    people: List<FavoritePerson>,
    pinnedIds: Set<Int>,
    isTablet: Boolean,
    onDismiss: () -> Unit,
    onPersonClick: (FavoritePerson) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTablet) 24.dp else 18.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.profile_insights_favorite_people_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.profile_insights_favorite_people_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTablet) 112.dp else 92.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isTablet) 600.dp else 480.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items = people, key = { person -> person.tmdbId }) { person ->
                    FavoritePersonItem(
                        person = person,
                        isTablet = isTablet,
                        onClick = { onPersonClick(person) },
                        isPinned = person.tmdbId in pinnedIds,
                        onTogglePinned = { FavoritePeopleRepository.togglePinned(person.tmdbId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritePersonItem(
    person: FavoritePerson,
    isTablet: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    onTogglePinned: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val avatarSize = if (isTablet) 82.dp else 70.dp
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(avatarSize + 8.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(tokens.colors.surfaceElevated)
                    .border(1.dp, tokens.colors.borderSubtle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val photo = person.photo
                if (!photo.isNullOrBlank()) {
                    NuvioAsyncImage(
                        imageUrl = photo,
                        contentDescription = person.name,
                        modifier = Modifier.size(avatarSize),
                    )
                } else {
                    Text(
                        text = person.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (onTogglePinned != null || isPinned) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(if (isTablet) 28.dp else 26.dp)
                        .clip(CircleShape)
                        .then(if (onTogglePinned != null) Modifier.clickable(onClick = onTogglePinned) else Modifier),
                    color = tokens.colors.surface.copy(alpha = 0.92f),
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (isPinned) tokens.colors.accent.copy(alpha = 0.62f) else tokens.colors.borderSubtle,
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = stringResource(
                                if (isPinned) {
                                    Res.string.profile_insights_favorite_people_unpin
                                } else {
                                    Res.string.profile_insights_favorite_people_pin
                                },
                            ),
                            modifier = Modifier.size(15.dp),
                            tint = if (isPinned) tokens.colors.accent else tokens.colors.textSecondary,
                        )
                    }
                }
            }
        }
        Text(
            text = person.name,
            style = if (isTablet) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
        person.knownFor?.let { knownFor ->
            Text(
                text = knownFor,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textSecondary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
