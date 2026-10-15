package com.nuvio.app.features.player

import android.os.SystemClock

/**
 * Avix: the mpvRx engine (MPVLib) is a process-wide singleton, unlike the previous per-instance
 * MPV API. A second `create()` while a core is alive aborts in native code, and a late `destroy()`
 * from a released view would tear down the core of the view that replaced it (engine switch,
 * next episode, reopening the player).
 *
 * Each libmpv view must [acquire] ownership before initializing and may only destroy the core
 * while it is the owner; [release] is called once its `destroy()` has completed.
 */
internal object LibmpvInstanceGuard {
    private val lock = Object()
    private var owner: Any? = null

    /** Waits up to [timeoutMs] for the previous core to be destroyed. Returns false on timeout. */
    fun acquire(newOwner: Any, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
        if (!LIBMPV_IS_SINGLETON) return true
        synchronized(lock) {
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (owner != null && owner !== newOwner) {
                val remaining = deadline - SystemClock.uptimeMillis()
                if (remaining <= 0) return false
                lock.wait(remaining)
            }
            owner = newOwner
            return true
        }
    }

    fun isOwner(candidate: Any): Boolean =
        !LIBMPV_IS_SINGLETON || synchronized(lock) { owner === candidate }

    fun release(oldOwner: Any) {
        if (!LIBMPV_IS_SINGLETON) return
        synchronized(lock) {
            if (owner === oldOwner) {
                owner = null
                lock.notifyAll()
            }
        }
    }

    /** Kept under the 5 s ANR threshold: initialization runs on the main thread. */
    const val DEFAULT_TIMEOUT_MS = 3_000L
}
