package com.nuvio.app.features.settings

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nuvio.app.core.diagnostics.ArtworkDiagnostics
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "en-rUS-w390dp-h844dp")
class ArtworkDiagnosticsSectionTest {
    @get:Rule val compose = createComposeRule()
    @Before @After fun resetProviderCacheBetweenRobolectricApplications() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }
    @After fun stop() { ArtworkDiagnostics.install(null) }
    @Test fun controlsStartStopClearAndLaunchNativeChooser() {
        ArtworkDiagnostics.configure(true)
        compose.setContent { MaterialTheme { Column { ArtworkDiagnosticsSection(false) } } }
        assertFalse(ArtworkDiagnostics.enabled)
        compose.onNodeWithText("Enable diagnostics").performClick()
        compose.runOnIdle { assertTrue(ArtworkDiagnostics.enabled); ArtworkDiagnostics.event("hero.test") { "marker=present" } }
        compose.onNodeWithText("Disable diagnostics").performClick()
        compose.runOnIdle { assertFalse(ArtworkDiagnostics.enabled) }
        compose.onNodeWithText("Copy diagnostic summary").performClick()
        compose.waitUntil(5_000) {
            val clipboard = RuntimeEnvironment.getApplication().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.text?.contains("marker=present") == true
        }
        compose.onNodeWithText("Export diagnostic (.txt)").performClick()
        var chooser: Intent? = null
        compose.waitUntil(5_000) {
            chooser = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            chooser != null
        }
        assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)
        assertEquals(Intent.ACTION_SEND, chooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.action)
        compose.onNodeWithText("Clear records").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertFalse("marker=present" in ArtworkDiagnostics.report()) }
    }
}
