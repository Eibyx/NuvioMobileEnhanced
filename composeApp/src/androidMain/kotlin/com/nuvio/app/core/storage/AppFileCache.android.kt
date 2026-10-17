package com.nuvio.app.core.storage

import android.content.Context
import android.util.AtomicFile
import java.io.File

internal actual object AppFileCache {
    private const val directoryName = "nuvio_file_cache"

    @Volatile
    private var root: File? = null

    fun initialize(context: Context) {
        root = context.cacheDir.resolve(directoryName)
    }

    // AtomicFile reads can recover/discard interrupted writes. Serialize operations so a
    // reader never discards a .new file belonging to a writer that is still active.
    @Synchronized
    actual fun read(path: String): String? {
        val target = root?.resolve(path) ?: return null
        return runCatching {
            AtomicFile(target).openRead().bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    @Synchronized
    actual fun write(path: String, payload: String) {
        val target = root?.resolve(path) ?: return
        target.parentFile?.mkdirs()
        val file = AtomicFile(target)
        val output = file.startWrite()
        try {
            output.write(payload.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }
}
