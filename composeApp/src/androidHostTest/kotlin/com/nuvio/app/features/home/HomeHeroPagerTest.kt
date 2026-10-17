package com.nuvio.app.features.home

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.home.components.HomeHeroSection
import com.nuvio.app.features.home.components.rememberHeroArtworkContinuityAllowed
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp")
class HomeHeroPagerTest {
    @get:Rule
    val compose = createComposeRule()

    private val items = List(8) { index ->
        MetaPreview(id = "hero-$index", type = "movie", name = "Hero $index")
    }

    @Test
    fun swipingAcrossEitherEndOnlyShowsTheFirstAndLastTitles() {
        showHero()

        swipeRight()
        assertWrapFrames(expectedIndex = 7)

        compose.onNodeWithTag("hero").performTouchInput {
            swipe(Offset(width * 0.7f, height * 0.3f), Offset(width * 0.3f, height * 0.3f))
        }
        assertWrapFrames(expectedIndex = 0)
    }

    @Test
    fun automaticCyclingFromLastToFirstDoesNotShowOtherTitles() {
        showHero()
        swipeRight()
        assertWrapFrames(expectedIndex = 7)

        compose.mainClock.advanceTimeBy(6_000L)
        assertWrapFrames(expectedIndex = 0)
    }

    @Test fun validArtworkRemainsEligibleForContinuityAfterLongIdle() {
        var allowed = false
        compose.mainClock.autoAdvance = false
        compose.setContent { allowed = rememberHeroArtworkContinuityAllowed("current", complete = true, failed = false) }
        compose.mainClock.advanceTimeBy(30_000)
        compose.runOnIdle { assertTrue(allowed) }
    }

    @Test fun pendingArtworkRetentionIsBoundedAndRecoversOnSuccessButNotError() {
        val complete = mutableStateOf(false)
        val failed = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            androidx.compose.foundation.text.BasicText(
                if (rememberHeroArtworkContinuityAllowed("pending", complete.value, failed.value)) "allowed" else "expired",
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("allowed").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(16_000)
        compose.onNodeWithText("expired").assertIsDisplayed()
        // Let the rule synchronize recomposition for externally delivered success/error.
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { complete.value = true }
        compose.onNodeWithText("allowed").assertIsDisplayed()
        compose.runOnIdle { failed.value = true }
        compose.onNodeWithText("expired").assertIsDisplayed()
    }

    @Test fun twoItemsCanChangePagesWithoutDuplicateCompositionKeys() {
        showHero(2)
        swipeRight()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Hero 1").assertIsDisplayed()
    }

    @Test fun oneItemDoesNotCreateAdditionalLayers() {
        showHero(1)
        compose.mainClock.advanceTimeBy(12_000)
        compose.onNodeWithText("Hero 0").assertIsDisplayed()
    }

    @Test fun threeItemsCanChangePages() {
        showHero(3)
        swipeRight()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Hero 2").assertIsDisplayed()
    }

    private fun showHero(itemCount: Int = 8) {
        compose.setContent {
            MaterialTheme {
                HomeHeroSection(
                    items = items.take(itemCount),
                    modifier = Modifier.width(390.dp).testTag("hero"),
                )
            }
        }
        compose.onNodeWithText("Hero 0").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
    }

    private fun swipeRight() {
        compose.onNodeWithTag("hero").performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.3f), Offset(width * 0.7f, height * 0.3f))
        }
    }

    private fun assertWrapFrames(expectedIndex: Int) {
        val intermediateTitle = (1..6)
            .map { hasText("Hero $it") }
            .reduce { matcher, next -> matcher or next }
        repeat(100) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(intermediateTitle).assertCountEquals(0)
        }
        compose.onNodeWithText("Hero $expectedIndex").assertIsDisplayed()
    }
}
