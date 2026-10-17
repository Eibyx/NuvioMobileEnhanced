package com.nuvio.app.features.details

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.EventListener
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.nuvio.app.features.details.components.DetailHero
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Controlled lifecycle reproduction on the ORIGINAL renderer. No HTTP server or transformations.
 * These tests cannot establish the cause of a physical phone's network/rendering delays. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp")
class OriginalDetailHeroLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private val starts = AtomicInteger()
    private val cancellations = AtomicInteger()
    private val loaded = AtomicInteger()
    private val sources = CopyOnWriteArrayList<DataSource>()
    private lateinit var loader: ImageLoader
    private lateinit var list: LazyListState
    private lateinit var scope: CoroutineScope

    private fun show(blockFirstFetch: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        loader = ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(8L * 1024 * 1024).build() }
            .crossfade(false)
            .eventListenerFactory {
                object : EventListener() {
                    override fun onSuccess(request: ImageRequest, result: SuccessResult) { sources += result.dataSource }
                }
            }
            .components {
                add(Fetcher.Factory<Uri> { data, _, _ ->
                    if (data.toString() != "probe:backdrop") null else Fetcher {
                        val count = starts.incrementAndGet()
                        if (blockFirstFetch && count == 1) {
                            try { awaitCancellation() } catch (cancelled: CancellationException) {
                                cancellations.incrementAndGet()
                                throw cancelled
                            }
                        }
                        ImageFetchResult(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).asImage(), false, DataSource.MEMORY)
                    }
                })
            }.build()
        SingletonImageLoader.setUnsafe(loader)
        compose.setContent {
            list = rememberLazyListState()
            scope = rememberCoroutineScope()
            MaterialTheme {
                LazyColumn(state = list, modifier = Modifier.width(390.dp)) {
                    item(key = "detail-hero") {
                        DetailHero(
                            meta = MetaDetails(id = "probe", type = "movie", name = "Probe", background = "probe:backdrop"),
                            onBackdropLoaded = { _, _ -> loaded.incrementAndGet() },
                        )
                    }
                    items(40) { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }
    private fun scrollTo(index: Int) {
        compose.runOnIdle { scope.launch { list.scrollToItem(index) } }
        compose.waitForIdle()
    }
    @After fun release() {
        SingletonImageLoader.reset()
        if (::loader.isInitialized) loader.shutdown()
    }
    @Test fun loadedBackdropReturnsFromMemoryAfterLazyItemDisposal() {
        show()
        compose.waitUntil(5_000) { loaded.get() == 1 }
        scrollTo(30)
        scrollTo(0)
        compose.waitUntil(5_000) { loaded.get() >= 2 }
        assertEquals(1, starts.get(), "Recomposition alone must not imply a second fetch")
        assertEquals(listOf(DataSource.MEMORY, DataSource.MEMORY_CACHE), sources.toList())
    }
    @Test fun cancelledBackdropRequestRestartsWhenLazyItemReturns() {
        show(blockFirstFetch = true)
        compose.waitUntil(5_000) { starts.get() == 1 }
        scrollTo(30)
        compose.waitUntil(5_000) { cancellations.get() == 1 }
        scrollTo(0)
        compose.waitUntil(5_000) { loaded.get() >= 1 }
        assertEquals(2, starts.get())
        assertEquals(listOf(DataSource.MEMORY), sources.toList())
    }
    @Test fun evictedBackdropCanFetchAgainWhenLazyItemReturns() {
        show()
        compose.waitUntil(5_000) { loaded.get() == 1 }
        scrollTo(30)
        compose.runOnIdle { loader.memoryCache!!.clear() }
        scrollTo(0)
        compose.waitUntil(5_000) { loaded.get() >= 2 }
        assertTrue(starts.get() >= 2, "A new fetch after eviction is expected; not proof of a network download")
    }
}
