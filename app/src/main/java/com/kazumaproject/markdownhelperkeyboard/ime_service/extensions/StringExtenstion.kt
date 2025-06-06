package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.view.inputmethod.InputConnection
import timber.log.Timber
import java.text.BreakIterator

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
    // ① カーソル前 8 コードユニットを取得しておく（ZWJ＋性別＋VS16≒6 コードユニットを想定し、余裕を持って 8）
    val maxLookback = 8
    val beforeText = ic.getTextBeforeCursor(maxLookback, 0)?.toString() ?: ""
    if (beforeText.isEmpty()) return ""

    // ② BreakIterator で「最後のグラフェムクラスタ」の開始位置を探す
    val bi = BreakIterator.getCharacterInstance()
    bi.setText(beforeText)

    // 「カーソルから見て最後の位置」は beforeText.length
    val end = beforeText.length
    // preceding() で、末尾直前のグラフェム境界を得る
    val start = bi.preceding(end).let { if (it == BreakIterator.DONE) 0 else it }

    // ③ substring でその範囲を丸ごと取り出す
    return beforeText.substring(start, end)
}
