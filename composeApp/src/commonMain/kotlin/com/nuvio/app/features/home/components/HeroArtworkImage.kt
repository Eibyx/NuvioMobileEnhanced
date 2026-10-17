package com.nuvio.app.features.home.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import com.nuvio.app.features.home.MetaPreview

internal fun heroArtworkCandidates(item: MetaPreview, artworkUrl: String?): List<String> =
    listOf(artworkUrl, item.rawLandscapePosterUrl, item.banner, item.rawPosterUrl, item.poster)
        .mapNotNull { value -> value?.trim()?.takeIf { (it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)) && runCatching { io.ktor.http.Url(it).host.isNotBlank() }.getOrDefault(false) } }
        .distinct()

/** One visible request, sized by AsyncImage's constraints. Errors advance a finite list
 * of artwork for this content; no retained image from a previous page and no enqueue prefetch.
 */
@Composable
internal fun HeroArtworkImage(
    item: MetaPreview,
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val candidates = remember(item, artworkUrl) { heroArtworkCandidates(item, artworkUrl) }
    val network by com.nuvio.app.core.network.NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    var failed by remember(item.id, item.type, candidates, network.condition) { mutableStateOf(emptySet<String>()) }
    val url = candidates.firstOrNull { it !in failed }
    val trace = remember(url) { HeroImageDiagnostics.begin(url) }
    val context = LocalPlatformContext.current
    val fallback = when (url) {
        item.landscapePoster -> item.rawLandscapePosterUrl
        item.poster -> item.rawPosterUrl
        else -> null
    }
    val model = remember(url, context, fallback) {
        ImageRequest.Builder(context).data(url).apply {
            if (trace != null) listener(
                onStart = { trace.event("requestStart") },
                onCancel = { trace.event("cancel") },
            )
            if (!fallback.isNullOrBlank() && fallback != url) {
                memoryCacheKeyExtras(mapOf(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallback))
            }
        }.build()
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = if (trace == null) modifier else modifier.drawWithContent {
            drawContent()
            trace.drawSubmitted()
        },
        alignment = alignment,
        contentScale = contentScale,
        onSuccess = { success ->
            trace?.apply {
                ready = true
                val result = success.result
                event("success", "source=${result.dataSource} decoded=${result.image.width}x${result.image.height} memoryKey=${result.memoryCacheKey?.key?.hashCode()?.toUInt()?.toString(16)}")
            }
        },
        onError = {
            trace?.event("error", it.result.throwable::class.simpleName.orEmpty())
            if (url != null) failed = failed + url + listOfNotNull(fallback)
        },
    )
}
