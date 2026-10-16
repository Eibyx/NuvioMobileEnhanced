package com.nuvio.app.core.avix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// iOS is not part of Nuvio Avix v1.
@Composable
actual fun rememberAvixBackupFiles(
    onExported: (Boolean) -> Unit,
    onImported: (Result<String>) -> Unit,
): AvixBackupFiles = remember { AvixBackupFiles(isSupported = false, onExport = { _, _ -> }, onImport = {}) }
