package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.view.inputmethod.InputConnection
import timber.log.Timber
import java.text.BreakIterator
import java.text.Normalizer

fun String.correctReading(): Pair<String, String> {
    val readingCorrectionString = this.split("\t")
    val readingCorrectionTango = readingCorrectionString[0]
    val readingCorrectionCorrectYomi = readingCorrectionString[1]
    return Pair(readingCorrectionTango, readingCorrectionCorrectYomi)
}

fun Char.isEnglishLetter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z'
}

fun String.isAllEnglishLetters(): Boolean =
    isNotEmpty() && all { it.isEnglishLetter() }

/**
 * Checks if the character is a zenkaku (full-width) English letter.
 */
fun Char.isZenkakuLetter(): Boolean {
    return this in 'ａ'..'ｚ' || this in 'Ａ'..'Ｚ'
}

/**
 * Checks if the string is not empty and contains only zenkaku (full-width) English letters.
 */
fun String.isAllZenkakuLetters(): Boolean =
    isNotEmpty() && all { it.isZenkakuLetter() }

/**
 * 1. 文字が半角ASCII（英数記号）であるかチェックする拡張関数
 * U+0020 (スペース) から U+007E (~) までの範囲をチェックします。
 */
fun Char.isHalfWidthAscii(): Boolean {
    return this in '\u0020'..'\u007E'
}

/**
 * 2. 文字列全体が半角ASCII（英数記号）で構成されているかチェックする拡張関数
 */
fun String.isAllHalfWidthAscii(): Boolean =
    isNotEmpty() && all { it.isHalfWidthAscii() }

/**
 * Checks if a character is a full-width (全角) character.
 * This includes full-width alphanumeric characters, symbols, katakana, hiragana, and kanji.
 */
fun Char.isFullWidth(): Boolean {
    // The primary range for full-width characters starts from U+FF01.
    // This also includes hiragana, katakana, and common kanji ranges.
    return this in '\uFF01'..'\uFF5E' || // Full-width ASCII variants
            this in '\u3000'..'\u303F' || // Japanese punctuation and symbols
            this in '\u3040'..'\u309F' || // Hiragana
            this in '\u30A0'..'\u30FF' || // Katakana
            this in '\u4E00'..'\u9FFF' || // CJK Unified Ideographs (common Kanji)
            this == '\u2010' // Full-width hyphen
}

/**
 * Checks if the entire string is composed of full-width (全角) characters.
 */
fun String.isAllFullWidth(): Boolean =
    isNotEmpty() && all { it.isFullWidth() }

/**
 * Checks if a character is a full-width alphanumeric or symbol character.
 * This covers the range from U+FF01 (！) to U+FF5E (～) and the full-width space.
 */
fun Char.isFullWidthAscii(): Boolean {
    // U+FF01 to U+FF5E contains the full-width versions of ASCII letters, numbers, and symbols.
    // U+3000 is the full-width space.
    return this in '\uFF01'..'\uFF5E' || this == '\u3000'
}

/**
 * Checks if the entire string is composed of full-width alphanumeric characters and symbols.
 */
fun String.isAllFullWidthAscii(): Boolean =
    isNotEmpty() && all { it.isFullWidthAscii() }

/**
 * 3. 半角ASCII文字列を全角に変換する拡張関数
 * - スペースは例外的にU+3000に変換します。
 * - その他の文字は、基本的にオフセット `0xFEE0` を加算して変換します。
 */
fun String.toFullWidth(): String {
    return this.map { char ->
        when (char) {
            ' ' -> '　' // 半角スペース(U+0020)は全角スペース(U+3000)へ
            else -> (char.code + 0xFEE0).toChar()
        }
    }.joinToString("")
}

private fun Char.isHiragana(): Boolean =
    this in '\u3041'..'\u3096'

fun String.isAllHiragana(): Boolean =
    isNotEmpty() && all { it.isHiragana() }

// 数字かどうか
fun Char.isNumber(): Boolean =
    this.isDigit()

// 句読点や記号（一般的な記号区分）かどうか
fun Char.isPunctuationOrSymbol(): Boolean {
    val type = Character.getType(this)  // Int
    return when (type) {
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt() -> true

        else -> false
    }
}

// 文字列中に記号・数字・絵文字が含まれているかをまとめて判定
fun String.containsSymbolNumberOrEmoji(): Boolean =
    any { it.isNumber() || it.isPunctuationOrSymbol() }

/**
 * 絵文字などを含む文字列を「グラフェム単位」で逆順にする拡張関数
 */
fun String.reversePreservingGraphemes(): String {
    // 文字単位（Grapheme Cluster）で区切る BreakIterator を用意
    val breaker = BreakIterator.getCharacterInstance()
    breaker.setText(this)

    val result = StringBuilder(this.length)
    var end = breaker.last()
    var start = breaker.previous()

    while (start != BreakIterator.DONE) {
        // start..end で「絵文字ひとまとまり」や通常の一文字を切り出して追加
        result.append(this.substring(start, end))
        end = start
        start = breaker.previous()
    }
    return result.toString()
}

fun debugPrintCodePoints(text: String) {
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        // 16進数で出してみれば、サロゲートペアが壊れていないかも確認できる
        Timber.d(
            "index=%d, codePoint=0x%04X (%s)",
            i,
            cp,
            Character.charCount(cp).let { if (it == 2) "サロゲートペア" else "単一" })
        i += Character.charCount(cp)
    }
}

