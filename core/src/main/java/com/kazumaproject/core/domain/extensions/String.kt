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
 * 文字が半角の英数記号（ASCIIの表示可能文字）であるかを判定します。
 * スペースも半角と見なします。
 */
fun Char.isHalfWidthAscii(): Boolean {
    // 半角の感嘆符(!)からチルダ(~)まで、および半角スペース
    return this in '\u0020'..'\u007E'
}

/**
 * 文字が全角の英数記号であるかを判定します。
 * 全角スペースも全角と見なします。
 */
fun Char.isFullWidthAscii(): Boolean {
    // 全角の感嘆符(！)からチルダ(～)まで、および全角スペース
    return this in '\uFF01'..'\uFF5E' || this == '\u3000'
}

/**
 * 文字列がすべて半角の英数記号で構成されているかを判定します。
 */
fun String.isAllHalfWidthAscii(): Boolean {
    if (this.isEmpty()) return false
    return this.all { it.isHalfWidthAscii() }
}

/**
 * 文字列がすべて全角の英数記号で構成されているかを判定します。
 */
fun String.isAllFullWidthAscii(): Boolean {
    if (this.isEmpty()) return false
    return this.all { it.isFullWidthAscii() }
}
