package com.nuvio.app.features.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nuvio.app.core.ui.artworkDrawMarker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.LocalDynamicArtworkBackgroundColor
import com.nuvio.app.core.ui.rememberDynamicArtworkBackgroundEnabled
import com.nuvio.app.core.ui.heroStretchHeight
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.heroStretchZoom
import com.nuvio.app.features.details.HeroTrailerAudioState
import com.nuvio.app.features.details.HeroTrailerSurface
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.components.DetailHeroMetaBadge
import com.nuvio.app.features.details.components.HeroTrailerPlayerSurface
import com.nuvio.app.features.details.selectHeroTrailer
import com.nuvio.app.features.details.youtubePlaybackUrl
import com.nuvio.app.features.home.HomeHeroArtworkSource
import com.nuvio.app.features.home.HomeHeroStyle
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.player.MiniPlayerController
import com.nuvio.app.features.trailer.TrailerPlaybackResolver
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private const val HERO_BACKGROUND_PARALLAX = 0.055f
private const val HERO_BACKGROUND_SCALE = 1.14f
private const val HERO_CONTENT_PARALLAX = 0.18f
private const val HERO_SCROLL_PARALLAX = 0.3f
private const val HERO_SCROLL_DOWN_SCALE_MULTIPLIER = 0.0001f
private const val HERO_SCROLL_UP_SCALE_MULTIPLIER = 0.002f
private const val HERO_SCROLL_MAX_SCALE = 1.3f
private const val HERO_SWIPE_THRESHOLD_FRACTION = 0.16f
private const val HERO_SWIPE_VELOCITY_THRESHOLD = 300f
private const val HERO_AUTO_SCROLL_INTERVAL_MS = 8_000L
private const val MOBILE_HERO_VIEWPORT_RATIO = 0.82f
private const val MOBILE_PORTRAIT_HERO_WIDTH_RATIO = 1.5f
private const val MOBILE_HERO_MIN_HEIGHT_DP = 360f
private const val MOBILE_HERO_MAX_HEIGHT_DP = 760f
private const val TABLET_LANDSCAPE_HERO_HEIGHT_MULTIPLIER = 1.5f

internal object HomeHeroTrailerPlaybackController {
    private var stopCallback: (() -> Unit)? = null

    fun register(stop: () -> Unit) {
        stopCallback = stop
    }

    fun unregister(stop: () -> Unit) {
        if (stopCallback === stop) stopCallback = null
    }

    fun forceStop() {
        stopCallback?.invoke()
    }
}

private object HomeHeroTrailerPlaybackPositionStore {
    private val positions = linkedMapOf<String, Long>()

    fun get(key: String): Long = positions[key] ?: 0L

    fun set(key: String, positionMs: Long) {
        if (positionMs > 0L) {
            positions.remove(key)
            positions[key] = positionMs
            if (positions.size > 32) positions.remove(positions.keys.first())
        }
    }

    fun clear(key: String) {
        positions.remove(key)
    }
}

internal const val TMDB_POSTER_HEIGHT_RATIO = 1.5f

internal const val BACKDROP_HEIGHT_RATIO = 9f / 16f

private const val CARD_WIDE_MAX_VIEWPORT_FRACTION = 0.72f
internal val HERO_CARD_CORNER_RADIUS = 28.dp
internal val HERO_CARD_HORIZONTAL_PADDING = 12.dp
internal val HERO_CARD_TOP_PADDING = 8.dp

internal fun heroUsesWideArtwork(
    maxWidth: Dp,
    viewportHeight: Dp?,
    isTablet: Boolean,
): Boolean = isTablet || (viewportHeight != null && maxWidth > viewportHeight)

internal fun cardHeroHeight(
    maxWidth: Dp,
    viewportHeight: Dp?,
    isTablet: Boolean,
): Dp = if (heroUsesWideArtwork(maxWidth, viewportHeight, isTablet)) {
    val wideHeight = maxWidth * BACKDROP_HEIGHT_RATIO
    if (viewportHeight != null) {
        minOf(wideHeight, viewportHeight * CARD_WIDE_MAX_VIEWPORT_FRACTION)
    } else {
        wideHeight
    }
} else {
    maxWidth * TMDB_POSTER_HEIGHT_RATIO
}

internal data class HomeHeroLayout(
    val isTablet: Boolean,
    val heroHeight: Dp,
    val contentMaxWidth: Dp,
    val contentWidthFraction: Float,
    val contentHorizontalPadding: Dp,
    val contentVerticalPadding: Dp,
    val bottomFadeHeight: Dp,
    val logoWidthFraction: Float,
)

@Composable
internal fun rememberHeroArtworkContinuityAllowed(url: String?, complete: Boolean, failed: Boolean): Boolean {
    var allowed by remember(url) { mutableStateOf(!failed) }
    LaunchedEffect(url, complete, failed) {
        allowed = !failed
        // Only a pending replacement expires. A valid current image can underlay a
        // later transition even after the user has left this page idle for a while.
        if (!complete && !failed) {
            delay(15_000)
            allowed = false
        }
    }
    // Readiness is immediately usable during this composition, without waiting for
    // the effect to reset an expired pending-request allowance on the next frame.
    return !failed && (complete || allowed)
}

