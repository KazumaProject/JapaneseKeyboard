package com.kazumaproject.core.domain.extensions

fun Char.asFullWidth(): Char =
    if (code in 0x21..0x7E) (code + 0xFEE0).toChar() else this

/**
 * Converts a zenkaku (full-width) character to its hankaku (half-width) equivalent.
 * Returns the original character if it's not a convertible zenkaku character.
 */
fun Char.toHankaku(): Char {
    return when (this) {
        // Full-width space
        '\u3000' -> ' '
        // Full-width ASCII-range characters
        in '\uFF01'..'\uFF5E' -> (this.code - 0xFEE0).toChar()
        // Otherwise, return the character itself
        else -> this
    }
}

fun Char.toHankakuKigou(): Char = when (this) {
    // Full-width space
    '\u3000' -> ' '

    // Full-width ASCII-range characters
    in '\uFF01'..'\uFF5E' -> (this.code - 0xFEE0).toChar()

    // Fullwidth symbol variants (outside FF01..FF5E)
    '\uFFE0' -> '\u00A2' // ￠ -> ¢
    '\uFFE1' -> '\u00A3' // ￡ -> £
    '\uFFE2' -> '\u00AC' // ￢ -> ¬
    '\uFFE3' -> '\u00AF' // ￣ -> ¯
    '\uFFE4' -> '\u00A6' // ￤ -> ¦
    '\uFFE5' -> '\u00A5' // ￥ -> ¥
    '\uFFE6' -> '\u20A9' // ￦ -> ₩

    // Frequently-seen "looks like fullwidth" symbols (not in FFxx)
    '\u301C' -> '~'      // 〜 (WAVE DASH) -> ~  ※必要なら
    '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212' -> '-' // various dashes -> -
    '\u2018', '\u2019' -> '\'' // ‘ ’ -> '
    '\u201C', '\u201D' -> '"'  // “ ” -> "
    'ー' -> 'ｰ' // U+30FC -> U+FF70

    else -> this
}

/**
 * Converts a hankaku (half-width) character to its zenkaku (full-width) equivalent.
 * Returns the original character if it's not a convertible hankaku character.
 */
fun Char.toZenkaku(): Char {
    return when (this) {
        // Half-width space
        ' ' -> '\u3000'
        // Half-width ASCII-range characters
        in '!'..'~' -> (this.code + 0xFEE0).toChar()
        // Otherwise, return the character itself
        else -> this
    }
}
