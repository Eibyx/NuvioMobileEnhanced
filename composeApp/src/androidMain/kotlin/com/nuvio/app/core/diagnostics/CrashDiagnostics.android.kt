package com.nuvio.app.core.diagnostics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.util.Log
import com.nuvio.app.core.build.AppVersionConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object CrashDiagnostics {
    actual val reportsSupported: Boolean = true

    private const val PREFS = "avix_local_crash_diagnostics"
    private const val PENDING_ID = "pending_id"
    private const val LAST_ID = "last_id"
    private const val LAST_SUMMARY = "last_summary"
    private const val LAST_DETAILS = "last_details"

    private val _pendingReport = MutableStateFlow<LocalCrashReport?>(null)
    actual val pendingReport: StateFlow<LocalCrashReport?> = _pendingReport.asStateFlow()
    private val _lastReport = MutableStateFlow<LocalCrashReport?>(null)
    actual val lastReport: StateFlow<LocalCrashReport?> = _lastReport.asStateFlow()

    private var preferences: SharedPreferences? = null
    private var installed = false

    actual fun initialize(context: Any?) {
        val appContext = (context as? Context)?.applicationContext ?: return
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        preferences = prefs
        val last = loadLast(prefs)
        _lastReport.value = last
        _pendingReport.value = last?.takeIf { prefs.getString(PENDING_ID, null) == it.id }
        if (installed) return
        installed = true
        // Chain to whatever was installed before (Android's default handler, or Sentry's when enabled).
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { save(appContext, prefs, thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    actual fun dismiss(reportId: String) {
        val prefs = preferences ?: return
        if (prefs.getString(PENDING_ID, null) == reportId) prefs.edit().remove(PENDING_ID).apply()
        _pendingReport.value = null
    }

    private fun loadLast(prefs: SharedPreferences): LocalCrashReport? {
        val id = prefs.getString(LAST_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val details = prefs.getString(LAST_DETAILS, null)?.takeIf { it.isNotBlank() } ?: return null
        val summary = prefs.getString(LAST_SUMMARY, null)?.takeIf { it.isNotBlank() } ?: "Unknown crash"
        return LocalCrashReport(id, summary, details)
    }

    private fun save(context: Context, prefs: SharedPreferences, thread: Thread, throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val id = timestamp.toString()
        val summary = sanitizeCrashText(
            throwable.message?.takeIf { it.isNotBlank() }?.let { "${throwable.javaClass.name}: $it" }
                ?: throwable.javaClass.name,
        ).take(500)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(timestamp))
        val details = sanitizeCrashText(
            buildString {
                appendLine("Nuvio Eibyx local crash report")
                appendLine("Time: $time")
                appendLine("Package: ${context.packageName}")
                appendLine("Version: ${AppVersionConfig.VERSION_NAME} (${AppVersionConfig.VERSION_CODE})")
                appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                append(Log.getStackTraceString(throwable))
            },
        ).take(CRASH_REPORT_MAX_LENGTH)
        prefs.edit()
            .putString(PENDING_ID, id)
            .putString(LAST_ID, id)
            .putString(LAST_SUMMARY, summary)
            .putString(LAST_DETAILS, details)
            .commit() // The process is about to die: write synchronously.
    }
}
