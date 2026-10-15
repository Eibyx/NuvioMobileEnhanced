package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_engine_option_auto
import nuvio.composeapp.generated.resources.player_engine_option_auto_desc
import nuvio.composeapp.generated.resources.player_engine_option_exo_desc
import nuvio.composeapp.generated.resources.player_engine_option_libmpv_desc
import nuvio.composeapp.generated.resources.player_engine_option_in_use
import nuvio.composeapp.generated.resources.player_engine_picker_title
import org.jetbrains.compose.resources.stringResource

/**
 * Avix: in-player engine picker (Automatic / ExoPlayer / libmpv (mpvRx)).
 * [selected] is the user's choice for this playback; [activeEngine] is the engine actually running.
 */
@Composable
internal fun PlayerEnginePickerDialog(
    selected: AndroidPlaybackEngine,
    activeEngine: AndroidPlaybackEngine?,
    defaultEngine: AndroidPlaybackEngine,
    onSelected: (AndroidPlaybackEngine) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = MaterialTheme.nuvio.colors.accent
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF0161616))
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = stringResource(Res.string.player_engine_picker_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Spacer(Modifier.size(8.dp))
            listOf(
                Triple(
                    AndroidPlaybackEngine.Auto,
                    stringResource(Res.string.player_engine_option_auto),
                    stringResource(Res.string.player_engine_option_auto_desc, defaultEngine.label),
                ),
                Triple(
                    AndroidPlaybackEngine.ExoPlayer,
                    AndroidPlaybackEngine.ExoPlayer.label,
                    stringResource(Res.string.player_engine_option_exo_desc),
                ),
                Triple(
                    AndroidPlaybackEngine.Libmpv,
                    AndroidPlaybackEngine.Libmpv.label,
                    stringResource(Res.string.player_engine_option_libmpv_desc),
                ),
            ).forEach { (engine, label, description) ->
                val isSelected = engine == selected
                val inUse = engine != AndroidPlaybackEngine.Auto && engine == activeEngine
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (isSelected) onDismiss() else onSelected(engine) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (inUse) "$label · ${stringResource(Res.string.player_engine_option_in_use)}" else label,
                            color = if (isSelected) accent else Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            text = description,
                            color = Color.White.copy(alpha = 0.66f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(start = 12.dp).size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
