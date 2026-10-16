package com.nuvio.app.core.avix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvixSettingsBackupTest {
    @Test
    fun roundTripKeepsOnlyAvixLocalKeys() {
        val prefs = mapOf(
            "ui.theme_mode" to "LIGHT",
            "player.engine.title.tt123" to "Libmpv",
            "subtitles.custom_font.family" to "Open Sans",
            "auth.session" to "must-not-be-exported",
        )
        val text = AvixSettingsBackup.encode(prefs, nowEpochMs = 1L)
        assertFalse(text.contains("must-not-be-exported"))
        assertEquals(prefs - "auth.session", AvixSettingsBackup.decode(text))
    }

    @Test
    fun rejectsForeignOrNewerFiles() {
        assertFailsWith<AvixBackupException> { AvixSettingsBackup.decode("""{"hello":"world"}""") }
        assertFailsWith<AvixBackupException> { AvixSettingsBackup.decode("not json") }
        assertFailsWith<AvixBackupException> {
            AvixSettingsBackup.decode(
                """{"format":"nuvio-avix-local-settings","version":99,"createdAtEpochMs":1,"preferences":{}}""",
            )
        }
    }

    @Test
    fun dropsKeysOutsideTheAllowList() {
        val text = """{"format":"nuvio-avix-local-settings","version":1,"createdAtEpochMs":1,
            "preferences":{"ui.theme_mode":"DARK","sync.blob":"x","player.show_end_time":"false"}}"""
        assertEquals(
            mapOf("ui.theme_mode" to "DARK", "player.show_end_time" to "false"),
            AvixSettingsBackup.decode(text),
        )
        assertTrue(AvixSettingsBackup.isAllowedKey("player.remember_choices"))
        assertFalse(AvixSettingsBackup.isAllowedKey("profile.pin"))
    }
}
