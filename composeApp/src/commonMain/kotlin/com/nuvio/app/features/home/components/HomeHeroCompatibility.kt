package com.nuvio.app.features.home.components

import kotlin.math.abs
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val MOBILE_HERO_VIEWPORT_RATIO = 0.82f
private const val MOBILE_PORTRAIT_HERO_WIDTH_RATIO = 1.5f
private const val MOBILE_HERO_MIN_HEIGHT_DP = 360f
private const val MOBILE_HERO_MAX_HEIGHT_DP = 760f
private const val TABLET_LANDSCAPE_HERO_HEIGHT_MULTIPLIER = 1.5f
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

internal fun heroPageForItem(currentPage: Int, itemIndex: Int, itemCount: Int): Int {
    val page = currentPage.toLong() - currentPage % itemCount + itemIndex
    return listOf(page - itemCount, page, page + itemCount)
        .filter { it in 0L until Int.MAX_VALUE.toLong() }
        .minBy { abs(it - currentPage) }
        .toInt()
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
