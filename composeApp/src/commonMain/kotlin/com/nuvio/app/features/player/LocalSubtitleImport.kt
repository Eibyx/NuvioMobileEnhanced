package com.nuvio.app.features.player

/**
 * Local subtitle import (Avix): validation, encoding normalisation and metadata for
 * subtitle files picked from the device. Platform code reads the bytes and stores the
 * normalised UTF-8 text in the app's private cache.
 */

internal const val LOCAL_SUBTITLE_SOURCE = "avix-local-file"
internal const val LOCAL_SUBTITLE_MAX_BYTES = 20 * 1024 * 1024

enum class LocalSubtitleFormat(val extension: String) {
    SRT("srt"),
    ASS("ass"),
    SSA("ssa"),
    VTT("vtt"),
    TTML("ttml"),
}

enum class LocalSubtitleEncoding {
    UTF8,
    UTF8_BOM,
    UTF16_LE,
    UTF16_BE,
    WINDOWS_1252,
}

data class NormalizedLocalSubtitle(
    val text: String,
    val format: LocalSubtitleFormat,
    val sourceEncoding: LocalSubtitleEncoding,
    val language: String?,
    val displayName: String,
)

enum class LocalSubtitleError {
    EMPTY,
    TOO_LARGE,
    UNSUPPORTED_FORMAT,
    NO_CUES,
}

class LocalSubtitleImportException(val error: LocalSubtitleError) : Exception(error.name)

internal fun normalizeLocalSubtitle(bytes: ByteArray, fileName: String?): Result<NormalizedLocalSubtitle> = runCatching {
    if (bytes.isEmpty()) throw LocalSubtitleImportException(LocalSubtitleError.EMPTY)
    if (bytes.size > LOCAL_SUBTITLE_MAX_BYTES) throw LocalSubtitleImportException(LocalSubtitleError.TOO_LARGE)

    val (decoded, encoding) = decodeSubtitleBytes(bytes)
    val text = decoded.removePrefix("﻿")
    if (text.isBlank()) throw LocalSubtitleImportException(LocalSubtitleError.EMPTY)

    val format = detectSubtitleFormat(text, fileName)
        ?: throw LocalSubtitleImportException(LocalSubtitleError.UNSUPPORTED_FORMAT)
    if (!hasSubtitleCues(text, format)) throw LocalSubtitleImportException(LocalSubtitleError.NO_CUES)

    NormalizedLocalSubtitle(
        text = text,
        format = format,
        sourceEncoding = encoding,
        language = guessSubtitleLanguageFromFileName(fileName),
        displayName = fileName?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "subtitle.${format.extension}",
    )
}

/** Decodes subtitle bytes: BOM first, then UTF-16 heuristics, strict UTF-8, Windows-1252 fallback. */
internal fun decodeSubtitleBytes(bytes: ByteArray): Pair<String, LocalSubtitleEncoding> {
    fun b(i: Int) = bytes[i].toInt() and 0xFF
    if (bytes.size >= 3 && b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF) {
        return bytes.decodeToString(3, bytes.size) to LocalSubtitleEncoding.UTF8_BOM
    }
    if (bytes.size >= 2 && b(0) == 0xFF && b(1) == 0xFE) {
        return decodeUtf16(bytes, 2, littleEndian = true) to LocalSubtitleEncoding.UTF16_LE
    }
    if (bytes.size >= 2 && b(0) == 0xFE && b(1) == 0xFF) {
        return decodeUtf16(bytes, 2, littleEndian = false) to LocalSubtitleEncoding.UTF16_BE
    }
    looksLikeBomlessUtf16(bytes)?.let { littleEndian ->
        return decodeUtf16(bytes, 0, littleEndian) to
            if (littleEndian) LocalSubtitleEncoding.UTF16_LE else LocalSubtitleEncoding.UTF16_BE
    }
    return try {
        bytes.decodeToString(throwOnInvalidSequence = true) to LocalSubtitleEncoding.UTF8
    } catch (_: CharacterCodingException) {
        decodeWindows1252(bytes) to LocalSubtitleEncoding.WINDOWS_1252
    }
}

/** Returns true for little-endian, false for big-endian, null when the data is not UTF-16. */
private fun looksLikeBomlessUtf16(bytes: ByteArray): Boolean? {
    val sample = minOf(bytes.size, 4096) and 0x7FFFFFFE
    if (sample < 8) return null
    var zeroEven = 0
    var zeroOdd = 0
    for (i in 0 until sample) {
        if (bytes[i].toInt() == 0) {
            if (i % 2 == 0) zeroEven++ else zeroOdd++
        }
    }
    val pairs = sample / 2
    return when {
        zeroOdd > pairs * 0.6 && zeroEven < pairs * 0.1 -> true
        zeroEven > pairs * 0.6 && zeroOdd < pairs * 0.1 -> false
        else -> null
    }
}

private fun decodeUtf16(bytes: ByteArray, offset: Int, littleEndian: Boolean): String {
    val builder = StringBuilder((bytes.size - offset) / 2)
    var i = offset
    while (i + 1 < bytes.size) {
        val lo = bytes[i].toInt() and 0xFF
        val hi = bytes[i + 1].toInt() and 0xFF
        builder.append((if (littleEndian) (hi shl 8) or lo else (lo shl 8) or hi).toChar())
        i += 2
    }
    return builder.toString()
}

