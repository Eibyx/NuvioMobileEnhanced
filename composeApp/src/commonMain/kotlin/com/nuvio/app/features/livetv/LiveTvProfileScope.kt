package com.nuvio.app.features.livetv

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileRepository

/**
 * Live TV storage scope (Yesnt10 port): a secondary profile that uses the primary profile's
 * addons or plugins also shares the primary profile's Live TV sources, favorites and EPG.
 * Profiles with their own addons keep their own Live TV data. Device-local only: Live TV
 * storage is never synced.
 */
internal object LiveTvProfileScope {
    const val PRIMARY_PROFILE_INDEX = 1

    /** Key that reads and writes go to for the active profile. */
    fun key(baseKey: String): String = ProfileScopedKey.of(baseKey, storageProfileId())

    /**
     * Keys to read, in order. A shared profile falls back to its own older data when the
     * primary profile has none, so nothing it saved before this change becomes unreachable.
     */
    fun readKeys(baseKey: String): List<String> {
        val ownKey = ProfileScopedKey.of(baseKey, ProfileRepository.activeProfileId)
        val scopedKey = key(baseKey)
        return if (scopedKey == ownKey) listOf(ownKey) else listOf(scopedKey, ownKey)
    }

    private fun storageProfileId(): Int = resolveLiveTvStorageProfileId(
        activeProfile = ProfileRepository.state.value.activeProfile,
        activeProfileId = ProfileRepository.activeProfileId,
    )
}

internal fun resolveLiveTvStorageProfileId(activeProfile: NuvioProfile?, activeProfileId: Int): Int {
    if (activeProfile == null) return activeProfileId
    return if (
        activeProfile.profileIndex != LiveTvProfileScope.PRIMARY_PROFILE_INDEX &&
        (activeProfile.usesPrimaryAddons || activeProfile.usesPrimaryPlugins)
    ) {
        LiveTvProfileScope.PRIMARY_PROFILE_INDEX
    } else {
        activeProfile.profileIndex
    }
}

/** First non-null value among [LiveTvProfileScope.readKeys]. */
internal inline fun <T : Any> readLiveTvScoped(baseKey: String, read: (String) -> T?): T? =
    LiveTvProfileScope.readKeys(baseKey).firstNotNullOfOrNull(read)
