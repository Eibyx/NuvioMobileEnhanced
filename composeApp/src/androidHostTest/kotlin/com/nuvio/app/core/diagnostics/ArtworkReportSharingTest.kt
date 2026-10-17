package com.nuvio.app.core.diagnostics

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 36], application = Application::class)
class ArtworkReportSharingTest {
    @Before @After fun resetProviderCacheBetweenRobolectricApplications() {
        // Each test gets a different private cache directory; Android keeps one application.
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }
    @Test fun privateTextExportGrantsReadOnlyContentUriWithoutStoragePermission() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val text = "NuvioEibyx diagnostic\nsource=DISK\n"
        val intent = artworkReportIntent(context, text)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".fileprovider", uri.authority)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertEquals(text, context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        assertTrue(File(context.cacheDir, "artwork-diagnostics").canonicalPath.startsWith(context.cacheDir.canonicalPath))
        clearArtworkReportFiles(context)
        assertFalse(File(context.cacheDir, "artwork-diagnostics").exists())
    }
    @Test fun repeatedExportReplacesOnePrivateFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        artworkReportIntent(context, "first")
        artworkReportIntent(context, "second")
        val files = File(context.cacheDir, "artwork-diagnostics").listFiles()!!
        assertEquals(1, files.size)
        assertEquals("second", files.single().readText())
        clearArtworkReportFiles(context)
    }
}
