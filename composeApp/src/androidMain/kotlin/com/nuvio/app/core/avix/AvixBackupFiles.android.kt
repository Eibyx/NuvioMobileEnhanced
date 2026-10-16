package com.nuvio.app.core.avix

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

@Composable
actual fun rememberAvixBackupFiles(
    onExported: (Boolean) -> Unit,
    onImported: (Result<String>) -> Unit,
): AvixBackupFiles {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportedCallback = rememberUpdatedState(onExported)
    val importedCallback = rememberUpdatedState(onImported)
    val pendingContent = remember { arrayOfNulls<String>(1) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingContent[0]
        pendingContent[0] = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.encodeToByteArray()) }
                        ?: error("cannot open output")
                }.isSuccess
            }
            exportedCallback.value(ok)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // InputStream.readNBytes needs API 33; minSdk is 24.
                        val out = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(chunk)
                            if (read < 0) break
                            out.write(chunk, 0, read)
                            if (out.size() > MAX_BACKUP_BYTES) throw AvixBackupException("backup too large")
                        }
                        out.toByteArray().decodeToString()
                    } ?: error("cannot open input")
                }
            }
            importedCallback.value(result)
        }
    }
    return remember(exportLauncher, importLauncher) {
        AvixBackupFiles(
            isSupported = true,
            onExport = { fileName, content ->
                pendingContent[0] = content
                exportLauncher.launch(fileName)
            },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )
    }
}