@Composable
internal fun HomeHeroSection(
    items: List<MetaPreview>,
    modifier: Modifier = Modifier,
    viewportHeight: Dp? = null,
    mobileBelowSectionHeightHint: Dp? = null,
    heroStyle: HomeHeroStyle = HomeHeroStyle.FULL_BLEED,
    listState: LazyListState? = null,
    stretchPx: () -> Float = { 0f },
    trailerPlaybackEnabled: Boolean = false,
    trailerStartDelaySeconds: Int = 0,
    trailerStartUnmuted: Boolean = false,
    onItemClick: ((MetaPreview) -> Unit)? = null,
    onActiveArtworkChange: ((String?) -> Unit)? = null,
    autoScrollEnabled: Boolean = true,
    heroOptions: com.nuvio.app.features.home.AvixHeroOptions = com.nuvio.app.features.home.AvixHeroOptions(),
) {
    if (items.isEmpty()) return

    val pagerState = key(items.size) {
        rememberPagerState(
            initialPage = if (items.size > 1) {
                Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % items.size
            } else {
                0
            },
            pageCount = { if (items.size > 1) Int.MAX_VALUE else 1 },
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val autoScrollPage = pagerState.settledPage
    val effectiveTrailerPlaybackEnabled = trailerPlaybackEnabled &&
        AppFeaturePolicy.heroTrailerPlaybackSupported &&
        AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP &&
        !MiniPlayerController.isActive

    LaunchedEffect(pagerState) {
        pagerState.scrollToPage(pagerState.currentPage)
    }

    ScreenActivityEffect(pagerState) { active ->
        if (!active) {
            pagerState.stopScroll(MutatePriority.PreventUserInput)
            pagerState.scrollToPage(pagerState.currentPage)
        }
    }

    ScreenActivityEffect(autoScrollPage, items.size, effectiveTrailerPlaybackEnabled, autoScrollEnabled) { active ->
        if (!active || !autoScrollEnabled || items.size <= 1 || effectiveTrailerPlaybackEnabled) return@ScreenActivityEffect
        delay(HERO_AUTO_SCROLL_INTERVAL_MS)
        while (pagerState.isScrollInProgress) {
            delay(100L)
        }

        val nextPage = pagerState.currentPage + 1
        pagerState.animateScrollToPage(nextPage)
    }

    val isCardStyle = heroStyle == HomeHeroStyle.CARD
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cardInsetModifier = if (isCardStyle) {
        Modifier.padding(
            top = statusBarTopPadding + HERO_CARD_TOP_PADDING,
            start = HERO_CARD_HORIZONTAL_PADDING,
            end = HERO_CARD_HORIZONTAL_PADDING,
        )
    } else {
        Modifier
    }
    val heroShape = if (isCardStyle) {
        RoundedCornerShape(HERO_CARD_CORNER_RADIUS)
    } else {
        RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(cardInsetModifier)
                .homeHeroPagerGesture(
                    pagerState = pagerState,
                    itemCount = items.size,
                    coroutineScope = coroutineScope,
                )
                .clip(heroShape),
        ) {
            val baseLayout = homeHeroLayout(
                maxWidthDp = maxWidth.value,
                viewportHeightDp = viewportHeight?.value,
                mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHint?.value,
            )
            val layout = if (isCardStyle) {
                baseLayout.copy(
                    heroHeight = cardHeroHeight(
                        maxWidth = maxWidth,
                        viewportHeight = viewportHeight,
                        isTablet = baseLayout.isTablet,
                    ),
                )
            } else {
                baseLayout
            }
            val effectiveArtworkSource = when {
                !isCardStyle && heroOptions.posterArtwork &&
                    !heroUsesWideArtwork(maxWidth, viewportHeight, baseLayout.isTablet) -> HomeHeroArtworkSource.POSTER
                !isCardStyle -> HomeHeroArtworkSource.BACKDROP
                heroUsesWideArtwork(maxWidth, viewportHeight, baseLayout.isTablet) ->
                    HomeHeroArtworkSource.BACKDROP
                else -> HomeHeroArtworkSource.POSTER
            }
            val artworkParallax = if (isCardStyle) 0f else HERO_BACKGROUND_PARALLAX
            val artworkBaseScale = if (isCardStyle) 1f else HERO_BACKGROUND_SCALE
            val heroWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val heroHeightPx = with(LocalDensity.current) { layout.heroHeight.toPx() }
            val scrollOffsetPx by remember(listState, heroHeightPx) {
                derivedStateOf {
                    when {
                        listState == null -> 0f
                        listState.firstVisibleItemIndex > 0 -> heroHeightPx
                        else -> listState.firstVisibleItemScrollOffset.toFloat()
                    }
                }
            }
            val isHeroScrolledAway by remember(listState, heroHeightPx) {
                derivedStateOf { scrollOffsetPx >= heroHeightPx * 0.6f }
            }
            val currentPage = pagerState.currentPage
            val visiblePages = remember(currentPage, pagerState.pageCount, items.size) {
                heroVisiblePages(currentPage, pagerState.pageCount)
                    .map { page -> HeroPageLayer(page = page, itemIndex = page % items.size) }
            }
            val currentItem = items[currentPage % items.size]

            // The composed neighbours are the prefetch: never enqueue ORIGINAL-sized duplicates.
            val platformContext = LocalPlatformContext.current
            var fallbackMeta by remember(currentItem.type, currentItem.id) {
                mutableStateOf(MetaDetailsRepository.peek(currentItem.type, currentItem.id))
            }
            val catalogArtworkUrl = heroArtworkUrl(currentItem, effectiveArtworkSource)
            LaunchedEffect(currentItem.type, currentItem.id, catalogArtworkUrl) {
                if (catalogArtworkUrl == null) {
                    fallbackMeta = try {
                        MetaDetailsRepository.fetch(currentItem.type, currentItem.id, includeEpisodes = false)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Throwable) { null }
                }
            }
            fun artworkFor(item: MetaPreview): String? = heroArtworkUrl(item, effectiveArtworkSource)
                ?: fallbackMeta?.takeIf { item.type == currentItem.type && item.id == currentItem.id }
                    ?.let { it.background?.takeIf(String::isNotBlank) ?: it.poster?.takeIf(String::isNotBlank) }
            val currentArtworkUrl = artworkFor(currentItem)
            val activeArtworkUrl = artworkFor(items[pagerState.targetPage % items.size])
            LaunchedEffect(onActiveArtworkChange, activeArtworkUrl) {
                onActiveArtworkChange?.invoke(activeArtworkUrl)
            }
            var activeArtworkComplete by remember(currentArtworkUrl) { mutableStateOf(false) }
            var activeArtworkFailed by remember(currentArtworkUrl) { mutableStateOf(currentArtworkUrl.isNullOrBlank()) }
            var lastValidArtwork by remember(effectiveArtworkSource) {
                mutableStateOf<Pair<String, androidx.compose.ui.graphics.painter.Painter>?>(null)
            }
            val continuityAllowed = rememberHeroArtworkContinuityAllowed(currentArtworkUrl, activeArtworkComplete, activeArtworkFailed)
            LaunchedEffect(currentArtworkUrl) {
                com.nuvio.app.core.ui.ImagePerformance.mark("home.artworkUrl", currentArtworkUrl.orEmpty())
            }

            val dynamicColorsEnabled = rememberDynamicArtworkBackgroundEnabled()
            // One painter per resource, even when two visual layers show the same item.
            val artworkPainters = mutableMapOf<String, coil3.compose.AsyncImagePainter>()
            visiblePages.map { items[it.itemIndex] }
                .distinctBy { artworkFor(it) }
                .forEach { item ->
                    val url = artworkFor(item) ?: return@forEach
                    key(url) {
                        var started by remember { mutableStateOf(false) }
                        val shouldStart = started || url == currentArtworkUrl || activeArtworkComplete || activeArtworkFailed
                        if (shouldStart) {
                            androidx.compose.runtime.SideEffect { started = true }
                            val request = remember(url, item.rawPosterUrl, heroWidthPx, heroHeightPx) {
                                ImageRequest.Builder(platformContext).data(url)
                                    .size(heroWidthPx.toInt().coerceAtLeast(1), heroHeightPx.toInt().coerceAtLeast(1))
                                    .scale(coil3.size.Scale.FILL)
                                    .precision(coil3.size.Precision.INEXACT)
                                    .apply {
                                        val fallback = item.rawPosterUrl ?: item.banner?.takeIf { it.isNotBlank() } ?: item.poster?.takeIf { it.isNotBlank() }
                                        if (!fallback.isNullOrBlank() && fallback != url) {
                                            extras[com.nuvio.app.core.poster.CustomPosterFallbackInterceptor.REQUEST_FALLBACK_URL] = fallback
                                        }
                                    }.build()
                            }
                            val painter = coil3.compose.rememberAsyncImagePainter(request, contentScale = ContentScale.Crop)
                            val state by painter.state.collectAsStateWithLifecycle()
                            val loaded = state as? coil3.compose.AsyncImagePainter.State.Success
                            com.nuvio.app.core.ui.rememberArtworkDominantColor(
                                url, enabled = dynamicColorsEnabled, painter = loaded?.painter,
                                bitmap = remember(loaded, dynamicColorsEnabled) {
                                    if (dynamicColorsEnabled) loaded?.result?.let { com.nuvio.app.features.details.components.loadedBackdropImageBitmap(it) } else null
                                },
                            )
                            LaunchedEffect(state, currentArtworkUrl) {
                                if (url == currentArtworkUrl) {
                                    when (state) {
                                        is coil3.compose.AsyncImagePainter.State.Success -> {
                                            lastValidArtwork = url to loaded!!.painter
                                            activeArtworkComplete = true
                                        }
                                        is coil3.compose.AsyncImagePainter.State.Error -> {
                                            activeArtworkFailed = true
                                            lastValidArtwork = null
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                            artworkPainters[url] = painter
                        }
                    }
                }

            val heroTrailerItemKey = "${currentItem.type}:${currentItem.id}"
            var heroTrailerPlaybackSource by remember(currentItem.type, currentItem.id) {
                mutableStateOf<TrailerPlaybackSource?>(null)
            }
            var heroTrailerReady by remember(currentItem.type, currentItem.id) { mutableStateOf(false) }
            var heroTrailerFinished by remember(currentItem.type, currentItem.id) { mutableStateOf(false) }
            val heroTrailerMuted by HeroTrailerAudioState
                .muted(HeroTrailerSurface.Home)
                .collectAsStateWithLifecycle()

            LaunchedEffect(trailerStartUnmuted) {
                HeroTrailerAudioState.applyStartMuted(HeroTrailerSurface.Home, !trailerStartUnmuted)
            }

            val latestForceStopTrailer = rememberUpdatedState {
                if (heroTrailerPlaybackSource != null || !heroTrailerFinished) {
                    heroTrailerPlaybackSource = null
                    heroTrailerReady = false
                    heroTrailerFinished = true
                }
            }
            DisposableEffect(Unit) {
                val callback: () -> Unit = { latestForceStopTrailer.value.invoke() }
                HomeHeroTrailerPlaybackController.register(callback)
                onDispose { HomeHeroTrailerPlaybackController.unregister(callback) }
            }

            LaunchedEffect(
                effectiveTrailerPlaybackEnabled,
                currentItem.type,
                currentItem.id,
                trailerStartDelaySeconds,
            ) {
                heroTrailerPlaybackSource = null
                heroTrailerReady = false
                heroTrailerFinished = false
                if (!effectiveTrailerPlaybackEnabled) return@LaunchedEffect
                val resolvedSource = async {
                    val meta = runCatching {
                        MetaDetailsRepository.fetch(currentItem.type, currentItem.id, includeEpisodes = false)
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                    val trailer = meta?.trailers?.let(::selectHeroTrailer) ?: return@async null
                    runCatching {
                        TrailerPlaybackResolver.resolveFromYouTubeUrl(trailer.youtubePlaybackUrl())
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                }
                if (trailerStartDelaySeconds > 0) {
                    delay(trailerStartDelaySeconds.toLong() * 1000L)
                }
                val source = resolvedSource.await()
                if (source == null) {
                    heroTrailerFinished = true
                } else {
                    heroTrailerPlaybackSource = source
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heroStretchHeight(layout.heroHeight, stretchPx),
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.01f },
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(layout.heroHeight)
                            .heroStretchZoom(stretchPx),
                    ) {
                        if (!activeArtworkFailed && continuityAllowed) {
                            lastValidArtwork?.let { (_, painter) ->
                                androidx.compose.foundation.Image(
                                    painter = painter, contentDescription = null,
                                    modifier = Modifier.fillMaxSize().graphicsLayer {
                                        val fraction = pagerState.currentPageOffsetFraction
                                        val incomingPage = (pagerState.currentPage + if (fraction >= 0f) 1 else -1)
                                            .coerceIn(0, pagerState.pageCount - 1)
                                        val incoming = artworkPainters[artworkFor(items[incomingPage % items.size])]
                                        val pendingTransition = pagerState.isScrollInProgress &&
                                            incoming?.state?.value !is coil3.compose.AsyncImagePainter.State.Success
                                        alpha = if (!activeArtworkComplete || pendingTransition) 1f else 0f
                                        translationY = if (isCardStyle) 0f else heroBackgroundScrollTranslationY(scrollOffsetPx)
                                        scaleX = artworkBaseScale * if (isCardStyle) 1f else heroBackgroundScrollScale(scrollOffsetPx)
                                        scaleY = scaleX
                                    },
                                    alignment = if (layout.isTablet) Alignment.TopCenter else Alignment.Center,
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        visiblePages.forEach { layer ->
                            val item = items[layer.itemIndex]
                            val artworkUrl = artworkFor(item)
                            val isCurrentLayer = layer.page == currentPage
                            key(layer.page, effectiveArtworkSource, item.type, item.id, artworkUrl) {
                                artworkPainters[artworkUrl]?.let { painter ->
                                androidx.compose.foundation.Image(
                                    painter = painter,
                                    contentDescription = item.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .artworkDrawMarker(artworkUrl.orEmpty(), painter.state.value is coil3.compose.AsyncImagePainter.State.Success)
                                        .graphicsLayer {
                                            val offset = heroPageOffset(pagerState, layer.page)
                                            val visibility = (1f - abs(offset)).coerceIn(0f, 1f)
                                            alpha = when {
                                                isCurrentLayer -> visibility
                                                visibility > 0f -> 1f
                                                else -> 0f
                                            }
                                            translationX = -offset * heroWidthPx * artworkParallax
                                            translationY = if (isCardStyle) 0f else heroBackgroundScrollTranslationY(scrollOffsetPx)
                                            scaleX = artworkBaseScale *
                                                if (isCardStyle) 1f else heroBackgroundScrollScale(scrollOffsetPx)
                                            scaleY = artworkBaseScale *
                                                if (isCardStyle) 1f else heroBackgroundScrollScale(scrollOffsetPx)
                                        },
                                    alignment = if (layout.isTablet) Alignment.TopCenter else Alignment.Center,
                                    contentScale = ContentScale.Crop,
                                )
                                }
                            }
                        }

                        val heroTrailerSourceUrl = heroTrailerPlaybackSource
                            ?.videoUrl
                            ?.takeIf { it.isNotBlank() && effectiveTrailerPlaybackEnabled && !heroTrailerFinished }
                        if (heroTrailerSourceUrl != null) {
                            val trailerReadyAlpha by animateFloatAsState(
                                targetValue = if (heroTrailerReady) 1f else 0f,
                                animationSpec = tween(durationMillis = 300),
                                label = "home_hero_trailer_alpha",
                            )
                            HeroTrailerPlayerSurface(
                                sourceUrl = heroTrailerSourceUrl,
                                sourceAudioUrl = heroTrailerPlaybackSource?.audioUrl,
                                playWhenReady = !heroTrailerFinished && !isHeroScrolledAway,
                                muted = heroTrailerMuted,
                                startPositionMs = remember(heroTrailerItemKey) {
                                    HomeHeroTrailerPlaybackPositionStore.get(heroTrailerItemKey)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val currentPageOffset = pagerState.currentPageOffsetFraction
                                        val currentPageVisibility = (1f - abs(currentPageOffset)).coerceIn(0f, 1f)
                                        alpha = trailerReadyAlpha * currentPageVisibility
                                        translationX = -currentPageOffset * heroWidthPx * artworkParallax
                                        translationY = if (isCardStyle) 0f else heroBackgroundScrollTranslationY(scrollOffsetPx)
                                        scaleX = artworkBaseScale * if (isCardStyle) 1f else heroBackgroundScrollScale(scrollOffsetPx)
                                        scaleY = artworkBaseScale * if (isCardStyle) 1f else heroBackgroundScrollScale(scrollOffsetPx)
                                    },
                                onUnavailable = { heroTrailerReady = false },
                                onReady = {
                                    if (!heroTrailerFinished) heroTrailerReady = true
                                },
                                onEnded = {
                                    heroTrailerReady = false
                                    heroTrailerFinished = true
                                    HomeHeroTrailerPlaybackPositionStore.clear(heroTrailerItemKey)
                                },
                                onError = {
                                    heroTrailerReady = false
                                    heroTrailerFinished = true
                                    HomeHeroTrailerPlaybackPositionStore.clear(heroTrailerItemKey)
                                },
                                onPositionUpdate = { positionMs ->
                                    HomeHeroTrailerPlaybackPositionStore.set(heroTrailerItemKey, positionMs)
                                },
                            )
                        }
                    }

                    if (!isCardStyle) {
                        val heroFadeColorState = LocalDynamicArtworkBackgroundColor.current
                        val themeBackground = MaterialTheme.colorScheme.background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val fade = heroFadeColorState?.value ?: themeBackground
                                    drawRect(
                                        Brush.verticalGradient(
                                            colors = com.nuvio.app.features.home.heroOverlayAlphas(heroOptions.displayMode)
                                                .map { alpha -> fade.copy(alpha = alpha) },
                                        ),
                                    )
                                },
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(layout.bottomFadeHeight)
                                .align(Alignment.BottomCenter)
                                .drawBehind {
                                    val fade = heroFadeColorState?.value ?: themeBackground
                                    // Reaches 0.6 a third of the way up, so the logo, metadata and
                                    // synopsis sit on a solid enough fade in both modes.
                                    drawRect(
                                        Brush.verticalGradient(
                                            0f to fade.copy(alpha = 0f),
                                            0.35f to fade.copy(alpha = 0.6f),
                                            0.7f to fade.copy(alpha = 0.9f),
                                            1f to fade,
                                        ),
                                    )
                                },
                        )
                    }

                    if (isCardStyle) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = onItemClick != null) {
                                    onItemClick?.invoke(currentItem)
                                },
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = onItemClick != null) {
                                    onItemClick?.invoke(currentItem)
                                },
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    horizontal = layout.contentHorizontalPadding,
                                    vertical = layout.contentVerticalPadding,
                                ),
                            horizontalAlignment = if (layout.isTablet) Alignment.Start else Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(layout.contentWidthFraction)
                                    .widthIn(max = layout.contentMaxWidth),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                visiblePages.forEach { layer ->
                                    val layerItem = items[layer.itemIndex]
                                    key(layer.page, layerItem.type, layerItem.id) {
                                    // Neighbour pages stay composed for smooth swipes, but while they are
                                    // fully off-screen they must not expose their titles to semantics
                                    // (tests, TalkBack) — only pages that are actually on screen do.
                                    val isLayerOnScreen by remember(pagerState, layer.page) {
                                        derivedStateOf { abs(heroPageOffset(pagerState, layer.page)) < 1f }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                val offset = heroPageOffset(pagerState, layer.page)
                                                val visibility = (1f - abs(offset)).coerceIn(0f, 1f)
                                                alpha = ((visibility - 0.5f) * 2f).coerceIn(0f, 1f)
                                                translationX = -offset * heroWidthPx * HERO_CONTENT_PARALLAX
                                            }
                                            .then(
                                                if (isLayerOnScreen) Modifier else Modifier.clearAndSetSemantics { },
                                            ),
                                    ) {
                                        HeroContentBlock(
                                            item = layerItem,
                                            layout = layout,
                                            onItemClick = onItemClick,
                                            options = heroOptions,
                                            loadMetadata = layer.page == currentPage && (activeArtworkComplete || activeArtworkFailed),
                                            prepareLogo = activeArtworkComplete || activeArtworkFailed,
                                        )
                                    }
                                    }
                                }
                            }

                            if (items.size > 1) {
                                Spacer(modifier = Modifier.height(if (layout.isTablet) 16.dp else 18.dp))
                                HeroPageIndicator(
                                    itemCount = items.size,
                                    pagerState = pagerState,
                                    coroutineScope = coroutineScope,
                                )
                            }
                        }
                    }

                    if (heroTrailerReady && heroTrailerPlaybackSource != null) {
                        val muteIconSize = 20.dp
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = statusBarTopPadding + 12.dp,
                                    end = if (layout.isTablet) 32.dp else 18.dp,
                                )
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    HeroTrailerAudioState.toggleMuted(HeroTrailerSurface.Home)
                                }
                                .padding(8.dp),
                        ) {
                            Icon(
                                imageVector = if (heroTrailerMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(muteIconSize),
                            )
                        }
                    }
                }
            }
        }

        if (isCardStyle && items.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            HeroPageIndicator(
                itemCount = items.size,
                pagerState = pagerState,
                coroutineScope = coroutineScope,
            )
        }
    }
}

