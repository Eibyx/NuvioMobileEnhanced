package com.nuvio.app.core.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MetadataResponseCacheTest {
    @Test fun survivesNewCacheInstanceAndSeparatesScopeAndSeason() = runTest {
        val disk = mutableMapOf<String, String>()
        fun cache() = MetadataResponseCache(disk::get, { path, data -> disk[path] = data }, { 1000 })
        val first = cache()
        first.write("profile1:es:show:1", "season1")
        first.write("profile1:es:show:2", "season2")
        first.write("profile2:es:show:1", "otherProfile")
        val restarted = cache()
        assertEquals("season1", restarted.read("profile1:es:show:1", 100))
        assertEquals("season2", restarted.read("profile1:es:show:2", 100))
        assertEquals("otherProfile", restarted.read("profile2:es:show:1", 100))
        assertNull(restarted.read("profile1:en:show:1", 100))
    }
    @Test fun capacityIsBoundedAndExpiredResponsesAreNotReused() = runTest {
        val disk = mutableMapOf<String, String>()
        var clock = 1000L
        val cache = MetadataResponseCache(disk::get, { path, data -> disk[path] = data }, { clock }, maxEntries = 2)
        cache.write("a", "A"); cache.write("b", "B"); cache.write("c", "C")
        assertNull(cache.read("a", 100))
        assertEquals("C", cache.read("c", 100))
        clock += 100
        assertNull(cache.read("c", 100))
    }
    @Test fun corruptFileAndFailedWritesAreSafeCacheMisses() = runTest {
        val cache = MetadataResponseCache({ "interrupted-json" }, { _, _ -> error("disk full") }, { 1000 })
        assertNull(cache.read("missing", 100))
        cache.write("a", "network succeeded")
        assertEquals("network succeeded", cache.read("a", 100))
    }
    @Test fun oversizedResponsesCannotGrowPersistentStorage() = runTest {
        var payload: String? = null
        val cache = MetadataResponseCache({ payload }, { _, data -> payload = data }, { 1000 }, maxBytes = 256)
        cache.write("too large", "x".repeat(257))
        assertNull(payload)
        repeat(10) { cache.write("$it", "a".repeat(40)) }
        assertTrue(payload!!.length <= 256)
    }
}
