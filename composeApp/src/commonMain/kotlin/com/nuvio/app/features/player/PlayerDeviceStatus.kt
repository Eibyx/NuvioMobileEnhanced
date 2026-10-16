package com.nuvio.app.features.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.getBoolean
import com.nuvio.app.core.avix.putBoolean
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_status_network_cellular
import nuvio.composeapp.generated.resources.player_status_network_offline
import nuvio.composeapp.generated.resources.player_status_network_wifi
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10 port): discreet clock, battery and network pill shown while the controls are hidden. */
internal enum class PlayerDeviceNetworkType { Wifi, Cellular, Offline, Unknown }

internal data class PlayerDeviceStatus(
    val batteryPercent: Int? = null,
    val batteryCharging: Boolean = false,
    val networkType: PlayerDeviceNetworkType = PlayerDeviceNetworkType.Unknown,
)

/** Battery and network state; refreshed by the platform while the overlay is on screen. */
@Composable
internal expect fun rememberPlayerDeviceStatus(): PlayerDeviceStatus

internal fun playerClockLabel(
    nowEpochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val now = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone)
    return "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
}

internal fun playerBatteryLabel(percent: Int?): String? = percent?.takeIf { it in 0..100 }?.let { "$it%" }

/** Device-local switch, off by default (docs/integration/DECISIONS.md D-5). */
object PlayerStatusOverlayPreference {
    private const val KEY = "player.status_overlay"
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private var loaded = false

    init {
        com.nuvio.app.core.avix.AvixPreferencesReload.register {
            loaded = false
            ensureLoaded()
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _enabled.value = AvixLocalStore.getBoolean(KEY) ?: false
    }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        _enabled.value = value
        AvixLocalStore.putBoolean(KEY, value)
    }
}

@Composable
internal fun PlayerDeviceStatusPill(
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    showEndTime: Boolean,
    modifier: Modifier = Modifier,
) {
    val status = rememberPlayerDeviceStatus()
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L - nowMs % 1_000L)
            nowMs = Clock.System.now().toEpochMilliseconds()
        }
    }
    val endLabel = if (showEndTime) {
        playbackEndClockLabel(positionMs, durationMs, playbackSpeed, nowEpochMs = nowMs)
    } else {
        null
    }
    val textColor = Color.White.copy(alpha = 0.92f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status.networkType != PlayerDeviceNetworkType.Unknown) {
                val (icon, label) = when (status.networkType) {
                    PlayerDeviceNetworkType.Wifi -> Icons.Rounded.Wifi to stringResource(Res.string.player_status_network_wifi)
                    PlayerDeviceNetworkType.Cellular -> Icons.Rounded.SignalCellularAlt to stringResource(Res.string.player_status_network_cellular)
                    else -> Icons.Rounded.WifiOff to stringResource(Res.string.player_status_network_offline)
                }
                StatusItem(icon, label, textColor)
            }
            Text(
                text = playerClockLabel(nowMs),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1,
            )
            playerBatteryLabel(status.batteryPercent)?.let { battery ->
                StatusItem(
                    icon = if (status.batteryCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                    text = battery,
                    color = textColor,
                )
            }
            endLabel?.let { StatusItem(Icons.Rounded.AccessTime, it, textColor) }
        }
    }
}

@Composable
private fun StatusItem(icon: ImageVector, text: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1,
        )
    }
}