@Composable
private fun HeroPageIndicator(
    itemCount: Int,
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(itemCount) { index ->
            val activeFraction = heroItemVisibility(pagerState, index, itemCount)
            Box(
                modifier = Modifier
                    .clickable {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                heroPageForItem(pagerState.currentPage, index, itemCount),
                            )
                        }
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground)
                    .graphicsLayer {
                        alpha = 0.35f + (0.57f * activeFraction)
                    }
                    .width(8.dp + (24.dp * activeFraction))
                    .height(8.dp),
            )
        }
    }
}

private data class HeroPageLayer(
    val page: Int,
    val itemIndex: Int,
)

internal fun heroArtworkUrl(item: MetaPreview, source: HomeHeroArtworkSource): String? = when (source) {
    HomeHeroArtworkSource.POSTER -> item.poster?.takeIf { it.isNotBlank() } ?: item.banner?.takeIf { it.isNotBlank() }
    HomeHeroArtworkSource.BACKDROP -> item.banner?.takeIf { it.isNotBlank() } ?: item.poster?.takeIf { it.isNotBlank() }
}

private fun heroPageOffset(
    pagerState: PagerState,
    page: Int,
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

private fun heroItemVisibility(pagerState: PagerState, itemIndex: Int, itemCount: Int): Float {
    val page = heroPageForItem(pagerState.currentPage, itemIndex, itemCount)
    return (1f - abs(heroPageOffset(pagerState, page))).coerceIn(0f, 1f)
}

internal fun heroPageForItem(currentPage: Int, itemIndex: Int, itemCount: Int): Int {
    val page = currentPage.toLong() - currentPage % itemCount + itemIndex
    return listOf(page - itemCount, page, page + itemCount)
        .filter { it in 0L until Int.MAX_VALUE.toLong() }
        .minBy { abs(it - currentPage) }
        .toInt()
}

@Composable
internal fun HomeHeroReservedSpace(
    modifier: Modifier = Modifier,
    viewportHeight: Dp? = null,
    mobileBelowSectionHeightHint: Dp? = null,
    heroStyle: HomeHeroStyle = HomeHeroStyle.FULL_BLEED,
) {
    val isCardStyle = heroStyle == HomeHeroStyle.CARD
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isCardStyle) {
                    Modifier.padding(
                        top = statusBarTopPadding + HERO_CARD_TOP_PADDING,
                        start = HERO_CARD_HORIZONTAL_PADDING,
                        end = HERO_CARD_HORIZONTAL_PADDING,
                    )
                } else {
                    Modifier
                },
            )
            .clip(
                if (isCardStyle) {
                    RoundedCornerShape(HERO_CARD_CORNER_RADIUS)
                } else {
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                },
            ),
    ) {
        val baseLayout = homeHeroLayout(
            maxWidthDp = maxWidth.value,
            viewportHeightDp = viewportHeight?.value,
            mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHint?.value,
        )
        val heroHeight = if (isCardStyle) {
            cardHeroHeight(
                maxWidth = maxWidth,
                viewportHeight = viewportHeight,
                isTablet = baseLayout.isTablet,
            )
        } else {
            baseLayout.heroHeight
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
        )
    }
}

