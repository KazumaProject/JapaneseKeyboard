package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

/** Software input only. Physical input keeps using RomajiKanaConverter unchanged. */
class CustomRomajiScreenConverter(
    private val rules: Map<String, Pair<String, Int>>,
    private val autoSokuon: Boolean = true,
    private val autoN: Boolean = true,
) {
    private val maxKeyLength = rules.keys.maxOfOrNull { it.length } ?: 0

    fun convert(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            val current = text[index].asciiLetter()
            val next = text.getOrNull(index + 1)?.asciiLetter()
            if (autoSokuon && current in SOKUON_CONSONANTS && current == next) {
                append("っ")
                index++
                continue
            }
            if (autoN && current == 'n' && next != null && next in 'a'..'z' && next !in "aiueoyn") {
                append("ん")
                index++
                continue
            }
            var matched = false
            for (length in minOf(maxKeyLength, text.length - index) downTo 1) {
                val rule = rules[text.substring(index, index + length)] ?: continue
                append(rule.first)
                // Legacy imports can contain invalid consumption values. Always make progress.
                index += rule.second.coerceIn(1, text.length - index)
                matched = true
                break
            }
            if (!matched) append(text[index++])
        }
    }

    fun flush(text: String): String =
        if (autoN && text.lastOrNull()?.asciiLetter() == 'n') text.dropLast(1) + "ん" else text

    private fun Char.asciiLetter(): Char =
        if (this in 'ａ'..'ｚ') (code - 0xfee0).toChar() else this

    private companion object {
        // Same set as the default software QWERTY converter; n is deliberately excluded.
        const val SOKUON_CONSONANTS = "kstcpbdfghljmqrvwxyz"
    }
}
