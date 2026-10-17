package com.nuvio.app.features.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Work belongs to the Hero session, rather than the caller awaiting it during a swipe.
 * Cancelling one waiter does not cancel another; leaving the session cancels all work.
 * Only successful values are retained, and the session has a fixed capacity.
 */
internal class HeroRequestCoordinator<T : Any>(
    private val scope: CoroutineScope,
    private val capacity: Int = 16,
    private val retainCompleted: Boolean = true,
) {
    private val mutex = Mutex()
    private val requests = linkedMapOf<String, Deferred<T?>>()

    suspend fun get(key: String, load: suspend () -> T?): T? {
        val request = mutex.withLock {
            requests.entries.removeAll { (_, value) -> value.isCancelled || (!retainCompleted && value.isCompleted) }
            requests[key] ?: scope.async(start = CoroutineStart.LAZY) { load() }.also { created ->
                // Completed entries can be evicted; in-flight work is always shared.
                while (requests.size >= capacity) {
                    val oldest = requests.entries.firstOrNull { it.value.isCompleted } ?: requests.entries.first()
                    requests.remove(oldest.key)
                    if (!oldest.value.isCompleted) oldest.value.cancel()
                }
                requests[key] = created
            }
        }
        return try {
            request.await().also { value ->
                if (value == null || !retainCompleted) mutex.withLock { if (requests[key] === request) requests.remove(key) }
            }
        } catch (error: Throwable) {
            if (request.isCompleted) mutex.withLock { if (requests[key] === request) requests.remove(key) }
            throw error
        }
    }
}

/** Active first, then at most two speculative neighbours, with duplicate resources removed. */
internal fun heroMetadataPriority(items: List<MetaPreview>, activePage: Int): List<MetaPreview> {
    if (items.isEmpty()) return emptyList()
    val page = activePage.coerceIn(items.indices)
    return listOf(items[page], items[(page + 1) % items.size], items[(page - 1 + items.size) % items.size])
        .distinctBy(MetaPreview::stableKey)
}

/** Optional enrichment can fail independently, but cancellation always remains cancellation. */
internal suspend fun <T> heroAttempt(timeoutMs: Long, block: suspend () -> T): T? = try {
    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { block() }
} catch (error: kotlinx.coroutines.CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

internal fun heroPagerKey(item: MetaPreview, page: Int): String = "${item.stableKey()}:$page"
