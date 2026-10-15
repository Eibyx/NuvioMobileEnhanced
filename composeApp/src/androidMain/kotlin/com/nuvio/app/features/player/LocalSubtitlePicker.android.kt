package com.nuvio.app.features.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.nuvio.app.core.logging.InAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val LOCAL_SUBTITLE_CACHE_DIR = "local-subtitles"
private const val LOCAL_SUBTITLE_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

@Composable
internal actual fun rememberLocalSubtitlePicker(
    onImported: (AddonSubtitle) -> Unit,
    onError: (LocalSubtitleError?) -> Unit,
): LocalSubtitlePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnImported = rememberUpdatedState(onImported)
    val currentOnError = rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { importLocalSubtitle(context, uri) }
            result
                .onSuccess { currentOnImported.value(it) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    InAppLogger.warn("Subtitles/LocalImport", "import failed: ${error.message}")
                    currentOnError.value((error as? LocalSubtitleImportException)?.error)
                }
        }
    }
    return remember(launcher) {
        // Many file managers report .srt/.ass as application/octet-stream, so accept any file
        // and validate the content instead of trusting the MIME type.
        LocalSubtitlePicker(isSupported = true) { launcher.launch(arrayOf("*/*")) }
    }
}

private fun importLocalSubtitle(context: Context, uri: Uri): Result<AddonSubtitle> = runCatching {
    val (displayName, declaredSize) = queryOpenable(context, uri)
    if (declaredSize != null && declaredSize > LOCAL_SUBTITLE_MAX_BYTES) {
        throw LocalSubtitleImportException(LocalSubtitleError.TOO_LARGE)
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > LOCAL_SUBTITLE_MAX_BYTES) throw LocalSubtitleImportException(LocalSubtitleError.TOO_LARGE)
            buffer.write(chunk, 0, read)
        }
        buffer.toByteArray()
    } ?: throw LocalSubtitleImportException(LocalSubtitleError.EMPTY)

    val normalized = normalizeLocalSubtitle(bytes, displayName).getOrThrow()
    val dir = File(context.cacheDir, LOCAL_SUBTITLE_CACHE_DIR).apply { mkdirs() }
    pruneOldLocalSubtitles(dir)
    val digest = sha256Hex(normalized.text.encodeToByteArray()).take(16)
    val file = File(dir, "$digest.${normalized.format.extension}")
    file.writeText(normalized.text, Charsets.UTF_8)
    InAppLogger.info(
        "Subtitles/LocalImport",
        "imported format=${normalized.format} encoding=${normalized.sourceEncoding} " +
            "language=${normalized.language ?: "unknown"} bytes=${bytes.size}",
    )
    AddonSubtitle(
        id = "local:$digest",
        url = Uri.fromFile(file).toString(),
        language = normalized.language.orEmpty(),
        display = normalized.displayName,
        addonName = LOCAL_SUBTITLE_SOURCE,
    )
}

private fun queryOpenable(context: Context, uri: Uri): Pair<String?, Long?> =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
            name to size
        } ?: (uri.lastPathSegment to null)

private fun pruneOldLocalSubtitles(dir: File) {
    val cutoff = System.currentTimeMillis() - LOCAL_SUBTITLE_CACHE_MAX_AGE_MS
    dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
