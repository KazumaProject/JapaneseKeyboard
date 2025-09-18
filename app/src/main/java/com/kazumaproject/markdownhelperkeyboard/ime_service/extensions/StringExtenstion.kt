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
