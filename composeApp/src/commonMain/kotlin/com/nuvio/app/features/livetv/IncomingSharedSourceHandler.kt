package com.nuvio.app.features.livetv

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.p2p.P2pSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.shared_source_add
import nuvio.composeapp.generated.resources.shared_source_add_playlist_message
import nuvio.composeapp.generated.resources.shared_source_add_playlist_title
import nuvio.composeapp.generated.resources.shared_source_cancel
import nuvio.composeapp.generated.resources.shared_source_p2p_disabled
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Avix (Yesnt10 port): reacts to content opened in or shared to the app.
 * Playlists are added to Live TV only after the user confirms (any app can send an intent);
 * direct links and magnets open the player, magnets only when P2P is enabled.
 */
@Composable
fun IncomingSharedSourceHandler(
    onPlayDirect: (url: String, title: String) -> Unit,
    onPlayMagnet: (IncomingSharedSource.Magnet) -> Unit,
    onOpenLiveTv: () -> Unit,
) {
    var pendingPlaylist by remember { mutableStateOf<IncomingSharedSource?>(null) }
    LaunchedEffect(Unit) {
        IncomingSharedSourceRepository.requests.collect { request ->
            when (request) {
                is IncomingSharedSource.PlaylistUrl, is IncomingSharedSource.PlaylistData -> pendingPlaylist = request
                is IncomingSharedSource.DirectStream -> onPlayDirect(request.url, request.title)
                is IncomingSharedSource.Magnet -> {
                    P2pSettingsRepository.ensureLoaded()
                    if (P2pSettingsRepository.isVisible && P2pSettingsRepository.uiState.value.p2pEnabled) {
                        onPlayMagnet(request)
                    } else {
                        NuvioToastController.show(getString(Res.string.shared_source_p2p_disabled))
                    }
                }
            }
        }
    }

    val pending = pendingPlaylist ?: return
    val label = when (pending) {
        is IncomingSharedSource.PlaylistUrl -> pending.url
        is IncomingSharedSource.PlaylistData -> pending.fileName
        else -> ""
    }
    AlertDialog(
        onDismissRequest = { pendingPlaylist = null },
        title = { Text(stringResource(Res.string.shared_source_add_playlist_title)) },
        text = { Text(stringResource(Res.string.shared_source_add_playlist_message, label)) },
        confirmButton = {
            TextButton(onClick = {
                when (pending) {
                    is IncomingSharedSource.PlaylistUrl -> LiveTvRepository.addPlaylistUrl(name = null, url = pending.url)
                    is IncomingSharedSource.PlaylistData ->
                        LiveTvRepository.addLocalPlaylist(name = pending.fileName, fileName = pending.fileName, content = pending.data)
                    else -> Unit
                }
                pendingPlaylist = null
                onOpenLiveTv()
            }) { Text(stringResource(Res.string.shared_source_add)) }
        },
        dismissButton = {
            TextButton(onClick = { pendingPlaylist = null }) { Text(stringResource(Res.string.shared_source_cancel)) }
        },
    )
}
