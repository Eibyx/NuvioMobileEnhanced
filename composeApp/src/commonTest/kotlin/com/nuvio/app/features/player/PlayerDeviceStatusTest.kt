package com.nuvio.app.features.player

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerDeviceStatusTest {
    @Test
    fun clockIsZeroPaddedInGivenZone() {
        // 2026-01-01T07:05:00Z
        assertEquals("07:05", playerClockLabel(1_767_251_100_000L, TimeZone.UTC))
    }

    @Test
    fun batteryLabelRejectsOutOfRangeValues() {
        assertEquals("57%", playerBatteryLabel(57))
        assertNull(playerBatteryLabel(null))
        assertNull(playerBatteryLabel(-1))
        assertNull(playerBatteryLabel(101))
    }
}
