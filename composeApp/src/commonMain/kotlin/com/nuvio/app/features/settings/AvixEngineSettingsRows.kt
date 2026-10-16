package com.nuvio.app.features.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nuvio.app.features.player.AvixPlaybackEnginePreferences
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_avix_subtitle_font_error_invalid
import nuvio.composeapp.generated.resources.settings_avix_subtitle_font_error_too_large
import nuvio.composeapp.generated.resources.settings_avix_subtitle_font_reset
import nuvio.composeapp.generated.resources.settings_avix_subtitle_font_default
import nuvio.composeapp.generated.resources.settings_avix_subtitle_font_current
import nuvio.composeapp.generated.resources.settings_avix_subtitle_font
import nuvio.composeapp.generated.resources.settings_avix_remember_choices_description
import nuvio.composeapp.generated.resources.settings_avix_remember_choices
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
    LaunchedEffect(Unit) { com.nuvio.app.features.player.RememberPlayerChoicesPreference.ensureLoaded() }
    val rememberChoices by com.nuvio.app.features.player.RememberPlayerChoicesPreference.enabled.collectAsState()

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
        title = stringResource(Res.string.settings_avix_remember_choices),
        description = stringResource(Res.string.settings_avix_remember_choices_description),
        checked = rememberChoices,
        isTablet = isTablet,
        onCheckedChange = com.nuvio.app.features.player.RememberPlayerChoicesPreference::setEnabled,
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
    AvixSubtitleFontRows(isTablet = isTablet)
}

/** Avix (Yesnt10): custom font for plain-text subtitles, stored only on this device. */
@androidx.compose.runtime.Composable
private fun AvixSubtitleFontRows(isTablet: Boolean) {
    LaunchedEffect(Unit) { com.nuvio.app.features.player.CustomSubtitleFontStore.ensureLoaded() }
    val font by com.nuvio.app.features.player.CustomSubtitleFontStore.selected.collectAsState()
    var importError by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<com.nuvio.app.features.player.SubtitleFontImportError?>(null)
    }
    var failed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val picker = com.nuvio.app.features.player.rememberSubtitleFontPicker(
        onImported = {
            failed = false
            importError = null
            com.nuvio.app.features.player.CustomSubtitleFontStore.select(it)
        },
        onError = {
            failed = true
            importError = it
        },
    )
    if (!picker.isSupported) return
    val status = when {
        failed && importError == com.nuvio.app.features.player.SubtitleFontImportError.TOO_LARGE ->
            stringResource(Res.string.settings_avix_subtitle_font_error_too_large)
        failed -> stringResource(Res.string.settings_avix_subtitle_font_error_invalid)
        font != null -> stringResource(Res.string.settings_avix_subtitle_font_current, font!!.familyName)
        else -> stringResource(Res.string.settings_avix_subtitle_font_default)
    }
    SettingsGroupDivider(isTablet = isTablet)
    SettingsNavigationRow(
        title = stringResource(Res.string.settings_avix_subtitle_font),
        description = status,
        isTablet = isTablet,
        onClick = picker::launch,
    )
    if (font != null) {
        SettingsGroupDivider(isTablet = isTablet)
        SettingsNavigationRow(
            title = stringResource(Res.string.settings_avix_subtitle_font_reset),
            description = null,
            isTablet = isTablet,
            onClick = { com.nuvio.app.features.player.CustomSubtitleFontStore.select(null) },
        )
    }
}
