package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadSpeedEtaTest {
    @Test
    fun firstSampleIsTheInstantSpeed() {
        assertEquals(1_000_000L, nextDownloadSpeed(0L, 0L, 1_000L, 2_000_000L, 3_000L))
    }

    @Test
    fun laterSamplesAreSmoothed() {
        // previous 1 MB/s, new sample 2 MB/s → 0.65·1 + 0.35·2 = 1.35 MB/s
        assertEquals(1_350_000L, nextDownloadSpeed(1_000_000L, 0L, 0L, 2_000_000L, 1_000L))
    }

    @Test
    fun noProgressKeepsThePreviousSpeed() {
        assertEquals(500L, nextDownloadSpeed(500L, 100L, 0L, 100L, 1_000L))
        assertEquals(500L, nextDownloadSpeed(500L, 100L, 1_000L, 200L, 1_000L))
    }

    @Test
    fun etaFormatting() {
        assertEquals("45s", formatDownloadEta(45))
        assertEquals("8m 20s", formatDownloadEta(500))
        assertEquals("25m", formatDownloadEta(1_500))
        assertEquals("1h 5m", formatDownloadEta(3_900))
        assertEquals("2h", formatDownloadEta(7_200))
    }
}