@Composable
private fun HeroContentBlock(
    item: MetaPreview,
    layout: HomeHeroLayout,
    onItemClick: ((MetaPreview) -> Unit)?,
    options: com.nuvio.app.features.home.AvixHeroOptions = com.nuvio.app.features.home.AvixHeroOptions(),
    loadMetadata: Boolean = true,
    prepareLogo: Boolean = true,
) {
    var logoLoadError by remember(item.type, item.id, item.logo) {
        mutableStateOf(false)
    }
    val logoUrl = item.logo?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (layout.isTablet) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (logoUrl != null && !logoLoadError) {
            if (prepareLogo) {
            AsyncImage(
                model = logoUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth(layout.logoWidthFraction)
                    .aspectRatio(2.6f)
                    .clickable(enabled = onItemClick != null) {
                        onItemClick?.invoke(item)
                    },
                alignment = if (layout.isTablet) Alignment.CenterStart else Alignment.Center,
                contentScale = ContentScale.Fit,
                onError = { logoLoadError = true },
            )
            } else {
                Spacer(Modifier.fillMaxWidth(layout.logoWidthFraction).aspectRatio(2.6f))
            }
        } else {
            Text(
                text = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onItemClick != null) {
                        onItemClick?.invoke(item)
                    },
                style = if (layout.isTablet) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.displaySmall
                },
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                textAlign = if (layout.isTablet) TextAlign.Start else TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val detailsState = remember(item.type, item.id) {
            mutableStateOf(MetaDetailsRepository.peek(item.type, item.id))
        }
        val needsDetails = !options.compactMetadata ||
            (options.showRatings && item.imdbRating.isNullOrBlank()) ||
            (options.showOverview && item.description.isNullOrBlank()) || item.releaseInfo.isNullOrBlank()
        LaunchedEffect(item.type, item.id, loadMetadata, needsDetails) {
            if (loadMetadata && needsDetails && detailsState.value == null) {
                detailsState.value = runCatching {
                    MetaDetailsRepository.fetch(item.type, item.id, includeEpisodes = false)
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            }
        }
        val details = detailsState.value
        val rating = (item.imdbRating ?: details?.imdbRating)
            ?.takeIf { options.showRatings }
            ?.takeIf { raw -> raw.toDoubleOrNull()?.let { it > 0.0 } == true }
        val year = (item.releaseInfo ?: details?.releaseInfo)
            ?.takeIf { it.isNotBlank() }
            ?.let(::formatReleaseDateForDisplay)
        val genre = (item.genres.firstOrNull() ?: details?.genres?.firstOrNull())
            ?.takeIf { it.isNotBlank() && !options.compactMetadata }
        val ageRating = details?.ageRating?.trim()?.takeIf { it.isNotBlank() && !options.compactMetadata }
        val overviewMaxLines = com.nuvio.app.features.home.heroOverviewMaxLines(options)
        val synopsis = (item.description ?: details?.description)?.trim()?.takeIf { it.isNotBlank() }
        val typeLabel = when (item.type.lowercase()) {
            "movie" -> stringResource(Res.string.media_movie)
            "series", "tv", "show" -> stringResource(Res.string.media_series)
            else -> item.type.replaceFirstChar(Char::uppercase)
        }
        val textAlign = if (layout.isTablet) TextAlign.Start else TextAlign.Center

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (layout.isTablet) {
                Arrangement.spacedBy(8.dp, Alignment.Start)
            } else {
                Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var needsDot = false
            if (rating != null) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = HeroRatingStar,
                    modifier = Modifier.size(18.dp),
                )
                HeroMetaText(text = rating)
                needsDot = true
            }
            year?.let {
                if (needsDot) HeroMetaDot()
                HeroMetaText(text = it)
                needsDot = true
            }
            genre?.let {
                if (needsDot) HeroMetaDot()
                HeroMetaText(text = it, modifier = Modifier.weight(1f, fill = false))
                needsDot = true
            }
            ageRating?.let {
                if (needsDot) HeroMetaDot()
                DetailHeroMetaBadge(text = it, contentColor = MaterialTheme.colorScheme.onBackground)
                needsDot = true
            }
            if (needsDot) HeroMetaDot()
            HeroTypePill(text = typeLabel)
        }

        if (synopsis != null && overviewMaxLines > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = synopsis,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                textAlign = textAlign,
                maxLines = overviewMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeroTypePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

private val HeroRatingStar = Color(0xFFF5C518)

@Composable
private fun HeroMetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun homeHeroLayout(
    maxWidthDp: Float,
    viewportHeightDp: Float? = null,
    mobileBelowSectionHeightHintDp: Float? = null,
): HomeHeroLayout {
    val isLandscapeViewport = viewportHeightDp?.let { maxWidthDp > it } == true
    val shortestViewportSideDp = viewportHeightDp?.let { minOf(maxWidthDp, it) } ?: maxWidthDp
    val shouldBoostTabletLandscapeHero = isLandscapeViewport && shortestViewportSideDp >= 600f

    return when {
        maxWidthDp >= 1200f -> HomeHeroLayout(
            isTablet = true,
            heroHeight = tabletHeroHeight(
                baseHeight = (maxWidthDp * 0.42f).dp.coerceIn(360.dp, 440.dp),
                boostForLandscape = shouldBoostTabletLandscapeHero,
            ),
            contentMaxWidth = 640.dp,
            contentWidthFraction = 0.56f,
            contentHorizontalPadding = 56.dp,
            contentVerticalPadding = 22.dp,
            bottomFadeHeight = 190.dp,
            logoWidthFraction = 0.58f,
        )
        maxWidthDp >= 840f -> HomeHeroLayout(
            isTablet = true,
            heroHeight = tabletHeroHeight(
                baseHeight = (maxWidthDp * 0.46f).dp.coerceIn(340.dp, 420.dp),
                boostForLandscape = shouldBoostTabletLandscapeHero,
            ),
            contentMaxWidth = 560.dp,
            contentWidthFraction = 0.62f,
            contentHorizontalPadding = 40.dp,
            contentVerticalPadding = 20.dp,
            bottomFadeHeight = 180.dp,
            logoWidthFraction = 0.56f,
        )
        maxWidthDp >= 600f -> HomeHeroLayout(
            isTablet = true,
            heroHeight = tabletHeroHeight(
                baseHeight = (maxWidthDp * 0.58f).dp.coerceIn(320.dp, 380.dp),
                boostForLandscape = shouldBoostTabletLandscapeHero,
            ),
            contentMaxWidth = 520.dp,
            contentWidthFraction = 0.72f,
            contentHorizontalPadding = 32.dp,
            contentVerticalPadding = 18.dp,
            bottomFadeHeight = 170.dp,
            logoWidthFraction = 0.54f,
        )
        else -> HomeHeroLayout(
            isTablet = false,
            heroHeight = mobileHeroHeight(
                maxWidthDp = maxWidthDp,
                viewportHeightDp = viewportHeightDp,
                mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHintDp,
            ),
            contentMaxWidth = 480.dp,
            contentWidthFraction = 1f,
            contentHorizontalPadding = 24.dp,
            contentVerticalPadding = 16.dp,
            bottomFadeHeight = 280.dp,
            logoWidthFraction = 0.62f,
        )
    }
}

private fun tabletHeroHeight(
    baseHeight: Dp,
    boostForLandscape: Boolean,
): Dp {
    if (!boostForLandscape) return baseHeight

    return (baseHeight.value * TABLET_LANDSCAPE_HERO_HEIGHT_MULTIPLIER).dp
}

private fun mobileHeroHeight(
    maxWidthDp: Float,
    viewportHeightDp: Float?,
    mobileBelowSectionHeightHintDp: Float?,
): Dp {
    val isPortraitViewport = viewportHeightDp?.let { it >= maxWidthDp } != false

    val maxAllowedFromViewportDp = if (viewportHeightDp != null && mobileBelowSectionHeightHintDp != null) {
        viewportHeightDp - mobileBelowSectionHeightHintDp
    } else {
        null
    }

    if (isPortraitViewport) {
        // Luqman's portrait hero (width-driven), still capped so the section below
        // (continue watching) keeps its room on short screens, as upstream intends.
        val portraitHeight = maxWidthDp * MOBILE_PORTRAIT_HERO_WIDTH_RATIO
        return (maxAllowedFromViewportDp?.let { portraitHeight.coerceAtMost(it.coerceAtLeast(0f)) } ?: portraitHeight).dp
    }

    val viewportDrivenHeight = viewportHeightDp?.let { (it * MOBILE_HERO_VIEWPORT_RATIO).dp }
    val widthFallbackHeight = (maxWidthDp * 1.16f).dp
    val baseHeight = viewportDrivenHeight ?: widthFallbackHeight

    val cappedHeight = if (maxAllowedFromViewportDp != null) {
        val maxAllowedFromViewport = maxAllowedFromViewportDp.dp
        baseHeight.coerceAtMost(maxAllowedFromViewport)
    } else {
        baseHeight
    }
    val minHeight = if (maxAllowedFromViewportDp != null) {
        minOf(MOBILE_HERO_MIN_HEIGHT_DP, maxAllowedFromViewportDp.coerceAtLeast(0f)).dp
    } else {
        MOBILE_HERO_MIN_HEIGHT_DP.dp
    }

    return cappedHeight.coerceIn(minHeight, MOBILE_HERO_MAX_HEIGHT_DP.dp)
}

@Composable
private fun HeroMetaDot() {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)),
    )
}

