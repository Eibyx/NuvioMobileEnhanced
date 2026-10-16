package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.AvixAppearancePreferences
import com.nuvio.app.core.ui.AvixSystemUiController
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10 port): animated accent gradient and hidden status bar, device-local. */
@Composable
internal fun AvixAppearanceExtraRows(isTablet: Boolean) {
    LaunchedEffect(Unit) { AvixAppearancePreferences.ensureLoaded() }
    val animatedAccent by AvixAppearancePreferences.animatedAccent.collectAsStateWithLifecycle()
    val statusBarHidden by AvixAppearancePreferences.statusBarHidden.collectAsStateWithLifecycle()
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_avix_animated_accent),
        description = stringResource(Res.string.settings_avix_animated_accent_description),
        checked = animatedAccent,
        isTablet = isTablet,
        onCheckedChange = AvixAppearancePreferences::setAnimatedAccent,
    )
    if (AvixSystemUiController.isSupported) {
        SettingsGroupDivider(isTablet = isTablet)
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_avix_hide_status_bar),
            description = stringResource(Res.string.settings_avix_hide_status_bar_description),
            checked = statusBarHidden,
            isTablet = isTablet,
            onCheckedChange = AvixAppearancePreferences::setStatusBarHidden,
        )
    }
}
