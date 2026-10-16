package com.nuvio.app.core.avix

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Avix (Yesnt10 settings backup, narrowed): export/import of the **device-local** Avix
 * preferences only. Synced Nuvio profile settings are deliberately excluded: restoring them would
 * push values to the user's Nuvio account and to the original app (docs/integration/DECISIONS.md D-5).
 * Font files and imported subtitles are not included (only the font choice, which is ignored on
 * restore when the file is missing).
 */
@Serializable
data class AvixSettingsBackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val createdAtEpochMs: Long,
    val preferences: Map<String, String>,
) {
    companion object {
        const val FORMAT = "nuvio-avix-local-settings"
        const val VERSION = 1
    }
}

class AvixBackupException(message: String) : Exception(message)

object AvixSettingsBackup {
    /** Key prefixes that belong to Avix device-local preferences. */
    internal val allowedPrefixes = listOf("player.", "ui.", "subtitles.", "favorites.")
    private const val MAX_ENTRIES = 5_000
    private const val MAX_VALUE_LENGTH = 4_096

    /** Avix: favorites are stored as one JSON list per profile, so they need a larger limit. */
    private const val MAX_LIST_VALUE_LENGTH = 512 * 1_024

    private fun maxValueLength(key: String): Int =
        if (key.startsWith("favorites.")) MAX_LIST_VALUE_LENGTH else MAX_VALUE_LENGTH
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    internal fun isAllowedKey(key: String): Boolean =
        key.length <= 200 && allowedPrefixes.any { key.startsWith(it) }

    fun encode(preferences: Map<String, String>, nowEpochMs: Long): String =
        json.encodeToString(
            AvixSettingsBackupFile.serializer(),
            AvixSettingsBackupFile(
                createdAtEpochMs = nowEpochMs,
                preferences = preferences.filterKeys(::isAllowedKey).toSortedMap(),
            ),
        )

    /** Parses and validates a backup; unknown or foreign keys are dropped. */
    fun decode(text: String): Map<String, String> {
        val file = runCatching { json.decodeFromString(AvixSettingsBackupFile.serializer(), text) }
            .getOrElse { throw AvixBackupException("not a Nuvio Avix settings backup") }
        if (file.format != AvixSettingsBackupFile.FORMAT) throw AvixBackupException("not a Nuvio Avix settings backup")
        if (file.version > AvixSettingsBackupFile.VERSION) throw AvixBackupException("backup from a newer version")
        if (file.preferences.size > MAX_ENTRIES) throw AvixBackupException("backup too large")
        return file.preferences.filter { (key, value) -> isAllowedKey(key) && value.length <= maxValueLength(key) }
    }

    /** Current device-local preferences that a backup would contain. */
    fun snapshot(): Map<String, String> =
        allowedPrefixes.flatMap { AvixLocalStore.keysWithPrefix(it) }
            .mapNotNull { key -> AvixLocalStore.getString(key)?.let { key to it } }
            .toMap()

    /** Replaces the device-local Avix preferences with [preferences]. */
    fun restore(preferences: Map<String, String>) {
        allowedPrefixes.flatMap { AvixLocalStore.keysWithPrefix(it) }
            .filter { it !in preferences }
            .forEach { AvixLocalStore.putString(it, null) }
        preferences.forEach { (key, value) -> AvixLocalStore.putString(key, value) }
        AvixPreferencesReload.reloadAll()
    }
}

/** Repositories that cache Avix preferences register here so a restore takes effect immediately. */
object AvixPreferencesReload {
    private val listeners = mutableListOf<() -> Unit>()

    fun register(reload: () -> Unit) {
        listeners += reload
    }

    fun reloadAll() = listeners.toList().forEach { it() }
}

class AvixBackupFiles(
    val isSupported: Boolean,
    private val onExport: (fileName: String, content: String) -> Unit,
    private val onImport: () -> Unit,
) {
    fun export(fileName: String, content: String) = onExport(fileName, content)
    fun import() = onImport()
}

/** [onResult] receives true/false for export, and the file text (or an error) for import. */
@androidx.compose.runtime.Composable
expect fun rememberAvixBackupFiles(
    onExported: (Boolean) -> Unit,
    onImported: (Result<String>) -> Unit,
): AvixBackupFiles
