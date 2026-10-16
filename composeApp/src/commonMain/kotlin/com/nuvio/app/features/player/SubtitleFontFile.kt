package com.nuvio.app.features.player

import com.nuvio.app.core.avix.AvixLocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avix (Yesnt10 SubtitleFontFileBridge): a user-imported font for plain-text subtitles.
 * The file lives in the app's private fonts directory (also libmpv's `sub-fonts-dir`); the choice is
 * device-local and never written to the synced subtitle style (docs/integration/DECISIONS.md D-5).
 */
data class CustomSubtitleFont(
    val fileName: String,
    val familyName: String,
)

object CustomSubtitleFontStore {
    private const val FILE_KEY = "subtitles.custom_font.file"
    private const val FAMILY_KEY = "subtitles.custom_font.family"
    private val _selected = MutableStateFlow<CustomSubtitleFont?>(null)
    val selected: StateFlow<CustomSubtitleFont?> = _selected.asStateFlow()
    private var loaded = false

    init {
        com.nuvio.app.core.avix.AvixPreferencesReload.register {
            loaded = false
            ensureLoaded()
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val file = AvixLocalStore.getString(FILE_KEY)
        val family = AvixLocalStore.getString(FAMILY_KEY)
        _selected.value = if (!file.isNullOrBlank() && !family.isNullOrBlank()) CustomSubtitleFont(file, family) else null
    }

    val current: CustomSubtitleFont?
        get() {
            ensureLoaded()
            return _selected.value
        }

    fun select(font: CustomSubtitleFont?) {
        ensureLoaded()
        _selected.value = font
        AvixLocalStore.putString(FILE_KEY, font?.fileName)
        AvixLocalStore.putString(FAMILY_KEY, font?.familyName)
    }
}

const val SUBTITLE_FONT_MAX_BYTES = 30 * 1024 * 1024

enum class SubtitleFontImportError { TOO_LARGE, NOT_A_FONT }

/** True for TrueType/OpenType/collection files ("\0\1\0\0", "true", "OTTO", "ttcf"). */
internal fun isSupportedFontFile(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    val tag = bytes.copyOfRange(0, 4)
    return tag.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
        tag.decodeToString() in setOf("true", "OTTO", "ttcf")
}

/**
 * Reads the font family from the OpenType `name` table (typographic family, ID 16, else family,
 * ID 1; Windows UTF-16BE preferred, Mac Roman as fallback). For collections the first face is used.
 */
internal fun parseFontFamilyName(bytes: ByteArray): String? = runCatching {
    fun u16(at: Int) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
    fun u32(at: Int) = (u16(at).toLong() shl 16) or u16(at + 2).toLong()
    var offset = 0
    if (bytes.copyOfRange(0, 4).decodeToString() == "ttcf") offset = u32(12).toInt()
    val numTables = u16(offset + 4)
    var nameTable = -1
    for (i in 0 until numTables) {
        val record = offset + 12 + i * 16
        if (bytes.copyOfRange(record, record + 4).decodeToString() == "name") {
            nameTable = u32(record + 8).toInt()
            break
        }
    }
    if (nameTable < 0) return@runCatching null
    val count = u16(nameTable + 2)
    val stringOffset = nameTable + u16(nameTable + 4)
    val candidates = mutableMapOf<Pair<Int, Int>, String>() // (nameId, platform) -> value
    for (i in 0 until count) {
        val record = nameTable + 6 + i * 12
        val platform = u16(record)
        val nameId = u16(record + 6)
        if (nameId != 1 && nameId != 16) continue
        val length = u16(record + 8)
        val start = stringOffset + u16(record + 10)
        val raw = bytes.copyOfRange(start, start + length)
        val text = when (platform) {
            0, 3 -> buildString { for (j in 0 until raw.size - 1 step 2) append((((raw[j].toInt() and 0xFF) shl 8) or (raw[j + 1].toInt() and 0xFF)).toChar()) }
            1 -> raw.map { (it.toInt() and 0xFF).toChar() }.joinToString("")
            else -> continue
        }.trim()
        if (text.isNotEmpty()) candidates.putIfAbsent(nameId to platform, text)
    }
    listOf(16 to 3, 16 to 0, 1 to 3, 1 to 0, 16 to 1, 1 to 1).firstNotNullOfOrNull { candidates[it] }
}.getOrNull()

class SubtitleFontPicker(val isSupported: Boolean, private val onLaunch: () -> Unit) {
    fun launch() = onLaunch()
}

/** Opens the system picker, validates the font and copies it into the private fonts directory. */
@androidx.compose.runtime.Composable
expect fun rememberSubtitleFontPicker(
    onImported: (CustomSubtitleFont) -> Unit,
    onError: (SubtitleFontImportError?) -> Unit,
): SubtitleFontPicker
