package com.nuvio.app.features.home.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.crossfade
import com.nuvio.app.NuvioApplication
import com.nuvio.app.core.diagnostics.ArtworkDiagnostics
import com.nuvio.app.features.home.MetaPreview
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Observes the original renderer with a local fetcher; no phone/network benchmark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OriginalHeroDiagnosticsTest {
    @get:Rule val compose = createComposeRule()
    private var loader: coil3.ImageLoader? = null
    @After fun release() {
        SingletonImageLoader.reset(); loader?.shutdown(); ArtworkDiagnostics.install(null)
    }
    @Test fun originalHeroRecordsSelectionImageStateAndDisposalWithoutExtraFetches() {
        ArtworkDiagnostics.configure(true); ArtworkDiagnostics.start()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val fetches = AtomicInteger()
        loader = NuvioApplication().newImageLoader(context).newBuilder().crossfade(false).components {
            add(Fetcher.Factory<Uri> { data, _, _ ->
                if (!data.toString().startsWith("probe:")) null else Fetcher {
                    fetches.incrementAndGet()
                    ImageFetchResult(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).asImage(), false, DataSource.MEMORY)
                }
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader!!)
        val visible = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (visible.value) HomeHeroSection(
                    items = listOf(MetaPreview("diagnostic-probe", "movie", "Probe", poster = "probe:artwork", banner = "probe:artwork")),
                    viewportHeight = 844.dp, autoScrollEnabled = false,
                )
            }
        }
        compose.waitUntil(10_000) { "event=coil.success" in ArtworkDiagnostics.report() }
        compose.waitForIdle()
        // Idle/success alone do not require Robolectric to draw a frame.
        compose.onRoot().captureToImage()
        val report = ArtworkDiagnostics.report()
        listOf("hero.selected", "hero.metadata.basic", "hero.url", "compose.image.enter", "compose.image.Success", "draw.submitted").forEach {
            assertTrue("event=$it" in report, it)
        }
        assertTrue("purpose=HERO_VISIBLE" in report)
        assertEquals(1, fetches.get())
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertTrue("event=compose.image.dispose" in ArtworkDiagnostics.report())
    }
}
