package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.view.inputmethod.InputConnection
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
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

private fun Char.isHiraganaWithSymbols(): Boolean =
    this in '\u3041'..'\u3096' || this == 'ー' || this == '-' || this == '？' || this == '?'
            || this == '！' || this == '!' || this == '。' || this == '、'

fun String.isAllHiraganaWithSymbols(): Boolean =
    isNotEmpty() && all { it.isHiraganaWithSymbols() }

// 数字かどうか（Nd / Nl / No を含める）
fun Char.isNumber(): Boolean {
    return when (Character.getType(this)) {
        Character.DECIMAL_DIGIT_NUMBER.toInt(), // Nd: 0-9 など
        Character.LETTER_NUMBER.toInt(),        // Nl: ローマ数字(Ⅰ)など
        Character.OTHER_NUMBER.toInt()          // No: ①, ㉑, ㊱ など
            -> true

        else -> false
    }
}

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

/**
 * 数字文字列を上付き文字（¹²³）に変換します。
 */
fun String.toSuperscriptDigits(): String {
    return this.map {
        when (it) {
            '0' -> '⁰'
            '1' -> '¹'
            '2' -> '²'
            '3' -> '³'
            '4' -> '⁴'
            '5' -> '⁵'
            '6' -> '⁶'
            '7' -> '⁷'
            '8' -> '⁸'
            '9' -> '⁹'
            else -> it // 数字以外はそのまま
        }
    }.joinToString("")
}

/**
 * 数字文字列を下付き文字（₀₁₂）に変換します。
 */
fun String.toSubscriptDigits(): String {
    return this.map {
        when (it) {
            '0' -> '₀'
            '1' -> '₁'
            '2' -> '₂'
            '3' -> '₃'
            '4' -> '₄'
            '5' -> '₅'
            '6' -> '₆'
            '7' -> '₇'
            '8' -> '₈'
            '9' -> '₉'
            else -> it // 数字以外はそのまま
        }
    }.joinToString("")
}

/**
 * 数値（Long）から、それに対応する特殊記号の候補リストを生成します。
 * (例: 1 -> "①", "❶", "Ⅰ", "ⅰ", "⒈", "⑴", 0 -> "⓪")
 * 0から50までの、Unicodeに存在する主要な記号に対応します。
 */
