package com.nuvio.app.features.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioInputField
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.gif_avatar_search_action
import nuvio.composeapp.generated.resources.gif_avatar_search_api_key
import nuvio.composeapp.generated.resources.gif_avatar_search_change_key
import nuvio.composeapp.generated.resources.gif_avatar_search_close
import nuvio.composeapp.generated.resources.gif_avatar_search_error
import nuvio.composeapp.generated.resources.gif_avatar_search_key_help
import nuvio.composeapp.generated.resources.gif_avatar_search_no_results
import nuvio.composeapp.generated.resources.gif_avatar_search_query
import nuvio.composeapp.generated.resources.gif_avatar_search_title
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10 port): pick a GIPHY GIF as the profile's custom avatar URL. */
@Composable
internal fun GifAvatarSearchDialog(
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    LaunchedEffect(Unit) { GifAvatarSearchSettings.ensureLoaded() }
    val savedKey by GifAvatarSearchSettings.apiKey.collectAsStateWithLifecycle()
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    var editingKey by remember(savedKey) { mutableStateOf(savedKey.isBlank()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GifAvatarResult>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        if (keyInput != savedKey) GifAvatarSearchSettings.setApiKey(keyInput)
        if (keyInput.isBlank() || query.isBlank()) return
        loading = true
        failed = false
        scope.launch {
            GifAvatarSearchService.search(query, keyInput)
                .onSuccess { results = it }
                .onFailure {
                    results = null
                    failed = true
                }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.gif_avatar_search_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (editingKey) {
                    Text(
                        text = stringResource(Res.string.gif_avatar_search_key_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NuvioInputField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = stringResource(Res.string.gif_avatar_search_api_key),
                    )
                } else {
                    TextButton(onClick = {
                        keyInput = ""
                        editingKey = true
                    }) {
                        Text(stringResource(Res.string.gif_avatar_search_change_key))
                    }
                }
                NuvioInputField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.gif_avatar_search_query),
                    trailingContent = {
                        TextButton(onClick = ::runSearch, enabled = !loading) {
                            Text(stringResource(Res.string.gif_avatar_search_action))
                        }
                    },
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val current = results
                    when {
                        loading -> CircularProgressIndicator()
                        failed -> Text(stringResource(Res.string.gif_avatar_search_error))
                        current != null && current.isEmpty() -> Text(stringResource(Res.string.gif_avatar_search_no_results))
                        current != null -> LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(current, key = { it.id }) { gif ->
                                NuvioAsyncImage(
                                    imageUrl = gif.previewUrl,
                                    contentDescription = gif.title,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelected(gif.avatarUrl) },
                                    contentScale = ContentScale.Crop,
                                    animateIfPossible = true,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.gif_avatar_search_close)) }
        },
    )
}
