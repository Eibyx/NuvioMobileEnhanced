package com.nuvio.app.features.livetv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class IncomingSharedSourceTest {
    @Test
    fun playlistTextAndLinks() {
        assertIs<IncomingSharedSource.PlaylistData>(parseSharedText("#EXTM3U\n#EXTINF:-1,A\nhttp://x/a"))
        assertEquals(IncomingSharedSource.PlaylistUrl("https://iptv.test/list.m3u?u=1"), parseSharedText("Mira esto: https://iptv.test/list.m3u?u=1"))
        assertIs<IncomingSharedSource.DirectStream>(parseSharedText("https://cdn.test/live/index.m3u8"))
        assertIs<IncomingSharedSource.DirectStream>(parseSharedText("https://cdn.test/movie.mkv"))
        assertNull(parseSharedText("https://example.test/page.html"))
        assertNull(parseSharedText("hola"))
    }

    @Test
    fun magnetWithHexHashNameAndTrackers() {
        val magnet = parseMagnet(
            "magnet:?xt=urn:btih:C12FE1C06BBA254A9DC9F519B335AA7C1367A88A&dn=Big+Buck%20Bunny&tr=udp%3A%2F%2Ftracker.test%3A80&tr=javascript:x",
        )!!
        assertEquals("c12fe1c06bba254a9dc9f519b335aa7c1367a88a", magnet.infoHash)
        assertEquals("Big Buck Bunny", magnet.name)
        assertEquals(listOf("udp://tracker.test:80"), magnet.trackers)
    }

    @Test
    fun magnetBase32HashIsConvertedToHex() {
        // base32 of 20 zero bytes followed by checks on a known value
        assertEquals("0".repeat(40), base32ToHex("A".repeat(32)))
        assertEquals("ffffffffffffffffffffffffffffffffffffffff", base32ToHex("7".repeat(32)))
        assertNull(parseMagnet("magnet:?xt=urn:btih:xyz"))
        assertNull(parseMagnet("https://not-a-magnet"))
    }
}
