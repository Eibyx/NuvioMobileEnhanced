@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.nuvio.app.core.concurrency

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SharedRequestsTest {
    @Test fun sharesWorkAndKeepsProducerWhenOneConsumerLeaves() = runTest {
        val requests = SharedRequests<String, Int>(backgroundScope)
        val gate = CompletableDeferred<Int>()
        var calls = 0
        val first = async { requests.await("same") { calls++; gate.await() } }
        val second = async { requests.await("same") { error("duplicate producer") } }
        runCurrent()
        assertEquals(1, calls)
        first.cancelAndJoin()
        assertFalse(gate.isCancelled)
        gate.complete(7)
        assertEquals(7, second.await())
    }

    @Test fun lastConsumerCancelsProducerAndRetryIsIndependent() = runTest {
        val requests = SharedRequests<String, Int>(backgroundScope)
        var cancelled = false
        val consumer = async { requests.await("same") { try { awaitCancellation() } finally { cancelled = true } } }
        runCurrent()
        consumer.cancelAndJoin()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(9, requests.await("same") { 9 })
    }

    @Test fun errorsReachAllConsumersAndDoNotPoisonRetry() = runTest {
        val requests = SharedRequests<String, Int>(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        supervisorScope {
            val first = async { runCatching { requests.await("same") { gate.await(); error("provider failure") } } }
            val second = async { runCatching { requests.await("same") { error("duplicate") } } }
            runCurrent()
            gate.complete(Unit)
            assertEquals("provider failure", first.await().exceptionOrNull()?.message)
            assertEquals("provider failure", second.await().exceptionOrNull()?.message)
        }
        assertEquals(3, requests.await("same") { 3 })
    }

    @Test fun profileLanguageAndContentKeysDoNotShareResults() = runTest {
        val requests = SharedRequests<String, Int>(backgroundScope)
        val keys = listOf("profile1:es:movie1", "profile2:es:movie1", "profile1:en:movie1", "profile1:es:movie2")
        val results = keys.mapIndexed { index, key -> async { requests.await(key) { index } } }
        assertEquals(listOf(0, 1, 2, 3), results.map { it.await() })
    }
}
