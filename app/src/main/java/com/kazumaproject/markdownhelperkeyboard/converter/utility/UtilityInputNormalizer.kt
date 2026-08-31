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
}