fun createValueBasedSymbolCandidates(numberValue: Long, inputLength: UByte): List<Candidate> {
    val num = numberValue.toInt() // 範囲チェックのためIntに
    // 0から50の範囲外の場合は空リストを返す
    if (num < 0 || num > 50) {
        return emptyList()
    }

    val candidates = mutableListOf<Pair<String, Int>>() // (文字列, スコア)

    // 候補のタイプごとにスコアを分ける
    val scoreCircled = 8500 // ① ㉑ ㊱ (白丸)
    val scoreDingbat = 8490 // ❶ (黒丸)
    val scoreRoman = 8480   // Ⅰ (ローマ数字)
    val scoreList = 8470    // ⒈ (ピリオド)
    val scoreParen = 8460   // ⑴ (括弧)

    // 数値に基づいて候補を追加
    when (num) {
        0 -> {
            candidates.add(Pair("⓪", scoreCircled))
            candidates.add(Pair("⓿", scoreDingbat)) // 黒丸の0
        }

        1 -> {
            candidates.add(Pair("①", scoreCircled)); candidates.add(Pair("❶", scoreDingbat))
            candidates.add(Pair("Ⅰ", scoreRoman)); candidates.add(Pair("ⅰ", scoreRoman))
            candidates.add(Pair("⒈", scoreList)); candidates.add(Pair("⑴", scoreParen))
        }

        2 -> {
            candidates.add(Pair("②", scoreCircled)); candidates.add(Pair("❷", scoreDingbat))
            candidates.add(Pair("Ⅱ", scoreRoman)); candidates.add(Pair("ⅱ", scoreRoman))
            candidates.add(Pair("⒉", scoreList)); candidates.add(Pair("⑵", scoreParen))
        }

        3 -> {
            candidates.add(Pair("③", scoreCircled)); candidates.add(Pair("❸", scoreDingbat))
            candidates.add(Pair("Ⅲ", scoreRoman)); candidates.add(Pair("ⅲ", scoreRoman))
            candidates.add(Pair("⒊", scoreList)); candidates.add(Pair("⑶", scoreParen))
        }

        4 -> {
            candidates.add(Pair("④", scoreCircled)); candidates.add(Pair("❹", scoreDingbat))
            candidates.add(Pair("Ⅳ", scoreRoman)); candidates.add(Pair("ⅳ", scoreRoman))
            candidates.add(Pair("⒋", scoreList)); candidates.add(Pair("⑷", scoreParen))
        }

        5 -> {
            candidates.add(Pair("⑤", scoreCircled)); candidates.add(Pair("❺", scoreDingbat))
            candidates.add(Pair("Ⅴ", scoreRoman)); candidates.add(Pair("ⅴ", scoreRoman))
            candidates.add(Pair("⒌", scoreList)); candidates.add(Pair("⑸", scoreParen))
        }

        6 -> {
            candidates.add(Pair("⑥", scoreCircled)); candidates.add(Pair("❻", scoreDingbat))
            candidates.add(Pair("Ⅵ", scoreRoman)); candidates.add(Pair("ⅵ", scoreRoman))
            candidates.add(Pair("⒍", scoreList)); candidates.add(Pair("⑹", scoreParen))
        }

        7 -> {
            candidates.add(Pair("⑦", scoreCircled)); candidates.add(Pair("❼", scoreDingbat))
            candidates.add(Pair("Ⅶ", scoreRoman)); candidates.add(Pair("ⅶ", scoreRoman))
            candidates.add(Pair("⒎", scoreList)); candidates.add(Pair("⑺", scoreParen))
        }

        8 -> {
            candidates.add(Pair("⑧", scoreCircled)); candidates.add(Pair("❽", scoreDingbat))
            candidates.add(Pair("Ⅷ", scoreRoman)); candidates.add(Pair("ⅷ", scoreRoman))
            candidates.add(Pair("⒏", scoreList)); candidates.add(Pair("⑻", scoreParen))
        }

        9 -> {
            candidates.add(Pair("⑨", scoreCircled)); candidates.add(Pair("❾", scoreDingbat))
            candidates.add(Pair("Ⅸ", scoreRoman)); candidates.add(Pair("ⅸ", scoreRoman))
            candidates.add(Pair("⒐", scoreList)); candidates.add(Pair("⑼", scoreParen))
        }

        10 -> {
            candidates.add(Pair("⑩", scoreCircled)); candidates.add(Pair("❿", scoreDingbat))
            candidates.add(Pair("Ⅹ", scoreRoman)); candidates.add(Pair("ⅹ", scoreRoman))
            candidates.add(Pair("⒑", scoreList)); candidates.add(Pair("⑽", scoreParen))
        }
        // --- 11-20 (❶ 黒丸が終了) ---
        11 -> {
            candidates.add(Pair("⑪", scoreCircled))
            candidates.add(Pair("Ⅺ", scoreRoman)); candidates.add(Pair("ⅺ", scoreRoman))
            candidates.add(Pair("⒒", scoreList)); candidates.add(Pair("⑾", scoreParen))
        }

        12 -> {
            candidates.add(Pair("⑫", scoreCircled))
            candidates.add(Pair("Ⅻ", scoreRoman)); candidates.add(Pair("ⅻ", scoreRoman))
            candidates.add(Pair("⒓", scoreList)); candidates.add(Pair("⑿", scoreParen))
        }

        13 -> {
            candidates.add(Pair("⑬", scoreCircled))
            candidates.add(Pair("⒔", scoreList)); candidates.add(Pair("⒀", scoreParen))
        }

        14 -> {
            candidates.add(Pair("⑭", scoreCircled))
            candidates.add(Pair("⒕", scoreList)); candidates.add(Pair("⒁", scoreParen))
        }

        15 -> {
            candidates.add(Pair("⑮", scoreCircled))
            candidates.add(Pair("⒖", scoreList)); candidates.add(Pair("⒂", scoreParen))
        }

        16 -> {
            candidates.add(Pair("⑯", scoreCircled))
            candidates.add(Pair("⒗", scoreList)); candidates.add(Pair("⒃", scoreParen))
        }

        17 -> {
            candidates.add(Pair("⑰", scoreCircled))
            candidates.add(Pair("⒘", scoreList)); candidates.add(Pair("⒄", scoreParen))
        }

        18 -> {
            candidates.add(Pair("⑱", scoreCircled))
            candidates.add(Pair("⒙", scoreList)); candidates.add(Pair("⒅", scoreParen))
        }

        19 -> {
            candidates.add(Pair("⑲", scoreCircled))
            candidates.add(Pair("⒚", scoreList)); candidates.add(Pair("⒆", scoreParen))
        }

        20 -> {
            candidates.add(Pair("⑳", scoreCircled))
            candidates.add(Pair("⒛", scoreList)); candidates.add(Pair("⒇", scoreParen))
        }
        // --- 21-35 (㉑...㉟) ---
        // (ピリオド と 括弧 が終了)
        21 -> candidates.add(Pair("㉑", scoreCircled))
        22 -> candidates.add(Pair("㉒", scoreCircled))
        23 -> candidates.add(Pair("㉓", scoreCircled))
        24 -> candidates.add(Pair("㉔", scoreCircled))
        25 -> candidates.add(Pair("㉕", scoreCircled))
        26 -> candidates.add(Pair("㉖", scoreCircled))
        27 -> candidates.add(Pair("㉗", scoreCircled))
        28 -> candidates.add(Pair("㉘", scoreCircled))
        29 -> candidates.add(Pair("㉙", scoreCircled))
        30 -> candidates.add(Pair("㉚", scoreCircled))
        31 -> candidates.add(Pair("㉛", scoreCircled))
        32 -> candidates.add(Pair("㉜", scoreCircled))
        33 -> candidates.add(Pair("㉝", scoreCircled))
        34 -> candidates.add(Pair("㉞", scoreCircled))
        35 -> candidates.add(Pair("㉟", scoreCircled))
        // --- 36-50 (㊱...㊿) ---
        36 -> candidates.add(Pair("㊱", scoreCircled))
        37 -> candidates.add(Pair("㊲", scoreCircled))
        38 -> candidates.add(Pair("㊳", scoreCircled))
        39 -> candidates.add(Pair("㊴", scoreCircled))
        40 -> candidates.add(Pair("㊵", scoreCircled))
        41 -> candidates.add(Pair("㊶", scoreCircled))
        42 -> candidates.add(Pair("㊷", scoreCircled))
        43 -> candidates.add(Pair("㊸", scoreCircled))
        44 -> candidates.add(Pair("㊹", scoreCircled))
        45 -> candidates.add(Pair("㊺", scoreCircled))
        46 -> candidates.add(Pair("㊻", scoreCircled))
        47 -> candidates.add(Pair("㊼", scoreCircled))
        48 -> candidates.add(Pair("㊽", scoreCircled))
        49 -> candidates.add(Pair("㊾", scoreCircled))
        50 -> {
            candidates.add(Pair("㊿", scoreCircled))
            // 50はローマ数字 (L) が存在する
            candidates.add(Pair("Ⅼ", scoreRoman)); candidates.add(Pair("ⅼ", scoreRoman))
        }
    }

    // PairのリストをCandidateのリストに変換
    return candidates.map { (str, score) ->
        Candidate(
            string = str,
            type = 21, // 21で統一
            length = inputLength,
            score = score,
            leftId = 2040,
            rightId = 2040
        )
    }
}

