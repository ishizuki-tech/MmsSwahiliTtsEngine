package com.negi.mmsswahilitts

import java.text.Normalizer

/** Converts text to the exact character vocabulary shipped with the MMS checkpoint. */
internal class SwahiliTextNormalizer(private val supportedCharacters: Set<Char>) {
    fun normalize(text: CharSequence): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        return buildString(normalized.length) {
            normalized.forEach { character ->
                append(if (character in supportedCharacters) character else ' ')
            }
        }.replace(Regex("\\s+"), " ").trim()
    }

    /** Bounds a single VITS request while retaining sentence boundaries when present. */
    fun chunks(text: CharSequence, maxCharacters: Int = 180): List<String> {
        require(maxCharacters > 0)
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()
        if (normalized.length <= maxCharacters) return listOf(normalized)
        val result = mutableListOf<String>()
        var remaining = normalized
        while (remaining.length > maxCharacters) {
            val split = remaining.lastIndexOf(' ', maxCharacters).takeIf { it > 0 } ?: maxCharacters
            result += remaining.substring(0, split).trim()
            remaining = remaining.substring(split).trim()
        }
        if (remaining.isNotBlank()) result += remaining
        return result
    }
}
