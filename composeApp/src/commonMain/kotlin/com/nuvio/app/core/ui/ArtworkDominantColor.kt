package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.kmpalette.extensions.painter.rememberPainterDominantColorState
import com.kmpalette.DominantColorState
import com.kmpalette.rememberDominantColorState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import com.nuvio.app.features.details.components.loadedBackdropImageBitmap

private const val PALETTE_SAMPLE_PX = 96
private const val CACHE_CAPACITY = 256

private val NoTint = Color.Transparent

object ArtworkDominantColorCache {
    private val entries = androidx.compose.runtime.mutableStateMapOf<String, Color>()
    private val order = ArrayDeque<String>()

    fun get(url: String): Color? {
        return entries[url]
    }

    fun put(url: String, color: Color) {
        order.remove(url)
        order.addLast(url)
        entries[url] = color
        if (entries.size > CACHE_CAPACITY) {
            entries.remove(order.removeFirst())
        }
    }
}

@Composable
fun rememberArtworkDominantColor(
    url: String?, enabled: Boolean = true, painter: Painter? = null,
    bitmap: ImageBitmap? = null, allowFetch: Boolean = false,
): Color? {
    val key = url?.takeIf { it.isNotBlank() }
    var color by remember(key) { mutableStateOf(key?.let(ArtworkDominantColorCache::get)) }
    val platformContext = LocalPlatformContext.current

    val bitmapState = rememberDominantColorState(
        defaultColor = NoTint,
        defaultOnColor = NoTint,
        isSwatchValid = isBrightDominantSwatch,
    )
    val painterState = rememberPainterDominantColorState(
        defaultColor = NoTint,
        defaultOnColor = NoTint,
        isSwatchValid = isBrightDominantSwatch,
    )

    LaunchedEffect(key, enabled, painter, bitmap, allowFetch) {
        if (!enabled || key == null || color != null) return@LaunchedEffect
        if (painter != null || bitmap != null) {
            try {
                if (bitmap != null) bitmapState.updateFrom(bitmap) else painterState.updateFrom(painter!!)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Palette extraction must never turn a successfully displayed image into a UI failure.
                return@LaunchedEffect
            }
            val resolved = (if (bitmap != null) bitmapState.color else painterState.color).brightenedDominant(fallback = NoTint)
            ArtworkDominantColorCache.put(key, resolved)
            color = resolved
        } else if (allowFetch) {
            color = sampleArtworkDominantColor(key, platformContext, bitmapState, painterState)
        }
    }

    return (key?.let(ArtworkDominantColorCache::get) ?: color)?.takeIf { it != NoTint }
}

@Composable
fun PrefetchArtworkDominantColors(urls: List<String>) {
    val platformContext = LocalPlatformContext.current
    val bitmapState = rememberDominantColorState(
        defaultColor = NoTint,
        defaultOnColor = NoTint,
        isSwatchValid = isBrightDominantSwatch,
    )
    val painterState = rememberPainterDominantColorState(
        defaultColor = NoTint,
        defaultOnColor = NoTint,
        isSwatchValid = isBrightDominantSwatch,
    )
    LaunchedEffect(urls) {
        urls.filter { it.isNotBlank() }.distinct().forEach { url ->
            if (ArtworkDominantColorCache.get(url) == null) {
                sampleArtworkDominantColor(url, platformContext, bitmapState, painterState)
            }
        }
    }
}

private suspend fun sampleArtworkDominantColor(
    url: String,
    platformContext: PlatformContext,
    bitmapState: DominantColorState<ImageBitmap>,
    painterState: DominantColorState<Painter>,
): Color? {
    val request = ImageRequest.Builder(platformContext)
        .data(url)
        .size(PALETTE_SAMPLE_PX, PALETTE_SAMPLE_PX)
        .memoryCacheKey("palette:$url")
        .build()
    val result = SingletonImageLoader.get(platformContext).execute(request) as? SuccessResult ?: return null
    val bitmap = loadedBackdropImageBitmap(result)
    val extracted = runCatching {
        if (bitmap != null) {
            bitmapState.updateFrom(bitmap)
            bitmapState.color
        } else {
            painterState.updateFrom(result.image.asPainter(platformContext))
            painterState.color
        }
    }.getOrNull() ?: return null
    val resolved = extracted.brightenedDominant(fallback = NoTint)
    ArtworkDominantColorCache.put(url, resolved)
    return resolved
}
