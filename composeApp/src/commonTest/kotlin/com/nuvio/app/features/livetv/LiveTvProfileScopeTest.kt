package com.nuvio.app.features.livetv

import com.nuvio.app.features.profiles.NuvioProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveTvProfileScopeTest {
    @Test
    fun noActiveProfileUsesActiveId() {
        assertEquals(3, resolveLiveTvStorageProfileId(activeProfile = null, activeProfileId = 3))
    }

    @Test
    fun primaryProfileKeepsItsOwnScope() {
        val primary = NuvioProfile(profileIndex = 1, usesPrimaryAddons = true)
        assertEquals(1, resolveLiveTvStorageProfileId(primary, activeProfileId = 1))
    }

    @Test
    fun profileWithOwnAddonsKeepsItsOwnScope() {
        val profile = NuvioProfile(profileIndex = 2)
        assertEquals(2, resolveLiveTvStorageProfileId(profile, activeProfileId = 2))
    }

    @Test
    fun profileSharingPrimaryAddonsOrPluginsUsesPrimaryScope() {
        assertEquals(1, resolveLiveTvStorageProfileId(NuvioProfile(profileIndex = 2, usesPrimaryAddons = true), 2))
        assertEquals(1, resolveLiveTvStorageProfileId(NuvioProfile(profileIndex = 4, usesPrimaryPlugins = true), 4))
    }
}
