package com.nuvio.app.core.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackdropLifetimeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun disposingLazyHeroDoesNotRestartItsScreenOwnedImageRequest() {
        val context = RuntimeEnvironment.getApplication()
        val loader = ImageLoader.Builder(context).build()
        var requests = 0
        var loaded = false
        val request = ImageRequest.Builder(context)
            .data(Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)).size(80, 80)
            .listener(onStart = { requests++ }, onSuccess = { _, _ -> loaded = true }).build()
        var scroll: ((Int) -> Unit)? = null
        compose.setContent {
            val painter = rememberAsyncImagePainter(request, imageLoader = loader)
            val list = rememberLazyListState()
            val scope = rememberCoroutineScope()
            scroll = { index -> scope.launch { list.scrollToItem(index) } }
            LazyColumn(state = list, modifier = Modifier.height(300.dp)) {
                item(key = "hero") {
                    BackdropImage("same", painter, "backdrop", Modifier.fillMaxWidth().height(200.dp).testTag("backdrop"))
                }
                items(50) { Box(Modifier.height(100.dp)) }
            }
        }
        compose.waitUntil(10_000) { loaded }
        compose.runOnIdle { scroll!!(40) }
        compose.waitForIdle()
        compose.runOnIdle { scroll!!(0) }
        compose.onNodeWithTag("backdrop").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests) }
        loader.shutdown()
    }
}
