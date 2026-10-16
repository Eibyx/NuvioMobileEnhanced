package com.nuvio.app.features.player

import android.content.Context
import android.graphics.Typeface
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

private class SubtitleFontImportException(val error: SubtitleFontImportError) : Exception(error.name)

@Composable
actual fun rememberSubtitleFontPicker(
    onImported: (CustomSubtitleFont) -> Unit,
    onError: (SubtitleFontImportError?) -> Unit,
): SubtitleFontPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnImported = rememberUpdatedState(onImported)
    val currentOnError = rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { importSubtitleFont(context, uri) }
            result
                .onSuccess { currentOnImported.value(it) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    InAppLogger.warn("Subtitles/Font", "font import failed: ${error.message}")
                    currentOnError.value((error as? SubtitleFontImportException)?.error)
                }
        }
    }
    // Font MIME types are reported inconsistently (font/ttf, application/x-font-ttf, octet-stream…).
    return remember(launcher) { SubtitleFontPicker(isSupported = true) { launcher.launch(arrayOf("*/*")) } }
}

private fun importSubtitleFont(context: Context, uri: Uri): Result<CustomSubtitleFont> = runCatching {
    val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }
    if (size != null && size > SUBTITLE_FONT_MAX_BYTES) throw SubtitleFontImportException(SubtitleFontImportError.TOO_LARGE)
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            out.write(chunk, 0, read)
            if (out.size() > SUBTITLE_FONT_MAX_BYTES) throw SubtitleFontImportException(SubtitleFontImportError.TOO_LARGE)
        }
        out.toByteArray()
    } ?: throw SubtitleFontImportException(SubtitleFontImportError.NOT_A_FONT)
    if (!isSupportedFontFile(bytes)) throw SubtitleFontImportException(SubtitleFontImportError.NOT_A_FONT)
    val family = parseFontFamilyName(bytes) ?: throw SubtitleFontImportException(SubtitleFontImportError.NOT_A_FONT)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
    val extension = when (bytes.copyOfRange(0, 4).decodeToString()) {
        "OTTO" -> "otf"
        "ttcf" -> "ttc"
        else -> "ttf"
    }
    val file = File(subtitleFontsDirectory(context), "user-$digest.$extension")
    if (!file.exists()) file.writeBytes(bytes)
    // Make sure Android can actually load it before accepting it.
    runCatching { Typeface.createFromFile(file) }.getOrNull()
        ?: run { file.delete(); throw SubtitleFontImportException(SubtitleFontImportError.NOT_A_FONT) }
    CustomSubtitleFont(fileName = file.name, familyName = family)
}

/** Same directory libmpv uses as `sub-fonts-dir`. */
internal fun subtitleFontsDirectory(context: Context): File = LibmpvSubtitleSupport.fontsDirectory(context)

private val typefaceCache = mutableMapOf<String, Typeface?>()

/** The selected custom subtitle typeface, or null for the default font. */
internal fun customSubtitleTypeface(context: Context, bold: Boolean): Typeface? {
    val font = CustomSubtitleFontStore.current ?: return null
    val base = synchronized(typefaceCache) {
        typefaceCache.getOrPut(font.fileName) {
            val file = File(subtitleFontsDirectory(context), font.fileName)
            if (file.exists()) runCatching { Typeface.createFromFile(file) }.getOrNull() else null
        }
    } ?: return null
    return if (bold) Typeface.create(base, Typeface.BOLD) else base
}
