package com.kazumaproject.core.domain.extensions

/**
 * Extension function to convert all Hiragana characters in this String to their corresponding Katakana.
 *
 * Hiragana Unicode range: U+3041 (ぁ) to U+3096 (ゖ)
 * Katakana Unicode range: U+30A1 (ァ) to U+30F6 (ヶ)
 *
 * The offset between Hiragana and Katakana code points is 0x60.
 */
fun String.hiraganaToKatakana(): String = buildString {
    for (ch in this@hiraganaToKatakana) {
        when (ch) {
            // Basic Hiragana range (ぁ〜ゖ)
            in '\u3041'..'\u3096' -> append(ch + 0x60)
            // Hiragana iteration mark (ゝ U+309D) → Katakana iteration mark (ヽ U+30FD)
            '\u309D' -> append('\u30FD')
            // Hiragana voiced iteration mark (ゞ U+309E) → Katakana voiced iteration mark (ヾ U+30FE)
            '\u309E' -> append('\u30FE')
            else -> append(ch)
        }
    }
}

/**
 * Extension function to convert all Katakana characters in this String to their corresponding Hiragana.
 *
 * Katakana Unicode range: U+30A1 (ァ) to U+30F6 (ヶ)
 * Hiragana Unicode range: U+3041 (ぁ) to U+3096 (ゖ)
 *
 * The offset between Katakana and Hiragana code points is -0x60.
 */
fun String.katakanaToHiragana(): String = buildString {
    for (ch in this@katakanaToHiragana) {
        when (ch) {
            // Basic Katakana range (ァ〜ヶ)
            in '\u30A1'..'\u30F6' -> append(ch - 0x60)
            // Katakana iteration mark (ヽ U+30FD) → Hiragana iteration mark (ゝ U+309D)
            '\u30FD' -> append('\u309D')
            // Katakana voiced iteration mark (ヾ U+30FE) → Hiragana voiced iteration mark (ゞ U+309E)
            '\u30FE' -> append('\u309E')
            else -> append(ch)
        }
    }
}

/**
 * 文字が半角の「数字または記号」であるかを判定します。
 * アルファベットは false を返します。
 */
fun Char.isHalfWidthNumericSymbol(): Boolean {
    return when (this) {
        // 半角数字 (0-9)
        in '\u0030'..'\u0039' -> true
        // 半角記号とスペース
        in '\u0020'..'\u002F', in '\u003A'..'\u0040', in '\u005B'..'\u0060', in '\u007B'..'\u007E' -> true
        // それ以外（アルファベット等）は false
        else -> false
    }
}

/**
 * 文字が全角の「数字または記号」であるかを判定します。
 * アルファベットは false を返します。
 */
fun Char.isFullWidthNumericSymbol(): Boolean {
    return when (this) {
        // 全角数字 (０-９)
        in '\uFF10'..'\uFF19' -> true
        // 全角記号と全角スペース
        '\u3000', in '\uFF01'..'\uFF0F', in '\uFF1A'..'\uFF20', in '\uFF3B'..'\uFF40', in '\uFF5B'..'\uFF5E' -> true
        // それ以外（アルファベット等）は false
        else -> false
    }
}

/**
 * 文字列がすべて半角の「数字または記号」で構成されているかを判定します。
 */
fun String.isAllHalfWidthNumericSymbol(): Boolean {
    if (this.isEmpty()) return false
    return this.all { it.isHalfWidthNumericSymbol() }
}

/**
 * 文字列がすべて全角の「数字または記号」で構成されているかを判定します。
 */
fun String.isAllFullWidthNumericSymbol(): Boolean {
    if (this.isEmpty()) return false
    return this.all { it.isFullWidthNumericSymbol() }
}
