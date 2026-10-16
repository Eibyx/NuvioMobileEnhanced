package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradient
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class AnimatedAccentBrushTest {
    @Test
    fun brushMovesWithPhase() {
        val colors = listOf(Color.Red, Color.Blue)
        val start = animatedAccentBrush(colors, 0f)
        val middle = animatedAccentBrush(colors, 0.5f)
        assertIs<LinearGradient>(start)
        assertNotEquals(start, middle)
    }
}
