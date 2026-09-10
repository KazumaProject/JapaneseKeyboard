package com.kazumaproject.markdownhelperkeyboard.converter.utility

import java.text.Normalizer

object UtilityInputNormalizer {
    fun normalize(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
        return buildString(normalized.length) {
            normalized.forEach { char ->
                append(
                    when (char) {
                        '\u00a0', '\u2000', '\u2001', '\u2002', '\u2003', '\u2004',
                        '\u2005', '\u2006', '\u2007', '\u2008', '\u2009', '\u200a',
                        '\u202f', '\u205f', '\u3000' -> ' '
                        '×' -> '*'
                        '÷' -> '/'
                        '−', '–', '—' -> '-'
                        '→', '⇒' -> '>'
                        'μ' -> 'µ'
                        else -> char
                    }
                )
            }
        }
    }

    /**
     * Normalizes full-width ASCII input for formula parsing without applying NFKC to mathematical
     * compatibility characters such as ℕ, ℝ, and superscript glyphs.  Those characters carry
     * mathematical meaning and must reach the formula parser unchanged.
     */
    fun normalizeForFormula(input: String): String {
        return buildString(input.length) {
            input.forEach { char ->
                when {
                    char in '\uFF01'..'\uFF5E' -> append((char.code - 0xFEE0).toChar())
                    char == '\u3000' || char == '\u00a0' ||
                        char in '\u2000'..'\u200a' || char == '\u202f' || char == '\u205f' ->
                        append(' ')
                    else -> append(char)
                }
            }
        }
    }
}
