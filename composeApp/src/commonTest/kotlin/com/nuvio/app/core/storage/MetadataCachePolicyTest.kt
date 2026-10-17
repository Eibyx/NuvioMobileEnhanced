package com.nuvio.app.core.storage

import kotlin.test.*

class MetadataCachePolicyTest {
    @Test fun freshEntriesAreAvailableAndExpiredEntriesAreRejected() {
        val entry = MetadataCacheEntry("profile:es:season1", 1000, "response")
        assertTrue(validMetadataCacheEntry(entry, 1000, 100))
        assertTrue(validMetadataCacheEntry(entry, 1099, 100))
        assertFalse(validMetadataCacheEntry(entry, 1100, 100))
    }
    @Test fun futureTimestampsAreRejectedAfterClockChanges() {
        assertFalse(validMetadataCacheEntry(MetadataCacheEntry("key", 2000, "data"), 1000, 10000))
    }
}
