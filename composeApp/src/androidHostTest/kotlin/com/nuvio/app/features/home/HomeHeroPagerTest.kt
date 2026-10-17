package com.nuvio.app.features.home

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.home.components.HomeHeroSection
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

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

    @Test
    fun twoItemsWrapWithoutSiblingKeyCollisions() {
        showHero(count = 2)
        swipeRight()
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithText("HERO 1").assertIsDisplayed()
        swipeRight()
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithText("HERO 0").assertIsDisplayed()
    }

    @Test
    fun oneItemStaysSelectedAndThreeItemsWrap() {
        showHero(count = 1)
        compose.mainClock.advanceTimeBy(20_000L)
        compose.onNodeWithText("HERO 0").assertIsDisplayed()
    }

    @Test
    fun threeItemsWrapToTheLastTitle() {
        showHero(count = 3)
        swipeRight()
        compose.mainClock.advanceTimeBy(1_000L)
        compose.onNodeWithText("HERO 2").assertIsDisplayed()
    }

    @Test
    fun showcaseDetailsAndLibraryActionsReceiveTheSelectedEibyxPreview() {
        var detailsId: String? = null
        var libraryId: String? = null
        compose.setContent {
            MaterialTheme {
                HomeHeroSection(
                    items = items.take(2),
                    autoScrollEnabled = false,
                    modifier = Modifier.width(390.dp).testTag("hero"),
                    onItemClick = { detailsId = it.id },
                    onSaveClick = { libraryId = it.id },
                )
            }
        }
        compose.onNodeWithText("View details", ignoreCase = true).performClick()
        compose.onNodeWithText("Save", ignoreCase = true).performClick()
        kotlin.test.assertEquals("hero-0", detailsId)
        kotlin.test.assertEquals("hero-0", libraryId)
    }

    private fun showHero(count: Int = 8) {
        compose.setContent {
            MaterialTheme {
                HomeHeroSection(
                    items = items.take(count),
                    modifier = Modifier.width(390.dp).testTag("hero"),
                )
            }
        }
        compose.onNodeWithText("HERO 0").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
    }

    private fun swipeRight() {
        compose.onNodeWithTag("hero").performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.3f), Offset(width * 0.7f, height * 0.3f))
        }
    }

    private fun assertWrapFrames(expectedIndex: Int) {
        val intermediateTitle = (1..6)
            .map { hasText("HERO $it") }
            .reduce { matcher, next -> matcher or next }
        repeat(100) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(intermediateTitle).assertCountEquals(0)
        }
        compose.onNodeWithText("HERO $expectedIndex").assertIsDisplayed()
    }
}
