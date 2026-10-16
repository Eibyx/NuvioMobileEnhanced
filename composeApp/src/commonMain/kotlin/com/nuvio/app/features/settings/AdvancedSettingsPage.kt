package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.library.clearLibraryReleaseScheduleCache
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watched.WatchedClock
import com.nuvio.app.features.watchprogress.ContinueWatchingEnrichmentCache
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.settings_advanced_clear_cw_cache
import nuvio.composeapp.generated.resources.settings_advanced_clear_cw_cache_done
import nuvio.composeapp.generated.resources.settings_advanced_clear_cw_cache_subtitle
import nuvio.composeapp.generated.resources.settings_advanced_clear_metadata_cache
import nuvio.composeapp.generated.resources.settings_advanced_clear_metadata_cache_done
import nuvio.composeapp.generated.resources.settings_advanced_clear_metadata_cache_subtitle
import nuvio.composeapp.generated.resources.settings_advanced_debug_logs
import nuvio.composeapp.generated.resources.settings_advanced_debug_logs_description
import nuvio.composeapp.generated.resources.settings_advanced_remember_last_profile
import nuvio.composeapp.generated.resources.settings_advanced_remember_last_profile_description
import nuvio.composeapp.generated.resources.settings_advanced_section_cache
import nuvio.composeapp.generated.resources.settings_advanced_section_debugging
import nuvio.composeapp.generated.resources.settings_advanced_section_diagnostics
import nuvio.composeapp.generated.resources.settings_advanced_section_startup
import nuvio.composeapp.generated.resources.settings_advanced_sentry_reports
import nuvio.composeapp.generated.resources.settings_advanced_sentry_reports_subtitle
import nuvio.composeapp.generated.resources.sentry_disable_dialog_subtitle
import nuvio.composeapp.generated.resources.sentry_disable_dialog_title
import nuvio.composeapp.generated.resources.sentry_enable_dialog_subtitle
import nuvio.composeapp.generated.resources.sentry_enable_dialog_title
import nuvio.composeapp.generated.resources.sentry_help_body
import nuvio.composeapp.generated.resources.sentry_help_title
import nuvio.composeapp.generated.resources.sentry_keep_enabled
import nuvio.composeapp.generated.resources.sentry_not_sent_body
import nuvio.composeapp.generated.resources.sentry_not_sent_title
import nuvio.composeapp.generated.resources.sentry_sent_body
import nuvio.composeapp.generated.resources.sentry_sent_title
import nuvio.composeapp.generated.resources.sentry_turn_off
import nuvio.composeapp.generated.resources.sentry_turn_on
import nuvio.composeapp.generated.resources.settings_avix_backup_exported
import nuvio.composeapp.generated.resources.settings_avix_backup_export_failed
import nuvio.composeapp.generated.resources.settings_avix_backup_imported
import nuvio.composeapp.generated.resources.settings_avix_backup_import_failed
import nuvio.composeapp.generated.resources.settings_avix_backup_section
import nuvio.composeapp.generated.resources.settings_avix_backup_export
import nuvio.composeapp.generated.resources.settings_avix_backup_description
import nuvio.composeapp.generated.resources.settings_avix_backup_import
import nuvio.composeapp.generated.resources.crash_report_section
import nuvio.composeapp.generated.resources.crash_report_last
import nuvio.composeapp.generated.resources.crash_report_none
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.advancedSettingsContent(
    isTablet: Boolean,
    rememberLastProfileEnabled: Boolean,
    onDebugLogsClick: () -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_advanced_section_startup),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_advanced_remember_last_profile),
                    description = stringResource(Res.string.settings_advanced_remember_last_profile_description),
                    checked = rememberLastProfileEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ProfileRepository::setRememberLastProfileEnabled,
                )
            }
        }
    }
    if (SentrySettingsRepository.isSupported) {
        item {
            val sentryEnabledFlow = remember {
                SentrySettingsRepository.ensureLoaded()
                SentrySettingsRepository.enabled
            }
            val sentryEnabled by sentryEnabledFlow.collectAsStateWithLifecycle()
            var showSentryDialog by rememberSaveable { mutableStateOf(false) }

            SettingsSection(
                title = stringResource(Res.string.settings_advanced_section_diagnostics),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_advanced_sentry_reports),
                        description = stringResource(Res.string.settings_advanced_sentry_reports_subtitle),
                        checked = sentryEnabled,
                        isTablet = isTablet,
                        onCheckedChange = { showSentryDialog = true },
                    )
                }
            }

            if (showSentryDialog) {
                SentrySettingsDialog(
                    enabled = sentryEnabled,
                    onConfirm = {
                        SentrySettingsRepository.setEnabled(!sentryEnabled)
                    },
                    onDismiss = {
                        showSentryDialog = false
                    },
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_advanced_section_cache),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                val scope = rememberCoroutineScope()
                var cleared by rememberSaveable { mutableStateOf(false) }
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_advanced_clear_cw_cache),
                    description = if (cleared) {
                        stringResource(Res.string.settings_advanced_clear_cw_cache_done)
                    } else {
                        stringResource(Res.string.settings_advanced_clear_cw_cache_subtitle)
                    },
                    isTablet = isTablet,
                    onClick = {
                        if (!cleared) {
                            ContinueWatchingEnrichmentCache.clearAll(ProfileRepository.activeProfileId)
                            cleared = true
                            scope.launch {
                                WatchProgressRepository.clearLocalAndForceSnapshotRefreshFromServer(
                                    ProfileRepository.activeProfileId,
                                )
                            }
                        }
                    },
                )
                var metadataCleared by rememberSaveable { mutableStateOf(MetadataCacheClearer.recentlyCleared()) }
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_advanced_clear_metadata_cache),
                    description = if (metadataCleared) {
                        stringResource(Res.string.settings_advanced_clear_metadata_cache_done)
                    } else {
                        stringResource(Res.string.settings_advanced_clear_metadata_cache_subtitle)
                    },
                    isTablet = isTablet,
                    onClick = {
                        if (!metadataCleared && !MetadataCacheClearer.recentlyCleared()) {
                            metadataCleared = true
                            MetadataCacheClearer.clear()
                        }
                    },
                )
            }
        }
    }
    item {
        AvixLocalSettingsBackupSection(isTablet = isTablet)
    }
    item {
        AvixAiAssistantSettingsSection(isTablet = isTablet)
    }
    item {
        AvixLastCrashReportSection(isTablet = isTablet)
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_advanced_section_debugging),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_advanced_debug_logs),
                    description = stringResource(Res.string.settings_advanced_debug_logs_description),
                    isTablet = isTablet,
                    onClick = onDebugLogsClick,
                )
            }
        }
    }
}

