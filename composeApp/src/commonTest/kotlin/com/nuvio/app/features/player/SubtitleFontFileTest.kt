package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleFontFileTest {
    /** Minimal sfnt with only a `name` table holding the given (nameId, platform, text) records. */
    private fun fontWithNames(vararg names: Triple<Int, Int, String>): ByteArray {
        val strings = names.map { (_, platform, text) ->
            if (platform == 1) text.encodeToByteArray()
            else text.flatMap { listOf((it.code shr 8).toByte(), (it.code and 0xFF).toByte()) }.toByteArray()
        }
        val nameHeader = 6 + 12 * names.size
        val out = mutableListOf<Byte>()
        fun u16(v: Int) { out += (v shr 8).toByte(); out += (v and 0xFF).toByte() }
        fun u32(v: Int) { u16(v ushr 16); u16(v and 0xFFFF) }
        val nameOffset = 12 + 16
        u32(0x00010000); u16(1); u16(0); u16(0); u16(0) // sfnt header, 1 table
        out += "name".encodeToByteArray().toList(); u32(0); u32(nameOffset); u32(nameHeader + strings.sumOf { it.size })
        u16(0); u16(names.size); u16(nameHeader)
        var stringPos = 0
        names.forEachIndexed { i, (nameId, platform, _) ->
            u16(platform); u16(if (platform == 1) 0 else 1); u16(if (platform == 1) 0 else 0x409); u16(nameId)
            u16(strings[i].size); u16(stringPos); stringPos += strings[i].size
        }
        strings.forEach { out += it.toList() }
        return out.toByteArray()
    }

    @Test
    fun readsWindowsFamilyName() {
        assertEquals("Gandhi Sans", parseFontFamilyName(fontWithNames(Triple(1, 3, "Gandhi Sans"))))
    }

    @Test
    fun prefersTypographicFamily() {
        val font = fontWithNames(Triple(1, 3, "Open Sans SemiBold"), Triple(16, 3, "Open Sans"))
        assertEquals("Open Sans", parseFontFamilyName(font))
    }

    @Test
    fun fallsBackToMacRoman() {
        assertEquals("Classic", parseFontFamilyName(fontWithNames(Triple(1, 1, "Classic"))))
    }

    @Test
    fun detectsFontFilesAndRejectsOthers() {
        assertTrue(isSupportedFontFile(fontWithNames(Triple(1, 3, "X"))))
        assertTrue(isSupportedFontFile("OTTO".encodeToByteArray() + ByteArray(20)))
        assertFalse(isSupportedFontFile("PK\u0003\u0004 not a font".encodeToByteArray()))
        assertNull(parseFontFamilyName("garbage".encodeToByteArray()))
    }
}
