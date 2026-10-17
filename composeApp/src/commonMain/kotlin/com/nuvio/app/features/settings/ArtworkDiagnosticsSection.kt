package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.diagnostics.ArtworkDiagnostics
import com.nuvio.app.core.diagnostics.rememberArtworkReportSharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ArtworkDiagnosticsSection(isTablet: Boolean) {
    val available by ArtworkDiagnostics.availability.collectAsStateWithLifecycle()
    if (!available) return
    val capturing by ArtworkDiagnostics.capturing.collectAsStateWithLifecycle()
    val sharing = rememberArtworkReportSharing()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val copied = stringResource(Res.string.artwork_diagnostics_copied)
    val cleared = stringResource(Res.string.artwork_diagnostics_cleared)
    val failed = stringResource(Res.string.artwork_diagnostics_export_failed)
    SettingsSection(title = stringResource(Res.string.artwork_diagnostics_title), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(if (capturing) Res.string.artwork_diagnostics_stop else Res.string.artwork_diagnostics_start),
                description = stringResource(Res.string.artwork_diagnostics_description),
                checked = capturing,
                isTablet = isTablet,
                onCheckedChange = { if (it) ArtworkDiagnostics.start() else ArtworkDiagnostics.stop() },
            )
            SettingsNavigationRow(
                title = stringResource(Res.string.artwork_diagnostics_clear), description = message,
                enabled = !busy, isTablet = isTablet,
                onClick = {
                    busy = true
                    ArtworkDiagnostics.clear()
                    scope.launch {
                        try { sharing.clearFiles(); message = cleared }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = failed }
                        finally { busy = false }
                    }
                },
            )
            SettingsNavigationRow(
                title = stringResource(Res.string.artwork_diagnostics_copy), description = null,
                enabled = !busy, isTablet = isTablet,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val report = withContext(Dispatchers.Default) { ArtworkDiagnostics.report(summary = true) }
                            clipboard.setText(AnnotatedString(report)); message = copied
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = failed }
                        finally { busy = false }
                    }
                },
            )
            SettingsNavigationRow(
                title = stringResource(Res.string.artwork_diagnostics_export), description = null,
                enabled = !busy && sharing.supported, isTablet = isTablet,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val report = withContext(Dispatchers.Default) { ArtworkDiagnostics.report() }
                            if (!sharing.share(report)) message = failed
                        } finally { busy = false }
                    }
                },
            )
        }
    }
}
