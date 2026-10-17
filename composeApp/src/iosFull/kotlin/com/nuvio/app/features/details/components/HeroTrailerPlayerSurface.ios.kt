package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitViewController
import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.NuvioPlayerBridgeFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val HERO_TRAILER_IOS_TAG = "HeroTrailerIos"

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HeroTrailerPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    playWhenReady: Boolean,
    muted: Boolean,
    modifier: Modifier,
    startPositionMs: Long,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
    onUnavailable: () -> Unit,
    onPositionUpdate: (Long) -> Unit,
) {
    val latestOnUnavailable = rememberUpdatedState(onUnavailable)
    val latestOnReady = rememberUpdatedState(onReady)
    val latestOnEnded = rememberUpdatedState(onEnded)
    val latestOnError = rememberUpdatedState(onError)
    val latestOnPositionUpdate = rememberUpdatedState(onPositionUpdate)
    val bridge = remember(sourceUrl, sourceAudioUrl) { NuvioPlayerBridgeFactory.create() }

    if (bridge == null) {
        LaunchedEffect(Unit) { latestOnError.value() }
        return
    }

    var presentable by remember(bridge) { mutableStateOf(false) }
    LaunchedEffect(bridge, sourceUrl, sourceAudioUrl) {
        latestOnUnavailable.value()
        runCatching {
            bridge.setEmbeddedPreviewMode(true)
            bridge.setResizeMode(1)
            bridge.setMuted(muted)
            bridge.loadFileWithAudio(
                videoUrl = sourceUrl,
                audioUrl = sourceAudioUrl,
                headersJson = null,
                subtitlesJson = null,
            )
            if (playWhenReady) bridge.play() else bridge.pause()
        }.onFailure { error ->
            Logger.w(HERO_TRAILER_IOS_TAG, error) { "Failed to load iOS hero trailer preview" }
            latestOnError.value()
        }
    }

    LaunchedEffect(bridge, playWhenReady) {
        runCatching {
            if (playWhenReady) bridge.play() else bridge.pause()
        }.onFailure { error ->
            Logger.w(HERO_TRAILER_IOS_TAG, error) { "Failed to update iOS hero trailer playback state" }
            latestOnError.value()
        }
    }

    LaunchedEffect(bridge, muted) {
        runCatching { bridge.setMuted(muted) }
            .onFailure { error ->
                Logger.w(HERO_TRAILER_IOS_TAG, error) { "Failed to update iOS hero trailer mute state" }
            }
    }

    LaunchedEffect(bridge, sourceUrl, sourceAudioUrl) {
        var readyReported = false
        var lastErrorMessage: String? = null
        while (isActive) {
            val errorMessage = runCatching { bridge.getErrorMessage().ifBlank { null } }.getOrNull()
            if (errorMessage != null && errorMessage != lastErrorMessage) {
                lastErrorMessage = errorMessage
                latestOnError.value()
                return@LaunchedEffect
            }

            if (!readyReported) {
                val hasVideo = runCatching {
                    bridge.getVideoWidth() > 0 && bridge.getVideoHeight() > 0
                }.getOrDefault(false)
                val isPlaying = runCatching { bridge.getIsPlaying() }.getOrDefault(false)
                if (hasVideo && isPlaying) {
                    readyReported = true
                    // Only safe to seek once the file is actually loaded — seeking right after
                    // loadFileWithAudio() is issued is silently ignored since nothing is ready yet.
                    if (startPositionMs > 0L) {
                        runCatching { bridge.seekTo(startPositionMs) }
                    }
                    presentable = true
                    latestOnReady.value()
                }
            }

            if (runCatching { bridge.getIsEnded() }.getOrDefault(false)) {
                latestOnEnded.value()
                return@LaunchedEffect
            }
            if (readyReported) {
                runCatching { bridge.getPositionMs() }.getOrNull()?.let(latestOnPositionUpdate.value)
            }
            delay(250L)
        }
    }

    DisposableEffect(bridge) {
        onDispose {
            latestOnUnavailable.value()
            runCatching {
                bridge.pause()
                latestOnPositionUpdate.value(bridge.getPositionMs())
                bridge.destroy()
            }.onFailure { error ->
                Logger.w(HERO_TRAILER_IOS_TAG, error) { "Failed to dispose iOS hero trailer preview" }
            }
        }
    }

    Box(modifier = modifier.graphicsLayer { alpha = if (presentable) 1f else 0f }.background(Color.Black)) {
        UIKitViewController(
            factory = { bridge.createPlayerViewController() },
            modifier = Modifier.fillMaxSize(),
            interactive = false,
        )
    }
}
