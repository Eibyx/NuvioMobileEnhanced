package com.nuvio.app.core.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.LocalNuvioIsDark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

internal val GlassSurfaceColor = Color(0xFF1C1C1E)

/** [GlassSurfaceColor] in dark scopes, the light surface in light mode. */
@Composable
internal fun glassSurfaceColor(): Color =
    if (LocalNuvioIsDark.current) GlassSurfaceColor else MaterialTheme.colorScheme.surface

/**
 * The backdrop of the floating navigation pill. Android 13+ adds a refraction shader on top of
 * the haze blur; everywhere else — including iOS — it is [FrostedGlassBar].
 */
@Composable
internal expect fun GlassBarSurface(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    glowStrength: Float = 1f,
)

/** Haze blur, a dark fill and a thin top-lit edge: the glass bar without the refraction shader. */
@Composable
internal fun FrostedGlassBar(hazeState: HazeState?, modifier: Modifier, glowStrength: Float) {
    val surfaceColor = glassSurfaceColor()
    val edgeColor = if (LocalNuvioIsDark.current) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .then(if (hazeState != null) Modifier.barBackdrop(hazeState, surfaceColor) else Modifier)
            .drawWithCache {
                val fill = surfaceColor.copy(alpha = if (hazeState != null) 0.55f else 0.82f)
                val edge = Brush.verticalGradient(
                    listOf(edgeColor.copy(alpha = 0.27f), edgeColor.copy(alpha = 0.02f)),
                )
                val width = 0.75.dp.toPx()
                onDrawBehind {
                    drawRect(fill)
                    drawRoundRect(
                        brush = edge,
                        topLeft = Offset(width / 2, width / 2),
                        size = Size(size.width - width, size.height - width),
                        cornerRadius = CornerRadius((size.height - width) / 2),
                        style = Stroke(width),
                        alpha = glowStrength,
                    )
                }
            },
    )
}

internal fun Modifier.barBackdrop(
    hazeState: HazeState,
    surfaceColor: Color = GlassSurfaceColor,
): Modifier = hazeEffect(state = hazeState) {
    blurRadius = 24.dp
    backgroundColor = surfaceColor
    tints = listOf(HazeTint(Color.Transparent))
    noiseFactor = 0f
}
