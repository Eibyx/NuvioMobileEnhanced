package com.nuvio.app.features.livetv

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults

actual object LiveTvStorage {
    private const val playlistUrlKey = "playlist_url"
    private const val playlistsBlobKey = "playlists_blob"
    private const val favoriteChannelIdsBlobKey = "favorite_channel_ids_blob"
    private const val lastWatchedChannelIdKey = "last_watched_channel_id"
    private const val navigationEnabledKey = "navigation_enabled"
    private const val stalkerSettingsKey = "stalker_settings"
    private const val xtreamSettingsKey = "xtream_settings"
    private const val epgSourcesBlobKey = "epg_sources_blob"
    private const val epgAutoDetectEnabledKey = "epg_auto_detect_enabled"
    private const val nativeNavigationVisibleKey = "NuvioLiveTvNavigationVisible"
    private const val nativeNavigationDidChangeNotification = "NuvioLiveTvNavigationVisibilityDidChange"

    actual fun loadPlaylistUrl(): String? =
        readLiveTvScoped(playlistUrlKey) { NSUserDefaults.standardUserDefaults.stringForKey(it) }

    actual fun savePlaylistUrl(url: String) {
        NSUserDefaults.standardUserDefaults.setObject(url, forKey = LiveTvProfileScope.key(playlistUrlKey))
    }

    actual fun loadPlaylistsBlob(): String? =
        readLiveTvScoped(playlistsBlobKey) { NSUserDefaults.standardUserDefaults.stringForKey(it) }

    actual fun savePlaylistsBlob(blob: String) {
        NSUserDefaults.standardUserDefaults.setObject(blob, forKey = LiveTvProfileScope.key(playlistsBlobKey))
    }

    actual fun loadFavoriteChannelIdsBlob(): String? =
        readLiveTvScoped(favoriteChannelIdsBlobKey) { NSUserDefaults.standardUserDefaults.stringForKey(it) }

    actual fun saveFavoriteChannelIdsBlob(blob: String) {
        NSUserDefaults.standardUserDefaults.setObject(blob, forKey = LiveTvProfileScope.key(favoriteChannelIdsBlobKey))
    }

    actual fun loadLastWatchedChannelId(): String? =
        readLiveTvScoped(lastWatchedChannelIdKey) { NSUserDefaults.standardUserDefaults.stringForKey(it) }

    actual fun saveLastWatchedChannelId(channelId: String) {
        NSUserDefaults.standardUserDefaults.setObject(channelId, forKey = LiveTvProfileScope.key(lastWatchedChannelIdKey))
    }

    actual fun loadNavigationEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(navigationEnabledKey)
        return if (defaults.objectForKey(key) == null) null else defaults.boolForKey(key)
    }

    actual fun saveNavigationEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(
            enabled,
            forKey = ProfileScopedKey.of(navigationEnabledKey),
        )
    }

    actual fun loadStalkerSettings(): LiveTvStalkerSettings =
        readLiveTvScoped(stalkerSettingsKey) { NSUserDefaults.standardUserDefaults.stringForKey(it) }
            ?.split('\u001F')
            ?.let { LiveTvStalkerSettings(it.getOrNull(0).orEmpty(), it.getOrNull(1).orEmpty(), it.getOrNull(2).orEmpty(), it.getOrNull(3).orEmpty(), it.getOrNull(4) != "false") }
            ?: LiveTvStalkerSettings()

    actual fun saveStalkerSettings(settings: LiveTvStalkerSettings) {
        NSUserDefaults.standardUserDefaults.setObject(listOf(settings.portalUrl, settings.macAddress, settings.username, settings.password, settings.isEnabled).joinToString("\u001F"), forKey = LiveTvProfileScope.key(stalkerSettingsKey))
    }

    actual fun loadXtreamSettings(): LiveTvXtreamSettings =
        readLiveTvScoped(xtreamSettingsKey) { NSUserDefaults.standardUserDefaults.stringForKey(it) }
            ?.split('\u001F')
            ?.let { LiveTvXtreamSettings(it.getOrNull(0).orEmpty(), it.getOrNull(1).orEmpty(), it.getOrNull(2).orEmpty(), it.getOrNull(3) != "false") }
            ?: LiveTvXtreamSettings()

    actual fun saveXtreamSettings(settings: LiveTvXtreamSettings) {
        NSUserDefaults.standardUserDefaults.setObject(listOf(settings.serverUrl, settings.username, settings.password, settings.isEnabled).joinToString("\u001F"), forKey = LiveTvProfileScope.key(xtreamSettingsKey))
    }

    actual fun publishNavigationVisibility(visible: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(visible, forKey = nativeNavigationVisibleKey)
        NSNotificationCenter.defaultCenter.postNotificationName(
            nativeNavigationDidChangeNotification,
            null,
        )
    }

    actual fun loadEpgSourcesBlob(): String? =
        readLiveTvScoped(epgSourcesBlobKey) { NSUserDefaults.standardUserDefaults.stringForKey(it) }

    actual fun saveEpgSourcesBlob(blob: String) {
        NSUserDefaults.standardUserDefaults.setObject(blob, forKey = LiveTvProfileScope.key(epgSourcesBlobKey))
    }

    actual fun loadEpgAutoDetectEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = LiveTvProfileScope.readKeys(epgAutoDetectEnabledKey).firstOrNull { defaults.objectForKey(it) != null } ?: return null
        return defaults.boolForKey(key)
    }

    actual fun saveEpgAutoDetectEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(
            enabled,
            forKey = LiveTvProfileScope.key(epgAutoDetectEnabledKey),
        )
    }
}
