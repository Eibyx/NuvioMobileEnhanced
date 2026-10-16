package com.nuvio.app.features.player

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackEndTimeTest {
    private val utc = TimeZone.UTC
    private val now = 1_760_000_000_000L // 2025-10-09T08:53:20Z

    @Test
    fun endTimeAddsRemainingDuration() {
        // 45 min left at 1x → 09:38
        assertEquals("09:38", playbackEndClockLabel(15 * 60_000L, 60 * 60_000L, 1f, now, utc))
    }

    @Test
    fun playbackSpeedShortensTheRemainingTime() {
        // 60 min left at 2x → 30 min → 09:23
        assertEquals("09:23", playbackEndClockLabel(0L, 60 * 60_000L, 2f, now, utc))
    }

    @Test
    fun wrapsPastMidnight() {
        val lateNight = 1_760_051_400_000L // 23:10Z
        assertEquals("00:40", playbackEndClockLabel(0L, 90 * 60_000L, 1f, lateNight, utc))
    }

    @Test
    fun noLabelForLiveUnknownOrFinished() {
        assertNull(playbackEndClockLabel(0L, 0L, 1f, now, utc))
        assertNull(playbackEndClockLabel(59 * 60_000L + 50_000L, 60 * 60_000L, 1f, now, utc))
    }
}
