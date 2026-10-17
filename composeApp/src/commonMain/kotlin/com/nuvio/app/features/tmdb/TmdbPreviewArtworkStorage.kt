package com.nuvio.app.features.tmdb

import com.nuvio.app.core.storage.AppFileCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** Only public artwork metadata is persisted. One bounded file uses the existing app cache,
 * so Android/iOS retain their storage lifecycle and atomic-write implementations.
 */
@Serializable
internal data class TmdbPreviewArtwork(
    val localizedTitle: String? = null,
    val description: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val releaseInfo: String? = null,
    val runtime: String? = null,
    val tmdbRating: Double? = null,
)

@Serializable
internal data class StoredPreviewArtwork(
    val key: String,
    val savedAtMs: Long,
    val artwork: TmdbPreviewArtwork,
)

internal class PreviewArtworkStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val now: () -> Long,
    private val capacity: Int = 128,
    private val ttlMs: Long = 7L * 24 * 60 * 60 * 1000,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var entries: List<StoredPreviewArtwork>? = null

    private fun current(): List<StoredPreviewArtwork> {
        val loaded = entries ?: runCatching {
            read()?.let { json.decodeFromString<List<StoredPreviewArtwork>>(it) }
        }.getOrNull().orEmpty()
        return loaded.filter { now() - it.savedAtMs in 0 until ttlMs }.takeLast(capacity).also { entries = it }
    }

    suspend fun load(key: String): TmdbPreviewArtwork? = mutex.withLock {
        current().lastOrNull { it.key == key }?.artwork
    }

    suspend fun save(key: String, artwork: TmdbPreviewArtwork) = mutex.withLock {
        val next = (current().filterNot { it.key == key } + StoredPreviewArtwork(key, now(), artwork)).takeLast(capacity)
        entries = next
        // Storage failure must never prevent visible artwork from being published.
        runCatching { write(json.encodeToString(next)) }
        Unit
    }
}

internal object TmdbPreviewArtworkStorage {
    private val store = PreviewArtworkStore(
        read = { AppFileCache.read("hero/tmdb_preview_artwork_v1.json") },
        write = { AppFileCache.write("hero/tmdb_preview_artwork_v1.json", it) },
        now = { Clock.System.now().toEpochMilliseconds() },
    )
    suspend fun load(key: String): TmdbPreviewArtwork? = store.load(key)
    suspend fun save(key: String, artwork: TmdbPreviewArtwork) = store.save(key, artwork)
}

internal fun previewArtworkKey(profile: String, type: String, id: String, language: String): String =
    "$profile|$type|$id|${normalizeTmdbLanguage(language)}"