private fun heroBackgroundScrollScale(scrollOffsetPx: Float): Float {
    val scaleIncrease = if (scrollOffsetPx < 0f) {
        abs(scrollOffsetPx) * HERO_SCROLL_UP_SCALE_MULTIPLIER
    } else {
        scrollOffsetPx * HERO_SCROLL_DOWN_SCALE_MULTIPLIER
    }
    return (1f + scaleIncrease).coerceAtMost(HERO_SCROLL_MAX_SCALE)
}

private fun heroBackgroundScrollTranslationY(scrollOffsetPx: Float): Float {
    return scrollOffsetPx * HERO_SCROLL_PARALLAX
}

private fun Modifier.homeHeroPagerGesture(
    pagerState: PagerState,
    itemCount: Int,
    coroutineScope: CoroutineScope,
): Modifier {
    if (itemCount <= 1) return this

    return pointerInput(pagerState, itemCount) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val widthPx = size.width.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
            val velocityTracker = VelocityTracker().apply {
                addPosition(down.uptimeMillis, down.position)
            }
            val startPage = pagerState.currentPage
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!change.pressed) {
                    if (dragging) {
                        val targetPage = resolveHeroTargetPage(
                            startPage = startPage,
                            pageCount = pagerState.pageCount,
                            totalDx = totalDx,
                            velocityX = velocityTracker.calculateVelocity().x,
                            widthPx = widthPx,
                        )
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                    break
                }

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                pagerState.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

private fun resolveHeroTargetPage(
    startPage: Int,
    pageCount: Int,
    totalDx: Float,
    velocityX: Float,
    widthPx: Float,
): Int {
    val thresholdPassed = abs(totalDx) > widthPx * HERO_SWIPE_THRESHOLD_FRACTION ||
        abs(velocityX) > HERO_SWIPE_VELOCITY_THRESHOLD
    if (!thresholdPassed) return startPage

    val currentPage = startPage.coerceIn(0, pageCount - 1)
    return when {
        totalDx > 0f -> (currentPage - 1).coerceAtLeast(0)
        totalDx < 0f -> (currentPage + 1).coerceAtMost(pageCount - 1)
        else -> currentPage
    }
}

internal fun heroVisiblePages(currentPage: Int, pageCount: Int): List<Int> =
    listOf((currentPage - 1).coerceAtLeast(0), (currentPage + 1).coerceAtMost(pageCount - 1), currentPage).distinct()
