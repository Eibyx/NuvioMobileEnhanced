package com.nuvio.app.features.player

import com.nuvio.app.navigation.PlayerRoute
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerExitPictureInPictureTest {
    private val route = PlayerRoute(launchId = 9_001L)

    @AfterTest
    fun resetController() {
        MiniPlayerController.close()
        MiniPlayerController.commit(topPlayerRoute = null, hostedRoute = null)
    }

    @Test
    fun `auto PiP only while an active player is playing and not exiting`() {
        assertTrue(isPictureInPictureAutoEnterAllowed(isActive = true, isPlaying = true, isExiting = false))
        assertFalse(isPictureInPictureAutoEnterAllowed(isActive = true, isPlaying = true, isExiting = true))
        assertFalse(isPictureInPictureAutoEnterAllowed(isActive = true, isPlaying = false, isExiting = false))
        assertFalse(isPictureInPictureAutoEnterAllowed(isActive = false, isPlaying = true, isExiting = false))
    }

    @Test
    fun `leaving the player with Back does not leave a mini player behind`() {
        assertEquals(route, MiniPlayerController.hostedRoute(topPlayerRoute = route, enabled = true))
        MiniPlayerController.commit(topPlayerRoute = route, hostedRoute = route)

        // Back pops the player route without requesting a minimize.
        val afterBack = MiniPlayerController.hostedRoute(topPlayerRoute = null, enabled = true)
        assertNull(afterBack)
        MiniPlayerController.commit(topPlayerRoute = null, hostedRoute = afterBack)
        assertFalse(MiniPlayerController.isActive)
    }

    @Test
    fun `the mini player still opens when requested manually`() {
        MiniPlayerController.commit(topPlayerRoute = route, hostedRoute = route)
        assertTrue(MiniPlayerController.requestMinimize())

        assertEquals(route, MiniPlayerController.hostedRoute(topPlayerRoute = null, enabled = true))
        MiniPlayerController.commit(topPlayerRoute = null, hostedRoute = route)
        assertTrue(MiniPlayerController.isActive)
    }
}
