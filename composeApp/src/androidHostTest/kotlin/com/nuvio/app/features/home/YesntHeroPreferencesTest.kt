package com.nuvio.app.features.home

import android.app.Application
import android.content.Context
import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.AvixPreferencesReload
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class YesntHeroPreferencesTest {
    @BeforeTest fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("avix_local_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        AvixLocalStore.initialize(context)
        AvixPreferencesReload.reloadAll()
        AvixHomeLayoutPreferences.ensureLoaded()
    }

    @AfterTest fun cleanup() = setup()

    @Test fun existingAndNewHeroPreferencesSurviveReloadWithoutChangingShelfPreferences() {
        AvixHomeLayoutPreferences.setShelfRows(3)
        AvixHomeLayoutPreferences.setHeroAutoScroll(false)
        AvixHomeLayoutPreferences.setHeroPosterArtwork(true)
        AvixHomeLayoutPreferences.setHeroMotionPreview(false)
        AvixHomeLayoutPreferences.setHeroShowOverview(false)
        AvixHomeLayoutPreferences.setHeroDisplayMode(AvixHeroDisplayMode.INFO_RICH)
        AvixPreferencesReload.reloadAll()
        val layout = AvixHomeLayoutPreferences.layout.value
        assertEquals(3, layout.shelfRows)
        assertFalse(layout.heroAutoScroll)
        assertTrue(layout.hero.posterArtwork)
        assertFalse(layout.hero.motionPreview)
        assertFalse(layout.hero.showOverview)
        assertEquals(AvixHeroDisplayMode.INFO_RICH, layout.hero.displayMode)
    }

    @Test fun defaultSelectsStreamingShowcaseAndKeepsMotionAndRatingsEnabled() {
        val hero = AvixHomeLayoutPreferences.layout.value.hero
        assertFalse(hero.posterArtwork)
        assertTrue(hero.motionPreview)
        assertTrue(hero.showRatings)
        assertTrue(hero.showOverview)
    }
}
