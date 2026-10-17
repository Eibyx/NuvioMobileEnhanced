package com.nuvio.app.core.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One bounded private export, written only at the user's request, never during image loading. */
internal suspend fun artworkReportIntent(context: Context, text: String): Intent = withContext(Dispatchers.IO) {
    require(text.encodeToByteArray().size <= ArtworkDiagnostics.MAX_BYTES + 8192)
    val directory = File(context.cacheDir, "artwork-diagnostics").apply { mkdirs() }
    val file = File(directory, "NuvioEibyx-Artwork-Diagnostic.txt")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    check(!file.exists() || file.delete())
    file.writeText(text, Charsets.UTF_8)
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Image diagnostics", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
internal suspend fun clearArtworkReportFiles(context: Context) = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "artwork-diagnostics")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(directory, "NuvioEibyx-Artwork-Diagnostic.txt"))
    context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    directory.deleteRecursively()
    Unit
}
@Composable
internal actual fun rememberArtworkReportSharing(): ArtworkReportSharing {
    val context = LocalContext.current
    return remember(context) {
        ArtworkReportSharing(
            supported = true,
            share = { text ->
                try {
                    val intent = artworkReportIntent(context, text)
                    context.startActivity(Intent.createChooser(intent, "Diagnóstico de imágenes"))
                    true
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { false }
            },
            clearFiles = { clearArtworkReportFiles(context) },
        )
    }
}

internal actual fun artworkObjectIdentity(value: Any): Int = System.identityHashCode(value)
