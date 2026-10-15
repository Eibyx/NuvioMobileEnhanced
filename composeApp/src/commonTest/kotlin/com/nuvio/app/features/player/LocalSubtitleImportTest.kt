package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSubtitleImportTest {

    private val srt = "1\n00:00:01,000 --> 00:00:02,500\n¿Qué pasó, señor?\n\n2\n00:00:03,000 --> 00:00:04,000\nAdiós\n"

    private val ass = """
        [Script Info]
        ScriptType: v4.00+
        PlayResX: 1920
        PlayResY: 1080

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Sign,Gandhi Sans,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,7,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:04.00,Sign,,0,0,0,,{\pos(960,200)\frz12\t(0,500,\fscx120)\clip(m 0 0 l 100 0 100 100 0 100)}Cartel
        Dialogue: 1,0:00:01.00,0:00:04.00,Default,,0,0,0,,{\k20}Ka{\kf40}ra{\ko30}o{\fad(200,300)}ke
    """.trimIndent()

    @Test
    fun srtUtf8IsAcceptedUnchanged() {
        val result = normalizeLocalSubtitle(srt.encodeToByteArray(), "Anime.Episodio.01.es.srt").getOrThrow()
        assertEquals(LocalSubtitleFormat.SRT, result.format)
        assertEquals(LocalSubtitleEncoding.UTF8, result.sourceEncoding)
        assertEquals(srt, result.text)
        assertEquals("es", result.language)
    }

    @Test
    fun assTextIsPreservedByteForByte() {
        val result = normalizeLocalSubtitle(ass.encodeToByteArray(), "show.ass").getOrThrow()
        assertEquals(LocalSubtitleFormat.ASS, result.format)
        // Override tags (\pos, \t, \clip, \k, \fad…) must survive untouched for libass.
        assertEquals(ass, result.text)
    }

    @Test
    fun legacySsaIsDetected() {
        val ssa = ass.replace("ScriptType: v4.00+", "ScriptType: v4.00").replace("[V4+ Styles]", "[V4 Styles]")
        assertEquals(LocalSubtitleFormat.SSA, normalizeLocalSubtitle(ssa.encodeToByteArray(), "x.ssa").getOrThrow().format)
    }

    @Test
    fun utf8BomIsStripped() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + srt.encodeToByteArray()
        val result = normalizeLocalSubtitle(bytes, "a.srt").getOrThrow()
        assertEquals(LocalSubtitleEncoding.UTF8_BOM, result.sourceEncoding)
        assertEquals(srt, result.text)
    }

    @Test
    fun utf16LittleEndianWithBomIsDecoded() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + utf16(ass, littleEndian = true)
        val result = normalizeLocalSubtitle(bytes, "a.ass").getOrThrow()
        assertEquals(LocalSubtitleEncoding.UTF16_LE, result.sourceEncoding)
        assertEquals(ass, result.text)
    }

    @Test
    fun utf16BigEndianWithoutBomIsDecoded() {
        val result = normalizeLocalSubtitle(utf16(srt, littleEndian = false), "a.srt").getOrThrow()
        assertEquals(LocalSubtitleEncoding.UTF16_BE, result.sourceEncoding)
        assertEquals(srt, result.text)
    }

    @Test
    fun windows1252IsDecoded() {
        // "¿Qué pasó, señor? – “ok”" in Windows-1252 (0x96 en dash, 0x93/0x94 curly quotes).
        val body = byteArrayOf(0xBF.toByte()) + "Qu".encodeToByteArray() + byteArrayOf(0xE9.toByte()) +
            " pas".encodeToByteArray() + byteArrayOf(0xF3.toByte()) + ", se".encodeToByteArray() +
            byteArrayOf(0xF1.toByte()) + "or? ".encodeToByteArray() + byteArrayOf(0x96.toByte()) + " ".encodeToByteArray() +
            byteArrayOf(0x93.toByte()) + "ok".encodeToByteArray() + byteArrayOf(0x94.toByte())
        val bytes = "1\n00:00:01,000 --> 00:00:02,000\n".encodeToByteArray() + body + "\n".encodeToByteArray()
        val result = normalizeLocalSubtitle(bytes, "a.srt").getOrThrow()
        assertEquals(LocalSubtitleEncoding.WINDOWS_1252, result.sourceEncoding)
        assertTrue(result.text.contains("¿Qué pasó, señor? – “ok”"), result.text)
    }

    @Test
    fun vttAndTtmlAreDetected() {
        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.000\nHola\n"
        assertEquals(LocalSubtitleFormat.VTT, normalizeLocalSubtitle(vtt.encodeToByteArray(), "a.vtt").getOrThrow().format)
        val ttml = """<?xml version="1.0"?><tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">Hola</p></div></body></tt>"""
        assertEquals(LocalSubtitleFormat.TTML, normalizeLocalSubtitle(ttml.encodeToByteArray(), "a.ttml").getOrThrow().format)
    }

    @Test
    fun unsupportedAndEmptyFilesAreRejected() {
        assertEquals(LocalSubtitleError.EMPTY, error(ByteArray(0), "a.srt"))
        assertEquals(LocalSubtitleError.EMPTY, error("   \n".encodeToByteArray(), "a.srt"))
        assertEquals(LocalSubtitleError.UNSUPPORTED_FORMAT, error("just some notes".encodeToByteArray(), "notes.txt"))
        val assWithoutEvents = ass.lines().filterNot { it.startsWith("Dialogue") }.joinToString("\n")
        assertEquals(LocalSubtitleError.NO_CUES, error(assWithoutEvents.encodeToByteArray(), "a.ass"))
        assertEquals(LocalSubtitleError.TOO_LARGE, error(ByteArray(LOCAL_SUBTITLE_MAX_BYTES + 1) { 'a'.code.toByte() }, "a.srt"))
    }

    @Test
    fun languageIsGuessedOnlyFromKnownTokens() {
        assertEquals("es", guessSubtitleLanguageFromFileName("Anime.Episodio.01.es.srt"))
        assertEquals("es", guessSubtitleLanguageFromFileName("Show S01E01 [Spanish].ass"))
        assertEquals("es-419", guessSubtitleLanguageFromFileName("Show.S01E01.lat.srt"))
        assertEquals("en", guessSubtitleLanguageFromFileName("show.eng.srt")?.substringBefore('-'))
        assertNull(guessSubtitleLanguageFromFileName("Show.S01E01.1080p.WEB.x264.srt"))
        assertNull(guessSubtitleLanguageFromFileName(null))
    }

    @Test
    fun assDetectionHelper() {
        assertTrue(isAssSubtitle("file:///cache/a.ASS"))
        assertTrue(isAssSubtitle("https://x/y.ssa?token=1"))
        assertTrue(isAssSubtitle(null, codec = "text/x-ssa"))
        assertTrue(isAssSubtitle("Signs & Songs", codec = "ass"))
        assertTrue(!isAssSubtitle("https://x/y.srt"))
    }

    private fun error(bytes: ByteArray, name: String): LocalSubtitleError? =
        (normalizeLocalSubtitle(bytes, name).exceptionOrNull() as? LocalSubtitleImportException)?.error

    private fun utf16(text: String, littleEndian: Boolean): ByteArray {
        val out = ByteArray(text.length * 2)
        text.forEachIndexed { i, c ->
            val hi = (c.code shr 8).toByte()
            val lo = (c.code and 0xFF).toByte()
            out[i * 2] = if (littleEndian) lo else hi
            out[i * 2 + 1] = if (littleEndian) hi else lo
        }
        return out
    }
}
