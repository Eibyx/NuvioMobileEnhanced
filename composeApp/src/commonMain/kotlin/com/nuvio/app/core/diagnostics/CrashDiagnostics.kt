package com.nuvio.app.core.diagnostics

import com.nuvio.app.core.logging.SecretRedactor
import kotlinx.coroutines.flow.StateFlow

/** Avix (ported from Yesnt10): a crash captured on this device, shown on the next start. */
data class LocalCrashReport(
    val id: String,
    val summary: String,
    val details: String,
)

/**
 * Local crash diagnostics: an uncaught exception is written to private storage (never uploaded)
 * and offered to the user on the next launch so it can be copied into a bug report.
 */
expect object CrashDiagnostics {
    val reportsSupported: Boolean
    val pendingReport: StateFlow<LocalCrashReport?>
    val lastReport: StateFlow<LocalCrashReport?>

    fun initialize(context: Any?)
    fun dismiss(reportId: String)
}

internal const val CRASH_REPORT_MAX_LENGTH = 24_000

/** Removes tokens, keys, JWTs and URL query strings from crash text before it is stored. */
internal fun sanitizeCrashText(text: String): String =
    SecretRedactor.redact(text)
        .replace(Regex("""https?://[^\s)\]"']+""")) { match ->
            val value = match.value
            val base = value.substringBefore('?').substringBefore('#')
            if (base == value) value else "$base?‹redacted›"
        }
