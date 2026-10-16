// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.mal

import com.nuvio.app.features.anilist.AniListTrackingWrites
import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingHistoryWriter
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingMutationResult
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import com.nuvio.app.features.tracking.TrackingScrobbler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Automatic MyAnimeList progress updates. Mirrors [AniListTrackingWrites]: only a finished playback
 * (STOP at or above the AniList completion threshold) is written, and progress only ever moves forward.
 */
class MalTrackingWrites internal constructor(
    private val store: MalAuthStore,
    private val api: MalApiClient,
    private val resolver: MalIdResolver,
) : TrackingScrobbler, TrackingHistoryWriter {
    override val providerId = TrackingProviderId.MAL
    private val writeLock = Mutex()

    override suspend fun scrobble(profileId: Int, action: TrackingScrobbleAction, event: TrackingScrobbleEvent) {
        if (action != TrackingScrobbleAction.STOP) return
        if (event.progressPercent < COMPLETION_THRESHOLD_PERCENT) return
        val scope = scope(profileId)
        val target = resolve(event.media) ?: return
        writeLock.withLock {
            updateProgress(scope, target.malId, target.episode)
        }
    }

    override suspend fun addToHistory(profileId: Int, items: Collection<TrackingHistoryItem>): TrackingMutationResult {
        val scope = scope(profileId)
        val targets = mutableMapOf<Long, Int>()
        var notFound = 0
        items.forEach { item ->
            val target = resolve(item.media)
            if (target == null) {
                notFound += 1
            } else {
                targets[target.malId] = maxOf(targets[target.malId] ?: 0, target.episode)
            }
        }
        writeLock.withLock {
            targets.forEach { (malId, episode) -> updateProgress(scope, malId, episode) }
        }
        return TrackingMutationResult(attemptedCount = items.size, notFoundCount = notFound)
    }

    override suspend fun removeFromHistory(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
    ): TrackingMutationResult {
        val scope = scope(profileId)
        val targets = mutableMapOf<Long, Int>()
        var notFound = 0
        items.forEach { media ->
            val target = resolve(media)
            if (target == null) {
                notFound += 1
            } else {
                targets[target.malId] = minOf(targets[target.malId] ?: Int.MAX_VALUE, target.episode)
            }
        }
        writeLock.withLock {
            targets.forEach { (malId, episode) ->
                val anime = api.anime(scope, malId) ?: return@forEach
                val update = malProgressRemovalFor(anime, episode) ?: return@forEach
                api.updateListStatus(scope, malId, update)
            }
        }
        return TrackingMutationResult(attemptedCount = items.size, notFoundCount = notFound)
    }

    private suspend fun updateProgress(scope: MalAuthScope, malId: Long, episode: Int) {
        val anime = api.anime(scope, malId) ?: return
        val update = malProgressUpdateFor(anime, episode) ?: return
        api.updateListStatus(scope, malId, update)
    }

    private suspend fun resolve(media: TrackingMediaReference): MalTarget? = try {
        resolver.resolve(media)
    } catch (error: CancellationException) {
        throw error
    } catch (error: MalAuthException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun scope(profileId: Int): MalAuthScope = store.scope().also {
        if (it.profileId != profileId) throw CancellationException("MyAnimeList profile changed")
    }

    companion object {
        /** Same threshold AniList uses to count an episode as watched. */
        const val COMPLETION_THRESHOLD_PERCENT = AniListTrackingWrites.COMPLETION_THRESHOLD_PERCENT
    }
}
