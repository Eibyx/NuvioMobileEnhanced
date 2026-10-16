package com.nuvio.app.core.diagnostics

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.crash_report_close
import nuvio.composeapp.generated.resources.crash_report_copied
import nuvio.composeapp.generated.resources.crash_report_copy
import nuvio.composeapp.generated.resources.crash_report_intro
import nuvio.composeapp.generated.resources.crash_report_title
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10): offered once after the app closed unexpectedly. */
@Composable
fun PendingCrashReportDialog() {
    if (!CrashDiagnostics.reportsSupported) return
    val report by CrashDiagnostics.pendingReport.collectAsStateWithLifecycle()
    val current = report ?: return
    CrashReportDialog(report = current, onDismiss = { CrashDiagnostics.dismiss(current.id) })
}

@Composable
fun CrashReportDialog(report: LocalCrashReport, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(report.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.crash_report_title)) },
        text = {
            androidx.compose.foundation.layout.Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(Res.string.crash_report_intro), style = MaterialTheme.typography.bodyMedium)
                Text(
                    report.summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.heightIn(min = 0.dp),
                )
                if (copied) {
                    Text(stringResource(Res.string.crash_report_copied), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(report.details))
                copied = true
            }) { Text(stringResource(Res.string.crash_report_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.crash_report_close)) }
        },
    )
}
