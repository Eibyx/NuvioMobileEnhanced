package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.AvixAppearancePreferences
import com.nuvio.app.core.ui.ThemeMode
import com.nuvio.app.core.ui.ThemeModeRepository
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_appearance_theme_mode
import nuvio.composeapp.generated.resources.settings_appearance_theme_mode_dark
import nuvio.composeapp.generated.resources.settings_appearance_theme_mode_description
import nuvio.composeapp.generated.resources.settings_appearance_theme_mode_light
import nuvio.composeapp.generated.resources.settings_appearance_theme_mode_system
import nuvio.composeapp.generated.resources.settings_avix_gradient_theme
import nuvio.composeapp.generated.resources.settings_avix_gradient_theme_description
import nuvio.composeapp.generated.resources.settings_avix_gradient_theme_none
import nuvio.composeapp.generated.resources.settings_avix_typography_description
import nuvio.composeapp.generated.resources.settings_avix_typography_original
import nuvio.composeapp.generated.resources.settings_avix_typography_system
import org.jetbrains.compose.resources.stringResource

/** Avix: Light / Dark / System selector. Applied immediately, stored only on this device. */
@Composable
internal fun AvixThemeModeRow(isTablet: Boolean) {
    val mode by remember {
        ThemeModeRepository.ensureLoaded()
        ThemeModeRepository.mode
    }.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 14.dp)) {
        Text(
            text = stringResource(Res.string.settings_appearance_theme_mode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.settings_appearance_theme_mode_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                ThemeMode.SYSTEM to Res.string.settings_appearance_theme_mode_system,
                ThemeMode.DARK to Res.string.settings_appearance_theme_mode_dark,
                ThemeMode.LIGHT to Res.string.settings_appearance_theme_mode_light,
            ).forEach { (option, label) ->
                val selected = option == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) tokens.colors.accent else Color.Transparent)
                        .border(1.dp, if (selected) tokens.colors.accent else tokens.colors.borderDefault, RoundedCornerShape(12.dp))
                        .semantics {
                            role = Role.RadioButton
                            this.selected = selected
                        }
                        .clickable { ThemeModeRepository.setMode(option) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) tokens.colors.onAccent else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Avix: optional device-local gradient palette (Yesnt10) overriding the synced theme's colours. */
@Composable
internal fun AvixGradientThemeRow(isTablet: Boolean) {
    val selected by remember {
        com.nuvio.app.core.ui.AvixGradientThemeRepository.ensureLoaded()
        com.nuvio.app.core.ui.AvixGradientThemeRepository.selected
    }.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 14.dp)) {
        Text(
            text = stringResource(Res.string.settings_avix_gradient_theme),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.settings_avix_gradient_theme_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val options = listOf<com.nuvio.app.core.ui.AvixGradientTheme?>(null) + com.nuvio.app.core.ui.AvixGradientTheme.entries
            items(options.size) { index ->
                val option = options[index]
                val isSelected = option == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .semantics {
                            role = Role.RadioButton
                            this.selected = isSelected
                        }
                        .clickable { com.nuvio.app.core.ui.AvixGradientThemeRepository.select(option) }
                        .padding(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                option?.let { androidx.compose.ui.graphics.Brush.linearGradient(it.palette.accentGradient) }
                                    ?: androidx.compose.ui.graphics.SolidColor(tokens.colors.surfaceCard),
                            )
                            .border(
                                if (isSelected) 3.dp else 1.dp,
                                if (isSelected) tokens.colors.accent else tokens.colors.borderDefault,
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                    Text(
                        text = option?.label ?: stringResource(Res.string.settings_avix_gradient_theme_none),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) tokens.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Avix: Settings → Appearance → Typography. Applied immediately, stored only on this device. */
@Composable
internal fun AvixTypographyRow(isTablet: Boolean) {
    val useSystemFont by remember {
        AvixAppearancePreferences.ensureLoaded()
        AvixAppearancePreferences.systemFont
    }.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 14.dp)) {
        Text(
            text = stringResource(Res.string.settings_avix_typography_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                false to Res.string.settings_avix_typography_original,
                true to Res.string.settings_avix_typography_system,
            ).forEach { (option, label) ->
                val selected = option == useSystemFont
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) tokens.colors.accent else Color.Transparent)
                        .border(1.dp, if (selected) tokens.colors.accent else tokens.colors.borderDefault, RoundedCornerShape(12.dp))
                        .semantics {
                            role = Role.RadioButton
                            this.selected = selected
                        }
                        .clickable { AvixAppearancePreferences.setSystemFont(option) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) tokens.colors.onAccent else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
