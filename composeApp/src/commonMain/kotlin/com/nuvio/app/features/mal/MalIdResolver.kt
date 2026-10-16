// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.mal

import com.nuvio.app.features.anilist.AniListTarget
import com.nuvio.app.features.anilist.parseAniListAnimeVideoId
import com.nuvio.app.features.tracking.TrackingMediaReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class MalTarget(
    val malId: Long,
    val episode: Int,
)

/**
 * Resolves a MyAnimeList anime id and absolute episode number.
 *
 * Direct `mal:` ids are used as-is; everything else goes through the AniList resolver, which already
 * understands `anilist:`/`anidb:` ids, IMDb season/episode mapping (Simkl + ARM) and returns an
 * absolute episode, and is then converted with AniList's `idMal`.
 */
internal class MalIdResolver(
    private val resolveAniList: suspend (TrackingMediaReference) -> AniListTarget?,
    private val aniListToMal: suspend (Long) -> Long?,
    private val kitsuToMal: suspend (Long) -> Long?,
) {
    private val cacheLock = Mutex()
    // Only successful lookups are cached, so a transient network failure is retried next time.
    private val aniListCache = mutableMapOf<Long, Long>()
    private val kitsuCache = mutableMapOf<Long, Long>()

    suspend fun resolve(reference: TrackingMediaReference): MalTarget? {
        val episodeNumber = reference.episode?.number
        val nativeEpisode = (episodeNumber ?: 1).coerceAtLeast(1)

        parseAniListAnimeVideoId(reference.catalog?.videoId)?.let { parts ->
            val episode = (parts.episode ?: episodeNumber ?: 1).coerceAtLeast(1)
            when (parts.prefix) {
                "mal" -> return MalTarget(parts.id, episode)
                "kitsu" -> cachedKitsu(parts.id)?.let { return MalTarget(it, episode) }
                else -> Unit
            }
        }

        reference.ids.mal?.takeIf { it > 0L }?.let { return MalTarget(it, nativeEpisode) }
        reference.ids.kitsu?.let { kitsu -> cachedKitsu(kitsu)?.let { return MalTarget(it, nativeEpisode) } }

        val aniList = guarded { resolveAniList(reference) } ?: return null
        val malId = cachedAniList(aniList.mediaId) ?: return null
        return MalTarget(malId, aniList.episode.coerceAtLeast(1))
    }

    private suspend fun cachedAniList(mediaId: Long): Long? {
        cacheLock.withLock { aniListCache[mediaId]?.let { return it } }
        val resolved = guarded { aniListToMal(mediaId) }?.takeIf { it > 0L } ?: return null
        cacheLock.withLock { aniListCache[mediaId] = resolved }
        return resolved
    }

    private suspend fun cachedKitsu(kitsuId: Long): Long? {
        cacheLock.withLock { kitsuCache[kitsuId]?.let { return it } }
        val resolved = guarded { kitsuToMal(kitsuId) }?.takeIf { it > 0L } ?: return null
        cacheLock.withLock { kitsuCache[kitsuId] = resolved }
        return resolved
    }

    private suspend fun <T> guarded(block: suspend () -> T?): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}