private val windows1252High = charArrayOf(
    '€', '�', '‚', 'ƒ', '„', '…', '†', '‡',
    'ˆ', '‰', 'Š', '‹', 'Œ', '�', 'Ž', '�',
    '�', '‘', '’', '“', '”', '•', '–', '—',
    '˜', '™', 'š', '›', 'œ', '�', 'ž', 'Ÿ',
)

/** Windows-1252, which is a superset of ISO-8859-1 for printable characters. */
internal fun decodeWindows1252(bytes: ByteArray): String {
    val chars = CharArray(bytes.size)
    for (i in bytes.indices) {
        val value = bytes[i].toInt() and 0xFF
        chars[i] = if (value in 0x80..0x9F) windows1252High[value - 0x80] else value.toChar()
    }
    return chars.concatToString()
}

private val srtTimingRegex = Regex("""\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}""")
private val vttTimingRegex = Regex("""(\d{1,2}:)?\d{2}:\d{2}\.\d{3}\s*-->\s*(\d{1,2}:)?\d{2}:\d{2}\.\d{3}""")

internal fun detectSubtitleFormat(text: String, fileName: String?): LocalSubtitleFormat? {
    val head = text.take(8_000).trimStart()
    when {
        head.startsWith("WEBVTT", ignoreCase = true) -> return LocalSubtitleFormat.VTT
        head.contains("[Script Info]", ignoreCase = true) ||
            head.contains("[V4+ Styles]", ignoreCase = true) ||
            head.contains("[V4 Styles]", ignoreCase = true) -> {
            val isLegacySsa = head.contains("[V4 Styles]", ignoreCase = true) ||
                Regex("""(?im)^\s*ScriptType\s*:\s*v4\.00\s*$""").containsMatchIn(head)
            return if (isLegacySsa) LocalSubtitleFormat.SSA else LocalSubtitleFormat.ASS
        }
        (head.startsWith("<?xml") || head.startsWith("<tt")) && Regex("""<tt[\s>]""").containsMatchIn(head) ->
            return LocalSubtitleFormat.TTML
        srtTimingRegex.containsMatchIn(head) -> return LocalSubtitleFormat.SRT
    }
    return when (fileName?.substringAfterLast('.', "")?.lowercase()) {
        "srt" -> LocalSubtitleFormat.SRT.takeIf { srtTimingRegex.containsMatchIn(text) }
        "vtt", "webvtt" -> LocalSubtitleFormat.VTT.takeIf { vttTimingRegex.containsMatchIn(text) }
        else -> null
    }
}

internal fun hasSubtitleCues(text: String, format: LocalSubtitleFormat): Boolean = when (format) {
    LocalSubtitleFormat.ASS, LocalSubtitleFormat.SSA ->
        Regex("""(?im)^\s*Dialogue\s*:""").containsMatchIn(text)
    LocalSubtitleFormat.SRT -> srtTimingRegex.containsMatchIn(text)
    LocalSubtitleFormat.VTT -> vttTimingRegex.containsMatchIn(text)
    LocalSubtitleFormat.TTML -> Regex("""<p[\s>]""").containsMatchIn(text)
}

private val languageNameAliases = mapOf(
    "spanish" to "es", "espanol" to "es", "español" to "es", "castellano" to "es", "latino" to "es",
    "english" to "en", "ingles" to "en", "inglés" to "en",
    "portuguese" to "pt", "portugues" to "pt", "português" to "pt", "brazilian" to "pt",
    "french" to "fr", "german" to "de", "italian" to "it", "japanese" to "ja", "korean" to "ko",
    "chinese" to "zh", "russian" to "ru", "arabic" to "ar",
)

private val subtitleLanguageCodes = setOf(
    "es", "en", "pt", "fr", "de", "it", "ja", "ko", "zh", "ru", "ar", "pl", "tr", "nl", "sv", "no", "nb", "da",
    "fi", "cs", "hu", "ro", "el", "he", "hi", "id", "ms", "th", "vi", "uk", "bg", "hr", "sr", "sk", "sl", "ca",
    "eu", "gl", "fa", "spa", "eng", "por", "fre", "fra", "ger", "deu", "ita", "jpn", "kor", "chi", "zho", "rus",
    "ara", "pol", "tur", "dut", "nld", "swe", "nor", "dan", "fin", "cze", "ces", "hun", "rum", "ron", "gre",
    "ell", "heb", "hin", "ind", "may", "msa", "tha", "vie", "ukr", "bul", "hrv", "srp", "slk", "slv", "cat",
)

/** Guesses a language code from names like `Anime.Episode.01.es.srt` or `Show_[Spanish].ass`. */
internal fun guessSubtitleLanguageFromFileName(fileName: String?): String? {
    val base = fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.lowercase() ?: return null
    val tokens = base.split('.', '_', '-', ' ', '[', ']', '(', ')').filter { it.isNotBlank() }
    for (token in tokens.asReversed().take(4)) {
        languageNameAliases[token]?.let { return it }
        if (token == "lat" || token == "latam") return "es-419"
        if (token in subtitleLanguageCodes) return normalizeLanguageCode(token) ?: token
    }
    return null
}

internal fun AddonSubtitle.isLocalImport(): Boolean = addonName == LOCAL_SUBTITLE_SOURCE
