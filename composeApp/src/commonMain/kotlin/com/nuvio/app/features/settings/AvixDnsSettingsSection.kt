package com.nuvio.app.features.settings

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.network.DnsOverHttpsProvider
import com.nuvio.app.core.network.DnsOverHttpsSettingsRepository
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10 port): DNS over HTTPS for addons and playback (Android). */
@Composable
internal fun AvixDnsSettingsSection(isTablet: Boolean) {
    if (!avixDnsOverHttpsSupported) return
    LaunchedEffect(Unit) { DnsOverHttpsSettingsRepository.ensureLoaded() }
    val provider by DnsOverHttpsSettingsRepository.provider.collectAsStateWithLifecycle()
    SettingsSection(title = stringResource(Res.string.settings_avix_doh_section), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsChipRow(
                title = stringResource(Res.string.settings_avix_doh),
                description = stringResource(Res.string.settings_avix_doh_description),
                isTablet = isTablet,
            ) {
                DnsOverHttpsProvider.entries.forEach { option ->
                    FilterChip(
                        selected = option == provider,
                        onClick = { DnsOverHttpsSettingsRepository.setProvider(option) },
                        label = {
                            Text(
                                if (option == DnsOverHttpsProvider.None) {
                                    stringResource(Res.string.settings_avix_doh_system)
                                } else {
                                    option.label
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

/** True where the resolver is wired into the HTTP clients (Android). */
internal expect val avixDnsOverHttpsSupported: Boolean
