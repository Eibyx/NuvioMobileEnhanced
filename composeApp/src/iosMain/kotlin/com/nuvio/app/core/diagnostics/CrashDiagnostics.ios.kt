package com.nuvio.app.core.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// iOS is not part of Nuvio Avix v1.
actual object CrashDiagnostics {
    actual val reportsSupported: Boolean = false
    actual val pendingReport: StateFlow<LocalCrashReport?> = MutableStateFlow(null)
    actual val lastReport: StateFlow<LocalCrashReport?> = MutableStateFlow(null)
    actual fun initialize(context: Any?) = Unit
    actual fun dismiss(reportId: String) = Unit
}
