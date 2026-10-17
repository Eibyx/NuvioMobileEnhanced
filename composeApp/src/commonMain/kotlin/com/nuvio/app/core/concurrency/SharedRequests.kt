package com.nuvio.app.core.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Share only work in flight. The last departing consumer cancels the producer. */
internal class SharedRequests<K, V>(scope: CoroutineScope) {
    private val scope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private class Entry<V>(val result: Deferred<V>, var consumers: Int = 0)
    private val mutex = Mutex()
    private val entries = mutableMapOf<K, Entry<V>>()

    suspend fun await(key: K, block: suspend () -> V): V {
        val entry = mutex.withLock {
            entries.getOrPut(key) { Entry(scope.async(start = CoroutineStart.LAZY) { block() }) }
                .also { it.consumers++ }
        }
        try {
            entry.result.start()
            return entry.result.await()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    entry.consumers--
                    if (entry.consumers == 0) {
                        if (entries[key] === entry) entries.remove(key)
                        if (!entry.result.isCompleted) entry.result.cancel()
                    }
                }
            }
        }
    }
}
