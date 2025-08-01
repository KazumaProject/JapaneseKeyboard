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