private object MetadataCacheClearer {
    private const val COOLDOWN_MS = 60L * 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastClearedAtEpochMs = 0L

    fun recentlyCleared(): Boolean =
        lastClearedAtEpochMs != 0L && WatchedClock.nowEpochMs() - lastClearedAtEpochMs < COOLDOWN_MS

    fun clear() {
        if (recentlyCleared()) return
        lastClearedAtEpochMs = WatchedClock.nowEpochMs()
        MetaDetailsRepository.clearCachedMetadata()
        scope.launch {
            clearProfileInsightTitleFacts()
            clearLibraryReleaseScheduleCache()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SentrySettingsDialog(
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(
                        if (enabled) {
                            Res.string.sentry_disable_dialog_title
                        } else {
                            Res.string.sentry_enable_dialog_title
                        },
                    ),
    ) {
        Text(
            text = stringResource(
                if (enabled) {
                    Res.string.sentry_disable_dialog_subtitle
                } else {
                    Res.string.sentry_enable_dialog_subtitle
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textMuted,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            SentryInfoSection(
                title = stringResource(Res.string.sentry_help_title),
                body = stringResource(Res.string.sentry_help_body),
            )
            SentryInfoSection(
                title = stringResource(Res.string.sentry_sent_title),
                body = stringResource(Res.string.sentry_sent_body),
            )
            SentryInfoSection(
                title = stringResource(Res.string.sentry_not_sent_title),
                body = stringResource(Res.string.sentry_not_sent_body),
            )
        }
        DialogButtons {
            DialogButton(
                text = stringResource(
                        if (enabled) {
                            Res.string.sentry_keep_enabled
                        } else {
                            Res.string.action_cancel
                        },
                    ),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(
                        if (enabled) {
                            Res.string.sentry_turn_off
                        } else {
                            Res.string.sentry_turn_on
                        },
                    ),
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun SentryInfoSection(
    title: String,
    body: String,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
    }
}

/** Avix: export / import of the device-local Avix preferences (never the synced account settings). */
@androidx.compose.runtime.Composable
private fun AvixLocalSettingsBackupSection(isTablet: Boolean) {
    var message by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val exportedOk = stringResource(Res.string.settings_avix_backup_exported)
    val exportFailed = stringResource(Res.string.settings_avix_backup_export_failed)
    val importedOk = stringResource(Res.string.settings_avix_backup_imported)
    val importFailed = stringResource(Res.string.settings_avix_backup_import_failed)
    val files = com.nuvio.app.core.avix.rememberAvixBackupFiles(
        onExported = { ok -> message = if (ok) exportedOk else exportFailed },
        onImported = { result ->
            message = result.mapCatching { text ->
                val prefs = com.nuvio.app.core.avix.AvixSettingsBackup.decode(text)
                com.nuvio.app.core.avix.AvixSettingsBackup.restore(prefs)
                prefs.size
            }.fold(onSuccess = { importedOk.replace("%1\$d", it.toString()) }, onFailure = { importFailed })
        },
    )
    if (!files.isSupported) return
    SettingsSection(
        title = stringResource(Res.string.settings_avix_backup_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_avix_backup_export),
                description = message ?: stringResource(Res.string.settings_avix_backup_description),
                isTablet = isTablet,
                onClick = {
                    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    files.export(
                        "nuvio-avix-ajustes.json",
                        com.nuvio.app.core.avix.AvixSettingsBackup.encode(
                            com.nuvio.app.core.avix.AvixSettingsBackup.snapshot(),
                            now,
                        ),
                    )
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_avix_backup_import),
                description = null,
                isTablet = isTablet,
                onClick = files::import,
            )
        }
    }
}

/** Avix (Yesnt10): view/copy the last local crash report (never uploaded). */
@androidx.compose.runtime.Composable
private fun AvixLastCrashReportSection(isTablet: Boolean) {
    if (!com.nuvio.app.core.diagnostics.CrashDiagnostics.reportsSupported) return
    val last by com.nuvio.app.core.diagnostics.CrashDiagnostics.lastReport.collectAsStateWithLifecycle()
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    SettingsSection(title = stringResource(Res.string.crash_report_section), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.crash_report_last),
                description = last?.summary ?: stringResource(Res.string.crash_report_none),
                enabled = last != null,
                isTablet = isTablet,
                onClick = { open = true },
            )
        }
    }
    val report = last
    if (open && report != null) {
        com.nuvio.app.core.diagnostics.CrashReportDialog(report = report, onDismiss = { open = false })
    }
}
