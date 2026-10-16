package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.features.ai.AiAssistantSettingsRepository
import com.nuvio.app.features.ai.AiProvider
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.ai_settings_delete_key
import nuvio.composeapp.generated.resources.ai_settings_enabled
import nuvio.composeapp.generated.resources.ai_settings_enabled_description
import nuvio.composeapp.generated.resources.ai_settings_key_label
import nuvio.composeapp.generated.resources.ai_settings_key_missing
import nuvio.composeapp.generated.resources.ai_settings_key_saved
import nuvio.composeapp.generated.resources.ai_settings_model
import nuvio.composeapp.generated.resources.ai_settings_provider
import nuvio.composeapp.generated.resources.ai_settings_provider_description
import nuvio.composeapp.generated.resources.ai_settings_save_key
import nuvio.composeapp.generated.resources.ai_settings_section
import nuvio.composeapp.generated.resources.ai_settings_tavily_key_label
import nuvio.composeapp.generated.resources.ai_settings_web_search
import nuvio.composeapp.generated.resources.ai_settings_web_search_description
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10 port): AI assistant options. Keys are typed by the user and stored encrypted. */
@Composable
internal fun AvixAiAssistantSettingsSection(isTablet: Boolean) {
    if (!AiAssistantSettingsRepository.isAvailable) return
    LaunchedEffect(Unit) { AiAssistantSettingsRepository.ensureLoaded() }
    val settings by AiAssistantSettingsRepository.settings.collectAsStateWithLifecycle()
    val provider = settings.provider

    SettingsSection(title = stringResource(Res.string.ai_settings_section), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.ai_settings_enabled),
                description = stringResource(Res.string.ai_settings_enabled_description),
                checked = settings.enabled,
                isTablet = isTablet,
                onCheckedChange = AiAssistantSettingsRepository::setEnabled,
            )
            if (settings.enabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsChipRow(
                    title = stringResource(Res.string.ai_settings_provider),
                    description = stringResource(Res.string.ai_settings_provider_description),
                    isTablet = isTablet,
                ) {
                    AiProvider.entries.forEach { option ->
                        FilterChip(
                            selected = option == provider,
                            onClick = { AiAssistantSettingsRepository.setProvider(option) },
                            label = { Text(option.displayName) },
                        )
                    }
                }
                SettingsGroupDivider(isTablet = isTablet)
                SecretKeyEditor(
                    label = stringResource(Res.string.ai_settings_key_label, provider.displayName),
                    hasKey = provider in settings.providersWithKey,
                    isTablet = isTablet,
                    onSave = { AiAssistantSettingsRepository.setApiKey(provider, it) },
                    onDelete = { AiAssistantSettingsRepository.setApiKey(provider, null) },
                    resetKey = provider,
                )
                SettingsGroupDivider(isTablet = isTablet)
                ModelEditor(
                    current = settings.modelFor(provider),
                    isTablet = isTablet,
                    resetKey = provider,
                    onSave = { AiAssistantSettingsRepository.setModel(provider, it) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.ai_settings_web_search),
                    description = stringResource(Res.string.ai_settings_web_search_description),
                    checked = settings.webSearchEnabled,
                    isTablet = isTablet,
                    onCheckedChange = AiAssistantSettingsRepository::setWebSearchEnabled,
                )
                if (settings.webSearchEnabled) {
                    SecretKeyEditor(
                        label = stringResource(Res.string.ai_settings_tavily_key_label),
                        hasKey = settings.hasTavilyKey,
                        isTablet = isTablet,
                        onSave = AiAssistantSettingsRepository::setTavilyKey,
                        onDelete = { AiAssistantSettingsRepository.setTavilyKey(null) },
                        resetKey = "tavily",
                    )
                }
            }
        }
    }
}

@Composable
private fun SecretKeyEditor(
    label: String,
    hasKey: Boolean,
    isTablet: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    resetKey: Any,
) {
    var value by remember(resetKey) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SettingsSecretTextField(value = value, onValueChange = { value = it }, label = label, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(if (hasKey) Res.string.ai_settings_key_saved else Res.string.ai_settings_key_missing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    onSave(value)
                    value = ""
                },
                enabled = value.isNotBlank(),
            ) { Text(stringResource(Res.string.ai_settings_save_key)) }
            if (hasKey) {
                TextButton(onClick = onDelete) { Text(stringResource(Res.string.ai_settings_delete_key)) }
            }
        }
    }
}

@Composable
private fun ModelEditor(current: String, isTablet: Boolean, resetKey: Any, onSave: (String) -> Unit) {
    var value by remember(resetKey, current) { mutableStateOf(current) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = stringResource(Res.string.ai_settings_model), style = MaterialTheme.typography.bodyLarge)
        NuvioInputField(
            value = value,
            onValueChange = { value = it },
            placeholder = current,
            trailingContent = {
                TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank() && value != current) {
                    Text(stringResource(Res.string.ai_settings_save_key))
                }
            },
        )
    }
}