fun getLastCharacterAsString(ic: InputConnection): String {
    // ① カーソル前 8 コードユニットを取得しておく
    val maxLookback = 8
    val rawBefore = ic.getTextBeforeCursor(maxLookback, 0)?.toString() ?: ""
    if (rawBefore.isEmpty()) return ""

    // ② Canonical 分解＋合成（NFC）で、全角記号はそのまま保持
    val beforeText = Normalizer.normalize(rawBefore, Normalizer.Form.NFC)

    // ③ BreakIterator で最後のグラフェムクラスタの開始位置を探す
    val bi = BreakIterator.getCharacterInstance()
    bi.setText(beforeText)
    val end = beforeText.length
    val start = bi.preceding(end).let { if (it == BreakIterator.DONE) 0 else it }

    // ④ その範囲を丸ごと取り出す
    val temp = beforeText.substring(start, end)
    if (temp == "あ゙" || temp == "ぁ゙" || temp == "ア゙" || temp == "ァ゙"
        || temp == "い゙" || temp == "ぃ゙" || temp == "ゐ゙" || temp == "イ゙" || temp == "ィ゙"
        || temp == "ぅ゙" || temp == "ゥ゙"
        || temp == "え゙" || temp == "エ゙" || temp == "ぇ゙" || temp == "ェ゙" || temp == "ゑ゙"
        || temp == "お゙" || temp == "ぉ゙" || temp == "オ゙" || temp == "ォ゙"
        || temp == "や゙" || temp == "ゃ゙" || temp == "ヤ゙" || temp == "ャ゙"
        || temp == "ゆ゙" || temp == "ゅ゙" || temp == "ユ゙" || temp == "ュ゙"
        || temp == "よ゙" || temp == "ょ゙" || temp == "ヨ゙" || temp == "ョ゙"
        || temp == "な゙" || temp == "ナ゙"
        || temp == "に゙" || temp == "ニ゙"
        || temp == "ぬ゙" || temp == "ヌ゙"
        || temp == "ね゙" || temp == "ネ゙"
        || temp == "の゙" || temp == "ノ゙"
        || temp == "ま゙" || temp == "マ゙"
        || temp == "み゙" || temp == "ミ゙"
        || temp == "む゙" || temp == "ム゙"
        || temp == "め゙" || temp == "メ゙"
        || temp == "も゙" || temp == "モ゙"
        || temp == "ら゙" || temp == "ラ゙"
        || temp == "り゙" || temp == "リ゙"
        || temp == "る゙" || temp == "ル゙"
        || temp == "れ゙" || temp == "レ゙"
        || temp == "ろ゙" || temp == "ロ゙"
        || temp == "わ゙" || temp == "ヷ"
        || temp == "を゙" || temp == "ヺ"
        || temp == "ん゙" || temp == "ン゙"
    ) {
        return "゙"
    }
    return beforeText.substring(start, end)
}

private val validTwoCharBrackets = setOf(
    "()", "[]", "{}", "<>", "「」",
    "（）", "［］", "｛｝", "＜＞",
    "〔〕", "〘〙", "〘〙", "〚〛", "〈〉",
    "《》", "«»", "‹›", "『』", "【】"
)

fun String.isOnlyTwoCharBracketPair(): Boolean {
    return validTwoCharBrackets.contains(this)
}

fun String.replaceJapaneseCharactersForEnglish(): String {
    return this.replace('あ', '@')
        .replace('い', '#')
        .replace('う', '/')
        .replace('え', '_')
        .replace('お', '1')
        .replace('か', 'a')
        .replace('き', 'b')
        .replace('く', 'c')
        .replace('こ', '2')
        .replace('さ', 'd')
        .replace('し', 'e')
        .replace('す', 'f')
        .replace('そ', '3')
        .replace('た', 'g')
        .replace('ち', 'h')
        .replace('つ', 'i')
        .replace('と', '4')
        .replace('な', 'j')
        .replace('に', 'k')
        .replace('ぬ', 'l')
        .replace('の', '5')
        .replace('は', 'm')
        .replace('ひ', 'n')
        .replace('ふ', 'o')
        .replace('ほ', '6')
        .replace('ま', 'p')
        .replace('み', 'q')
        .replace('む', 'r')
        .replace('め', 's')
        .replace('も', '7')
        .replace('や', 't')
        .replace('ゆ', 'v')
        .replace('よ', '8')
        .replace('ら', 'w')
        .replace('り', 'x')
        .replace('る', 'y')
        .replace('れ', 'z')
        .replace('ろ', '9')
        .replace('わ', '\'')
        .replace('を', '"')
        .replace('ん', '(')
        .replace('ー', ')')
        .replace('〜', '0')
        .replace('、', ',')
        .replace('。', '.')
        .replace('？', '?')
        .replace('！', '!')
}

fun String.groupAndReplaceJapaneseForNumber(): String {
    val result = StringBuilder()
    for (char in this) {
        when (char) {
            in 'あ'..'お' -> result.append('1')
            in 'か'..'こ' -> result.append('2')
            in 'さ'..'そ' -> result.append('3')
            in 'た'..'と' -> result.append('4')
            in 'な'..'の' -> result.append('5')
            in 'は'..'ほ' -> result.append('6')
            in 'ま'..'も' -> result.append('7')
            'や', 'ゆ', 'よ' -> result.append('8')
            in 'ら'..'ろ' -> result.append('9')
            'わ', 'を', 'ん' -> result.append('0')
            // 上記以外の文字はそのまま追加する
            else -> result.append(char)
        }
    }
    return result.toString()
}
