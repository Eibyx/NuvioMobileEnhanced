package com.nuvio.app.core.storage

import com.nuvio.app.features.home.HomeSnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.time.Clock

@Serializable
internal data class MetadataCacheEntry(val key: String, val savedAt: Long, val payload: String)

internal fun validMetadataCacheEntry(entry: MetadataCacheEntry, now: Long, ttl: Long): Boolean =
    entry.savedAt <= now && now - entry.savedAt < ttl

/** Public TMDB responses only. Reuses AppFileCache's atomic storage, bounded across ALL profiles. */
internal object MetadataDiskCache : MetadataResponseCache() {
    fun scopeKey(language: String): String = "${HomeSnapshotStore.currentProfileKey().orEmpty()}|$language"
}

internal open class MetadataResponseCache(
    private val readFile: (String) -> String? = AppFileCache::read,
    private val writeFile: (String, String) -> Unit = AppFileCache::write,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val maxEntries: Int = 96,
    private val maxBytes: Int = 4 * 1024 * 1024,
) {
    private val path = "metadata/tmdb-v1.json"
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var entries: MutableList<MetadataCacheEntry>? = null

    private fun load(): MutableList<MetadataCacheEntry> = entries ?: run {
        val payload = runCatching { readFile(path) }.getOrNull()
        val loaded = if (payload != null && payload.encodeToByteArray().size <= maxBytes)
            runCatching { json.decodeFromString<List<MetadataCacheEntry>>(payload).takeLast(maxEntries).toMutableList() }.getOrNull()
        else null
        (loaded ?: mutableListOf()).also { entries = it }
    }

    suspend fun read(key: String, ttl: Long): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            load().lastOrNull { it.key == key && validMetadataCacheEntry(it, now(), ttl) }?.payload
        }
    }

    suspend fun write(key: String, payload: String) = withContext(Dispatchers.IO) {
        // Large or malformed provider responses must not grow this cache without bound.
        if (payload.encodeToByteArray().size > maxBytes / 4) return@withContext
        mutex.withLock {
            val values = load()
            values.removeAll { it.key == key }
            values.add(MetadataCacheEntry(key, now(), payload))
            while (values.size > maxEntries) values.removeAt(0)
            var encoded = json.encodeToString(values)
            while (encoded.encodeToByteArray().size > maxBytes && values.isNotEmpty()) {
                values.removeAt(0)
                encoded = json.encodeToString(values)
            }
            // Cache I/O failure cannot turn successful network metadata into a UI failure.
            runCatching { writeFile(path, encoded) }
        }
        Unit
    }
}
