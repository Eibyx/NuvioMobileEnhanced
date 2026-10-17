package com.nuvio.app.core.diagnostics

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.nuvio.app.NuvioApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArtworkRequestTagCacheTest {
    @After fun stop() { ArtworkDiagnostics.install(null) }
    @Test fun tagsDoNotAlterMemoryCacheAndUnrelatedPostersAreNotRecorded() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        var fetches = 0
        val loader = NuvioApplication().newImageLoader(context).newBuilder().components {
            add(Fetcher.Factory<Uri> { _, _, _ -> Fetcher {
                fetches++
                ImageFetchResult(Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).asImage(), false, DataSource.MEMORY)
            } })
        }.build()
        try {
            val original = ImageRequest.Builder(context).data("probe:hero").size(100,100).build()
            val tagged = original.newBuilder().artworkTag("movie:hero", "HERO_VISIBLE", "CATALOG").build()
            assertEquals(original.memoryCacheKeyExtras, tagged.memoryCacheKeyExtras)
            assertEquals(original.diskCacheKey, tagged.diskCacheKey)
            assertEquals(original.memoryCacheKey, tagged.memoryCacheKey)
            assertEquals(original.data, tagged.data)
            assertEquals(original.sizeResolver, tagged.sizeResolver)
            val first = loader.execute(tagged) as SuccessResult
            val second = loader.execute(original) as SuccessResult
            assertEquals(first.memoryCacheKey, second.memoryCacheKey)
            assertEquals(DataSource.MEMORY_CACHE, second.dataSource)
            assertEquals(1, fetches)
            loader.execute(ImageRequest.Builder(context).data("probe:unrelated-poster").build())
            val report = ArtworkDiagnostics.report()
            assertEquals(1, Regex("event=coil.start ").findAll(report).count())
            assertTrue("purpose=HERO_VISIBLE" in report)
            assertFalse(ArtworkDiagnostics.token("probe:unrelated-poster") in report)
        } finally { loader.shutdown() }
    }
}