fun String.containsDigit(): Boolean {
    return this.any { it.isDigit() }
}

/**
 * この文字列に含まれる全角数字（０〜９）を
 * 対応する半角数字（0-9）に変換します。
 *
 * 全角数字以外の文字はそのまま維持されます。
 *
 * @return 半角数字に変換された新しい文字列
 */
fun String.convertFullWidthNumbersToHalfWidth(): String {
    // 文字列を1文字ずつ処理します
    return this.map { char ->
        // 現在の文字が全角数字の範囲内かチェック
        if (char in '０'..'９') {
            // 全角'０'と半角'0'のUnicodeコードポイントの差を利用して変換
            // (例: '３'.code - '０'.code は 3 となる)
            val numericValue = char.code - '０'.code

            // 半角'0'のコードポイントにその差を足して、
            // 対応する半角文字に変換
            ('0'.code + numericValue).toChar()
        } else {
            // 全角数字でなければ、その文字をそのまま返す
            char
        }
    }.joinToString("") // 処理後の文字リストを結合して文字列に戻す
}

/**
 * この文字列に全角数字（０〜９）が1文字以上含まれているかを確認します。
 * * @return 全角数字が含まれていれば true、そうでなければ false
 */
fun String.containsFullWidthNumber(): Boolean {
    // '０' (U+FF10) から '９' (U+FF19) までの文字が
    // 1文字でも含まれているかをチェックします。
    return this.any { it in '０'..'９' }
}
