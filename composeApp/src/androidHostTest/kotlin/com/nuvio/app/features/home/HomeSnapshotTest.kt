package com.nuvio.app.features.home

import android.app.Application
import com.nuvio.app.core.storage.AppFileCache
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeSnapshotTest {
    @BeforeTest fun initialize() { AppFileCache.initialize(RuntimeEnvironment.getApplication()) }
    @Test fun collectionOnlyHeroSurvivesRestartAndRetainsCustomArtwork() {
        val hero = MetaPreview("anime", "series", "Anime", poster = "custom", rawPosterUrl = "original", banner = "background")
        HomeSnapshotStore.save("profile1", Clock.System.now().toEpochMilliseconds(), emptyList(), listOf(hero))
        val loaded = assertNotNull(HomeSnapshotStore.load("profile1"))
        assertTrue(loaded.sections.isEmpty())
        assertEquals(listOf(hero), loaded.heroItems)
        assertNull(HomeSnapshotStore.load("anotherProfile"))
    }
    @Test fun interruptedWriteKeepsTheLastValidSnapshot() {
        val profile = "interrupted"
        val hero = MetaPreview("kept", "movie", "Last valid")
        HomeSnapshotStore.save(profile, Clock.System.now().toEpochMilliseconds(), emptyList(), listOf(hero))
        val target = java.io.File(RuntimeEnvironment.getApplication().cacheDir,
            "nuvio_file_cache/home/${profile.length}_${profile.hashCode().toUInt()}.json")
        android.util.AtomicFile(target).startWrite().use { it.write("incomplete".toByteArray()) }
        assertEquals(listOf(hero), HomeSnapshotStore.load(profile)?.heroItems)
    }
    @Test fun expiredSnapshotsAreNotRestored() {
        HomeSnapshotStore.save("expired", 1, emptyList(), listOf(MetaPreview("id", "movie", "old")))
        assertNull(HomeSnapshotStore.load("expired"))
    }
    @Test fun snapshotBoundsHeroAndRejectsCorruptPayload() {
        HomeSnapshotStore.save("bounded", Clock.System.now().toEpochMilliseconds(), emptyList(), List(40) { MetaPreview("$it", "movie", "$it") })
        assertEquals(8, HomeSnapshotStore.load("bounded")?.heroItems?.size)
        val key = "broken"
        AppFileCache.write("home/${key.length}_${key.hashCode().toUInt()}.json", "broken-json")
        assertNull(HomeSnapshotStore.load(key))
    }
}
