package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize

interface PlayerGestureController {
    fun currentBrightness(): Float?
    fun setBrightness(level: Float): Float?
    fun currentVolume(): PlayerAudioLevel?
    fun setVolume(level: Float): PlayerAudioLevel?
}

data class PlayerAudioLevel(
    val fraction: Float,
    val isMuted: Boolean,
)

@Composable
expect fun LockPlayerToLandscape()

@Composable
expect fun FullscreenPlayerDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
)

@Composable
expect fun HidePlayerSystemBars()

@Composable
expect fun EnterImmersivePlayerMode(keepScreenAwake: Boolean)

@Composable
expect fun ManagePlayerPictureInPicture(
    isActive: Boolean,
    isPlaying: Boolean,
    videoSize: IntSize,
)

/**
 * Turns off automatic picture-in-picture right away, before the player screen is removed, so that
 * leaving the player with Back never starts PiP (Android 12+ auto-enter) during the exit.
 */
expect fun disablePlayerPictureInPictureAutoEnter()

/** PiP may only start on its own (Home / recents) while an active player is playing and not exiting. */
internal fun isPictureInPictureAutoEnterAllowed(
    isActive: Boolean,
    isPlaying: Boolean,
    isExiting: Boolean,
): Boolean = isActive && isPlaying && !isExiting

@Composable
expect fun rememberIsInPictureInPicture(): Boolean

@Composable
fun rememberPlayerGestureController(restoreBrightness: Boolean): PlayerGestureController? {
    val controller = rememberPlatformPlayerGestureController() ?: return null
    DisposableEffect(controller, restoreBrightness) {
        if (restoreBrightness) {
            PlayerSettingsStorage.loadPlaybackBrightness()
                ?.takeIf { it in 0f..1f }
                ?.let(controller::setBrightness)
        }
        onDispose {}
    }
    return remember(controller) {
        object : PlayerGestureController by controller {
            override fun setBrightness(level: Float): Float? {
                if (!level.isFinite()) return null
                return controller.setBrightness(level.coerceIn(0f, 1f))
                    ?.also(PlayerSettingsStorage::savePlaybackBrightness)
            }
        }
    }
}

@Composable
internal expect fun rememberPlatformPlayerGestureController(): PlayerGestureController?
