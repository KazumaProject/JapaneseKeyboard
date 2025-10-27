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
