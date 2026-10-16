package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.home.AvixHomeLayoutPreferences
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_avix_catalog_columns
import nuvio.composeapp.generated.resources.settings_avix_catalog_columns_auto
import nuvio.composeapp.generated.resources.settings_avix_catalog_columns_description
import nuvio.composeapp.generated.resources.settings_avix_hero_auto_scroll
import nuvio.composeapp.generated.resources.settings_avix_hero_auto_scroll_description
import nuvio.composeapp.generated.resources.settings_avix_hide_release_dates
import nuvio.composeapp.generated.resources.settings_avix_hide_release_dates_description
import nuvio.composeapp.generated.resources.settings_avix_home_layout_section
import nuvio.composeapp.generated.resources.settings_avix_shelf_rows
import nuvio.composeapp.generated.resources.settings_avix_shelf_rows_description
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10 port): device-local Home and catalog layout options. */
@Composable
internal fun AvixHomeLayoutSettingsSection(isTablet: Boolean) {
    LaunchedEffect(Unit) { AvixHomeLayoutPreferences.ensureLoaded() }
    val layout by AvixHomeLayoutPreferences.layout.collectAsStateWithLifecycle()
    SettingsSection(
        title = stringResource(Res.string.settings_avix_home_layout_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSliderRow(
                title = stringResource(Res.string.settings_avix_shelf_rows),
                description = stringResource(Res.string.settings_avix_shelf_rows_description),
                value = layout.shelfRows,
                valueText = layout.shelfRows.toString(),
                valueRange = AvixHomeLayoutPreferences.MIN_SHELF_ROWS..AvixHomeLayoutPreferences.MAX_SHELF_ROWS,
                step = 1,
                isTablet = isTablet,
                onValueChange = AvixHomeLayoutPreferences::setShelfRows,
            )
            SettingsGroupDivider(isTablet = isTablet)
            // Slider position 1 = automatic (columns from the screen width), 2–6 = fixed.
            val columnsSlider = if (layout.catalogColumns <= 0) 1 else layout.catalogColumns
            SettingsSliderRow(
                title = stringResource(Res.string.settings_avix_catalog_columns),
                description = stringResource(Res.string.settings_avix_catalog_columns_description),
                value = columnsSlider,
                valueText = if (layout.catalogColumns <= 0) {
                    stringResource(Res.string.settings_avix_catalog_columns_auto)
                } else {
                    layout.catalogColumns.toString()
                },
                valueRange = 1..AvixHomeLayoutPreferences.MAX_CATALOG_COLUMNS,
                step = 1,
                isTablet = isTablet,
                onValueChange = { value ->
                    AvixHomeLayoutPreferences.setCatalogColumns(if (value <= 1) 0 else value)
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_avix_hide_release_dates),
                description = stringResource(Res.string.settings_avix_hide_release_dates_description),
                checked = layout.hideReleaseDates,
                isTablet = isTablet,
                onCheckedChange = AvixHomeLayoutPreferences::setHideReleaseDates,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_avix_hero_auto_scroll),
                description = stringResource(Res.string.settings_avix_hero_auto_scroll_description),
                checked = layout.heroAutoScroll,
                isTablet = isTablet,
                onCheckedChange = AvixHomeLayoutPreferences::setHeroAutoScroll,
            )
        }
    }
}
