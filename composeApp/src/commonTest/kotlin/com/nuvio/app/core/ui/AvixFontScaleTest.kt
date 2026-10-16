package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AvixFontScaleTest {
    @Test
    fun `original Nuvio font keeps the fixed upstream scale`() {
        assertEquals(1f, avixFontScale(useSystemFont = false, deviceFontScale = 1.5f))
        assertEquals(1f, avixFontScale(useSystemFont = false, deviceFontScale = 0.85f))
    }

    @Test
    fun `system font follows the device text size within layout bounds`() {
        assertEquals(1f, avixFontScale(useSystemFont = true, deviceFontScale = 1f))
        assertEquals(1.15f, avixFontScale(useSystemFont = true, deviceFontScale = 1.15f))
        assertEquals(MaxSystemFontScale, avixFontScale(useSystemFont = true, deviceFontScale = 2f))
        assertEquals(MinSystemFontScale, avixFontScale(useSystemFont = true, deviceFontScale = 0.5f))
    }
}
