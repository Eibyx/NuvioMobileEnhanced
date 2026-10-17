package com.nuvio.app.core.ui

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.Path.Companion.toPath
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalCoilApi::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ImageCacheIntegrationTest {
    private fun success(result: coil3.request.ImageResult): SuccessResult = result as? SuccessResult
        ?: throw AssertionError("Coil request failed", (result as? coil3.request.ErrorResult)?.throwable)
    private val context get() = RuntimeEnvironment.getApplication()
    private fun response(policy: String) = MockResponse().setHeader("Cache-Control", policy)
        .setHeader("Content-Type", "image/png").setBody(Buffer().write(png()))
    private fun png(): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
            .compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
    private fun loader(directory: File) = ImageLoader.Builder(context)
        .diskCache { DiskCache.Builder().directory(directory.absolutePath.toPath()).maxSizeBytes(4L * 1024 * 1024).build() }
        .components { add(KtorNetworkFetcherFactory(
            cacheStrategy = { ObservedImageCacheStrategy() },
            concurrentRequestStrategy = { DeDupeConcurrentRequestStrategy() },
        )) }.build()
    private fun request(url: String, memory: Boolean = true, size: Int = 40) = ImageRequest.Builder(context)
        .data(url).size(size, size).memoryCachePolicy(if (memory) CachePolicy.ENABLED else CachePolicy.DISABLED).build()

    @Test fun memoryDiskAndProcessRestartReuseTheSameDownloadedFile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response("public, max-age=3600"))
            val directory = File(context.cacheDir, "coil-restart-${System.nanoTime()}")
            val url = server.url("/image.png").toString()
            val first = loader(directory)
            assertEquals(DataSource.NETWORK, success(first.execute(request(url))).dataSource)
            assertEquals(DataSource.MEMORY_CACHE, success(first.execute(request(url))).dataSource)
            first.memoryCache?.clear()
            assertEquals(DataSource.DISK, success(first.execute(request(url))).dataSource)
            first.shutdown()
            first.diskCache?.shutdown()
            val restarted = loader(directory)
            try {
                assertEquals(DataSource.DISK, success(restarted.execute(request(url).newBuilder().networkCachePolicy(CachePolicy.DISABLED).build())).dataSource)
                assertEquals(1, server.requestCount)
            } finally { restarted.shutdown(); restarted.diskCache?.shutdown(); directory.deleteRecursively() }
        }
    }
    @Test fun conditional304ReusesTheBodyAndNoStoreIsRespected() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response("public, max-age=0").setHeader("ETag", "v1"))
            server.enqueue(MockResponse().setResponseCode(304).setHeader("Cache-Control", "public, max-age=3600"))
            server.enqueue(response("no-store"))
            server.enqueue(response("no-store"))
            val directory = File(context.cacheDir, "coil-http-${System.nanoTime()}")
            val loader = loader(directory)
            try {
                val url = server.url("/conditional.png").toString()
                success(loader.execute(request(url, false)))
                val validated = success(loader.execute(request(url, false)))
                assertTrue(validated.image.width > 0)
                server.takeRequest()
                assertEquals("v1", server.takeRequest().getHeader("If-None-Match"))
                val privateUrl = server.url("/no-store.png").toString()
                repeat(2) { success(loader.execute(request(privateUrl, false))) }
                assertNull(loader.diskCache?.openSnapshot(privateUrl))
                assertEquals(4, server.requestCount)
            } finally { loader.shutdown(); loader.diskCache?.shutdown(); directory.deleteRecursively() }
        }
    }
    @Test fun concurrentVariantsShareFetchAndDoNotReuseAnUndersizedBitmap() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response("public, max-age=3600"))
            val directory = File(context.cacheDir, "coil-concurrent-${System.nanoTime()}")
            val loader = loader(directory)
            try {
                val url = server.url("/concurrent.png").toString()
                coroutineScope {
                    val small = async { loader.execute(request(url, size = 16)) }
                    val large = async { loader.execute(request(url, size = 80)) }
                    success(small.await())
                    val result = success(large.await())
                    assertTrue(result.image.width >= 80)
                }
                assertEquals(1, server.requestCount)
            } finally { loader.shutdown(); loader.diskCache?.shutdown(); directory.deleteRecursively() }
        }
    }
}
