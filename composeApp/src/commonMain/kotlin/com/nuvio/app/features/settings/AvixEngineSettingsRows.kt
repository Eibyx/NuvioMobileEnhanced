package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nuvio.app.features.player.AvixPlaybackEnginePreferences
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_avix_show_end_time_description
import nuvio.composeapp.generated.resources.settings_avix_show_end_time
import nuvio.composeapp.generated.resources.settings_avix_engine_prefer_libmpv_ass
import nuvio.composeapp.generated.resources.settings_avix_engine_prefer_libmpv_ass_description
import nuvio.composeapp.generated.resources.settings_avix_engine_remember_title
import nuvio.composeapp.generated.resources.settings_avix_engine_remember_title_description
import nuvio.composeapp.generated.resources.settings_avix_engine_reset_titles
import nuvio.composeapp.generated.resources.settings_avix_engine_reset_titles_description
import org.jetbrains.compose.resources.stringResource

/** Avix-only playback engine preferences (stored on the device, never synced). */
@Composable
internal fun AvixEngineSettingsRows(
    enabled: Boolean,
    isTablet: Boolean,
) {
    LaunchedEffect(Unit) { AvixPlaybackEnginePreferences.ensureLoaded() }
    val rememberPerTitle by AvixPlaybackEnginePreferences.rememberPerTitle.collectAsState()
    val preferLibmpvForAss by AvixPlaybackEnginePreferences.preferLibmpvForAss.collectAsState()
    val rememberedCount by AvixPlaybackEnginePreferences.rememberedTitleCount.collectAsState()
    LaunchedEffect(Unit) { com.nuvio.app.features.player.PlaybackEndTimePreference.ensureLoaded() }
    val showEndTime by com.nuvio.app.features.player.PlaybackEndTimePreference.enabled.collectAsState()

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_avix_show_end_time),
        description = stringResource(Res.string.settings_avix_show_end_time_description),
        checked = showEndTime,
        isTablet = isTablet,
        onCheckedChange = com.nuvio.app.features.player.PlaybackEndTimePreference::setEnabled,
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_avix_engine_prefer_libmpv_ass),
        description = stringResource(Res.string.settings_avix_engine_prefer_libmpv_ass_description),
        checked = preferLibmpvForAss,
        enabled = enabled,
        isTablet = isTablet,
        onCheckedChange = AvixPlaybackEnginePreferences::setPreferLibmpvForAss,
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_avix_engine_remember_title),
        description = stringResource(Res.string.settings_avix_engine_remember_title_description),
        checked = rememberPerTitle,
        enabled = enabled,
        isTablet = isTablet,
        onCheckedChange = AvixPlaybackEnginePreferences::setRememberPerTitle,
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsNavigationRow(
        title = stringResource(Res.string.settings_avix_engine_reset_titles),
        description = stringResource(Res.string.settings_avix_engine_reset_titles_description, rememberedCount),
        enabled = enabled && rememberedCount > 0,
        isTablet = isTablet,
        onClick = AvixPlaybackEnginePreferences::forgetAllTitles,
    )
}
