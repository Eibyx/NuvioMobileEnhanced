package com.nuvio.app.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

/**
 * Avix: removes credentials from log text. supabase-kt's RestException message embeds the request
 * URL and *all* request headers (apikey, `Authorization: Bearer <access token>`), and the app logs
 * those exceptions in full, so every log line and stack trace goes through [redact].
 */
object SecretRedactor {
    private const val MASK = "‹redacted›"
    private val patterns = listOf(
        // Authorization headers: "Authorization: Bearer xxx", "Authorization=[Bearer xxx]"
        Regex("""(?i)(authorization["']?\s*[:=]\s*\[?\s*["']?(?:bearer|basic)\s+)[^\s,\]"']+"""),
        // apikey header / query parameter
        Regex("""(?i)(apikey["']?\s*[:=]\s*\[?\s*["']?)[^\s,\]"'&]+"""),
        // OAuth / session fields in JSON, query strings or form bodies
        Regex("""(?i)((?:access_token|refresh_token|id_token|provider_token|client_secret|password|code_verifier)["']?\s*[:=]\s*["']?)[^\s,&"'}\]]+"""),
        // Any remaining JWT (header.payload.signature)
        Regex("""eyJ[A-Za-z0-9_-]{6,}\.[A-Za-z0-9_-]{6,}\.[A-Za-z0-9_-]{6,}"""),
        // Supabase API keys
        Regex("""sb_(?:publishable|secret)_[A-Za-z0-9_-]{8,}"""),
    )

    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return text.orEmpty()
        return patterns.fold(text) { acc, regex ->
            regex.replace(acc) { match ->
                if (match.groupValues.size > 1 && match.groupValues[1].isNotEmpty()) {
                    match.groupValues[1] + MASK
                } else {
                    MASK
                }
            }
        }
    }

    /** A copy of [error] whose messages (including causes) are redacted; stack traces are kept. */
    fun sanitize(error: Throwable): Throwable = RedactedThrowable(error, depth = 0)

    private class RedactedThrowable(original: Throwable, depth: Int) : Throwable(
        "${original::class.simpleName ?: "Throwable"}: ${redact(original.message)}",
        original.cause?.takeIf { depth < 8 }?.let { RedactedThrowable(it, depth + 1) },
    )
}

/** Kermit writer that forwards redacted messages and throwables to [delegate]. */
class RedactingLogWriter(private val delegate: LogWriter) : LogWriter() {
    override fun isLoggable(tag: String, severity: Severity): Boolean = delegate.isLoggable(tag, severity)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        delegate.log(
            severity = severity,
            message = SecretRedactor.redact(message),
            tag = tag,
            throwable = throwable?.let(SecretRedactor::sanitize),
        )
    }
}

object NuvioLogging {
    private var installed = false

    /** Call once at process start, before anything logs. */
    fun install() {
        if (installed) return
        installed = true
        Logger.setLogWriters(RedactingLogWriter(platformLogWriter()))
    }
}
