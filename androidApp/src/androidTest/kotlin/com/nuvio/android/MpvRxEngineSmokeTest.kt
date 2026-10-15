package com.nuvio.android

import android.content.Context
import android.os.Build
import android.os.Debug
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import java.io.File
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test of the mpvRx engine (app.gyrolet.mpvlib:mpvlib 1.1.3) inside the Avix APK.
 *
 * Runs headless (vo=null, ao=null): it proves JNI loading, demuxing/decoding, track discovery,
 * ASS event delivery (sub-text), seeking, external subtitle charset handling, HTTP header
 * propagation and repeated create/destroy of the singleton core. It does NOT prove audiovisual
 * output or libass rendering fidelity; those are covered by docs/ASS_SSA_TESTS.md.
 *
 * Fixture: androidTest/assets/avix_fixture.mkv (6 s, mpeg4 160x90 + AAC + ASS track "Signs",
 * generated with FFmpeg lavfi; see docs/MPVRX_INTEGRATION.md).
 */
@RunWith(AndroidJUnit4::class)
class MpvRxEngineSmokeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fixture: File
    private var coreAlive = false

    @Before
    fun setUp() {
        fixture = File(context.cacheDir, "avix_fixture.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets.open("avix_fixture.mkv").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
    }

    @After
    fun tearDown() {
        destroyCore()
    }

    @Test
    fun t01_jniLoadsAndReportsEngineVersions() {
        createCore()
        val mpvVersion = MPVLib.getPropertyString("mpv-version")
        val ffmpegVersion = MPVLib.getPropertyString("ffmpeg-version")
        log("abi=${Build.SUPPORTED_ABIS.joinToString()} mpv=$mpvVersion ffmpeg=$ffmpegVersion")
        assertNotNull(mpvVersion)
        assertTrue("unexpected engine: $mpvVersion", mpvVersion!!.startsWith("mpv v0.41"))
        assertNotNull(ffmpegVersion)
    }

    @Test
    fun t02_decodesMkvWithVideoAudioAndAssTracks() {
        createCore()
        load(fixture.absolutePath)
        MPVLib.setPropertyString("sid", "1")
        waitUntil("playback advances") { (MPVLib.getPropertyDouble("time-pos") ?: 0.0) > 0.6 }

        val tracks = trackList()
        log("tracks=$tracks")
        assertEquals("mpeg4", tracks.single { it.first == "video" }.second)
        assertEquals("aac", tracks.single { it.first == "audio" }.second)
        assertEquals("ass", tracks.single { it.first == "sub" }.second)
        waitUntil("ASS event text") { MPVLib.getPropertyString("sub-text").orEmpty().contains("Avix ASS uno") }
    }

    @Test
    fun t03_seekReachesTargetAndNextSubtitleEvent() {
        createCore()
        load(fixture.absolutePath)
        MPVLib.setPropertyString("sid", "1")
        waitUntil("playback advances") { (MPVLib.getPropertyDouble("time-pos") ?: 0.0) > 0.3 }
        MPVLib.command("seek", "3.5", "absolute")
        waitUntil("seek to 3.5 s") { (MPVLib.getPropertyDouble("time-pos") ?: 0.0) in 3.4..5.9 }
        waitUntil("ASS event after seek") { MPVLib.getPropertyString("sub-text").orEmpty().contains("Avix ASS dos") }
    }

    @Test
    fun t04_externalWindows1252SubtitleIsDecodedByEngine() {
        // mpvlib is built with iconv + uchardet: sub-codepage=auto must turn CP1252 into UTF-8.
        val srt = File(context.cacheDir, "avix_cp1252.srt")
        val body = "1\r\n00:00:00,000 --> 00:00:06,000\r\n¿Qué pasó, señor?\r\n"
        srt.writeBytes(body.toByteArray(charset("windows-1252")))
        createCore()
        load(fixture.absolutePath)
        waitUntil("file loaded") { (MPVLib.getPropertyDouble("time-pos") ?: 0.0) > 0.2 }
        MPVLib.command("sub-add", srt.absolutePath, "select")
        waitUntil("external subtitle listed") { trackList().count { it.first == "sub" } == 2 }
        waitUntil("CP1252 text decoded") { MPVLib.getPropertyString("sub-text").orEmpty().contains("¿Qué pasó, señor?") }
    }

    @Test
    fun t05_httpRequestCarriesConfiguredHeaders() {
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val server = ServerSocket(0)
        val serverThread = thread(isDaemon = true) {
            runCatching {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        val head = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                        requests += head.joinToString("\n")
                        val bytes = fixture.readBytes()
                        val out = socket.getOutputStream()
                        out.write(
                            ("HTTP/1.1 200 OK\r\nContent-Type: video/x-matroska\r\n" +
                                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()
                        )
                        out.write(bytes)
                        out.flush()
                    }
                }
            }
        }
        try {
            createCore()
            MPVLib.setPropertyString("user-agent", "AvixSmoke/1.0")
            MPVLib.setPropertyString("http-header-fields", "X-Avix-Test: header-ok")
            load("http://127.0.0.1:${server.localPort}/avix_fixture.mkv")
            waitUntil("HTTP playback advances") { (MPVLib.getPropertyDouble("time-pos") ?: 0.0) > 0.3 }
            val first = requests.first()
            log("request=$first")
            assertTrue(first, first.contains("User-Agent: AvixSmoke/1.0"))
            assertTrue(first, first.contains("X-Avix-Test: header-ok"))
        } finally {
            server.close()
            serverThread.join(2_000)
        }
    }

    @Test
    fun t06_repeatedCreateDestroyKeepsNativeHeapBounded() {
        val before = Debug.getNativeHeapAllocatedSize()
        repeat(CYCLES) { cycle ->
            createCore()
            load(fixture.absolutePath)
            waitUntil("cycle $cycle playback") { (MPVLib.getPropertyDouble("time-pos") ?: 0.0) > 0.2 }
            destroyCore()
        }
        System.gc()
        val growthMb = (Debug.getNativeHeapAllocatedSize() - before) / (1024.0 * 1024.0)
        log("native heap growth after $CYCLES cycles: %.1f MB".format(growthMb))
        assertTrue("native heap grew %.1f MB".format(growthMb), growthMb < MAX_HEAP_GROWTH_MB)
    }

    @Test
    fun t07_reportsAbiAndPageSize() {
        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE)
        log("abi=${Build.SUPPORTED_ABIS.first()} pageSize=$pageSize sdk=${Build.VERSION.SDK_INT}")
        createCore()
        assertNotNull(MPVLib.getPropertyString("mpv-version"))
    }

    private fun createCore() {
        check(!coreAlive)
        MPVLib.create(context)
        MPVLib.setOptionString("vo", "null")
        MPVLib.setOptionString("ao", "null")
        MPVLib.setOptionString("hwdec", "no")
        MPVLib.setOptionString("idle", "yes")
        MPVLib.setOptionString("msg-level", "all=warn")
        MPVLib.setOptionString("sub-codepage", "auto")
        MPVLib.init()
        coreAlive = true
    }

    private fun destroyCore() {
        if (!coreAlive) return
        coreAlive = false
        MPVLib.destroy()
    }

    private fun load(url: String) {
        MPVLib.command("loadfile", url)
    }

    /** (type, codec) of every track. */
    private fun trackList(): List<Pair<String, String?>> {
        val node: MPVNode = MPVLib.getPropertyNode("track-list") ?: return emptyList()
        return node.asArray().orEmpty().map { track ->
            track.get("type")?.asString().orEmpty() to track.get("codec")?.asString()
        }
    }

    private fun waitUntil(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        println("$TAG: $message")
    }

    private companion object {
        const val TAG = "MpvRxEngineSmokeTest"
        const val CYCLES = 5
        const val MAX_HEAP_GROWTH_MB = 64.0
    }
}
