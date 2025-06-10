package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import android.view.KeyEvent

class RomajiKanaConverter {
    private val buffer = StringBuilder()
    private val surface = StringBuilder()

    /**
     * ローマ字からひらがなへの変換
     *
     * https://github.com/google/mozc/blob/master/src/data/preedit/romanji-hiragana.tsv
     *
     * **/
    private val romajiToKana: Map<String, Pair<String, Int>> = mapOf(
        // punctuation / symbols
        "-" to Pair("ー", 1),
        "~" to Pair("〜", 1),
        "." to Pair("。", 1),
        "," to Pair("、", 1),
        "z/" to Pair("・", 2),
        "z." to Pair("…", 2),
        "z," to Pair("‥", 2),
        "zh" to Pair("←", 2),
        "zj" to Pair("↓", 2),
        "zk" to Pair("↑", 2),
        "zl" to Pair("→", 2),
        "z-" to Pair("〜", 2),
        "z[" to Pair("『", 2),
        "z]" to Pair("』", 2),
        "[" to Pair("「", 1),
        "]" to Pair("」", 1),

        // v-row
        "va" to Pair("ゔぁ", 2),
        "vi" to Pair("ゔぃ", 2),
        "vu" to Pair("ゔ", 2),
        "ve" to Pair("ゔぇ", 2),
        "vo" to Pair("ゔぉ", 2),
        "vya" to Pair("ゔゃ", 3),
        "vyi" to Pair("ゔぃ", 3),
        "vyu" to Pair("ゔゅ", 3),
        "vye" to Pair("ゔぇ", 3),
        "vyo" to Pair("ゔょ", 3),

        // gemination (small tsu + consonant)
        "qq" to Pair("っ", 2),
        "vv" to Pair("っ", 2),
        "ll" to Pair("っ", 2),
        "xx" to Pair("っ", 2),
        "kk" to Pair("っ", 2),
        "gg" to Pair("っ", 2),
        "ss" to Pair("っ", 2),
        "zz" to Pair("っ", 2),
        "jj" to Pair("っ", 2),
        "tt" to Pair("っ", 2),
        "tch" to Pair("っ", 3),
        "dd" to Pair("っ", 2),
        "hh" to Pair("っ", 2),
        "ff" to Pair("っ", 2),
        "bb" to Pair("っ", 2),
        "pp" to Pair("っ", 2),
        "mm" to Pair("っ", 2),
        "yy" to Pair("っ", 2),
        "rr" to Pair("っ", 2),
        "ww" to Pair("っ", 2),
        "www" to Pair("www", 3),
        "cc" to Pair("っ", 2),

        // k-row youon
        "kya" to Pair("きゃ", 3),
        "kyi" to Pair("きぃ", 3),
        "kyu" to Pair("きゅ", 3),
        "kye" to Pair("きぇ", 3),
        "kyo" to Pair("きょ", 3),

        // g-row youon
        "gya" to Pair("ぎゃ", 3),
        "gyi" to Pair("ぎぃ", 3),
        "gyu" to Pair("ぎゅ", 3),
        "gye" to Pair("ぎぇ", 3),
        "gyo" to Pair("ぎょ", 3),

        // s-row
        "sya" to Pair("しゃ", 3),
        "syi" to Pair("しぃ", 3),
        "syu" to Pair("しゅ", 3),
        "sye" to Pair("しぇ", 3),
        "syo" to Pair("しょ", 3),
        "sha" to Pair("しゃ", 3),
        "shi" to Pair("し", 3),
        "shu" to Pair("しゅ", 3),
        "she" to Pair("しぇ", 3),
        "sho" to Pair("しょ", 3),

        "na" to Pair("な", 2),
        "ni" to Pair("に", 2),
        "nu" to Pair("ぬ", 2),
        "ne" to Pair("ね", 2),
        "no" to Pair("の", 2),

        "ka" to Pair("か", 2),
        "ki" to Pair("き", 2),
        "ku" to Pair("く", 2),
        "ke" to Pair("け", 2),
        "ko" to Pair("こ", 2),

        "sa" to Pair("さ", 2),
        "si" to Pair("し", 2),
        "su" to Pair("す", 2),
        "se" to Pair("せ", 2),
        "so" to Pair("そ", 2),

        "ga" to Pair("が", 2),
        "gi" to Pair("ぎ", 2),
        "gu" to Pair("ぐ", 2),
        "ge" to Pair("げ", 2),
        "go" to Pair("ご", 2),

        // z-row
        "zya" to Pair("じゃ", 3),
        "zyi" to Pair("じぃ", 3),
        "zyu" to Pair("じゅ", 3),
        "zye" to Pair("じぇ", 3),
        "zyo" to Pair("じょ", 3),
        "za" to Pair("ざ", 2),
        "zi" to Pair("じ", 2),
        "zu" to Pair("ず", 2),
        "ze" to Pair("ぜ", 2),
        "zo" to Pair("ぞ", 2),

        "ja" to Pair("じゃ", 2),
        "ji" to Pair("じ", 2),
        "ju" to Pair("じゅ", 2),
        "je" to Pair("じぇ", 2),
        "jo" to Pair("じょ", 2),

        // t-row youon & variants
        "tya" to Pair("ちゃ", 3),
        "tyi" to Pair("ちぃ", 3),
        "tyu" to Pair("ちゅ", 3),
        "tye" to Pair("ちぇ", 3),
        "tyo" to Pair("ちょ", 3),
        "cha" to Pair("ちゃ", 3),
        "chi" to Pair("ち", 3),
        "chu" to Pair("ちゅ", 3),
        "che" to Pair("ちぇ", 3),
        "cho" to Pair("ちょ", 3),
        "cya" to Pair("ちゃ", 3),
        "cyi" to Pair("ちぃ", 3),
        "cyu" to Pair("ちゅ", 3),
        "cye" to Pair("ちぇ", 3),
        "cyo" to Pair("ちょ", 3),
        "ta" to Pair("た", 2),
        "ti" to Pair("ち", 2),
        "tu" to Pair("つ", 2),
        "tsu" to Pair("つ", 3),
        "te" to Pair("て", 2),
        "to" to Pair("と", 2),

        // d-row youon & variants
        "dya" to Pair("ぢゃ", 3),
        "dyi" to Pair("ぢぃ", 3),
        "dyu" to Pair("ぢゅ", 3),
        "dye" to Pair("ぢぇ", 3),
        "dyo" to Pair("ぢょ", 3),
        "da" to Pair("だ", 2),
        "di" to Pair("ぢ", 2),
        "du" to Pair("づ", 2),
        "de" to Pair("で", 2),
        "do" to Pair("ど", 2),

        // de-y variants
        "dha" to Pair("でゃ", 3),
        "dhi" to Pair("でぃ", 3),
        "d'i" to Pair("でぃ", 3),
        "dhu" to Pair("でゅ", 3),
        "dhe" to Pair("でぇ", 3),
        "dho" to Pair("でょ", 3),
        "d'yu" to Pair("でゅ", 4),

        // t-h variants
        "tha" to Pair("てゃ", 3),
        "thi" to Pair("てぃ", 3),
        "t'i" to Pair("てぃ", 3),
        "thu" to Pair("てゅ", 3),
        "the" to Pair("てぇ", 3),
        "tho" to Pair("てょ", 3),
        "t'yu" to Pair("てゅ", 4),

        // t-w variants
        "twa" to Pair("とぁ", 3),
        "twi" to Pair("とぃ", 3),
        "twu" to Pair("とぅ", 3),
        "twe" to Pair("とぇ", 3),
        "two" to Pair("とぉ", 3),
        "t'u" to Pair("とぅ", 3),

        // d-w variants
        "dwa" to Pair("どぁ", 3),
        "dwi" to Pair("どぃ", 3),
        "dwu" to Pair("どぅ", 3),
        "dwe" to Pair("どぇ", 3),
        "dwo" to Pair("どぉ", 3),
        "d'u" to Pair("どぅ", 3),

        // n-row youon & n variants
        "nya" to Pair("にゃ", 3),
        "nyi" to Pair("にぃ", 3),
        "nyu" to Pair("にゅ", 3),
        "nye" to Pair("にぇ", 3),
        "nyo" to Pair("にょ", 3),
        "n" to Pair("n", 1),
        "nn" to Pair("ん", 2),
        "xn" to Pair("ん", 2),

        // h-row youon & h-variants
        "hya" to Pair("ひゃ", 3),
        "hyi" to Pair("ひぃ", 3),
        "hyu" to Pair("ひゅ", 3),
        "hye" to Pair("ひぇ", 3),
        "hyo" to Pair("ひょ", 3),
        "ha" to Pair("は", 2),
        "hi" to Pair("ひ", 2),
        "hu" to Pair("ふ", 2),
        "fu" to Pair("ふ", 2),
        "he" to Pair("へ", 2),
        "ho" to Pair("ほ", 2),

        // b-row youon
        "bya" to Pair("びゃ", 3),
        "byi" to Pair("びぃ", 3),
        "byu" to Pair("びゅ", 3),
        "bye" to Pair("びぇ", 3),
        "byo" to Pair("びょ", 3),
        "ba" to Pair("ば", 2),
        "bi" to Pair("び", 2),
        "bu" to Pair("ぶ", 2),
        "be" to Pair("べ", 2),
        "bo" to Pair("ぼ", 2),

        // p-row youon
        "pya" to Pair("ぴゃ", 3),
        "pyi" to Pair("ぴぃ", 3),
        "pyu" to Pair("ぴゅ", 3),
        "pye" to Pair("ぴぇ", 3),
        "pyo" to Pair("ぴょ", 3),
        "pa" to Pair("ぱ", 2),
        "pi" to Pair("ぴ", 2),
        "pu" to Pair("ぷ", 2),
        "pe" to Pair("ぺ", 2),
        "po" to Pair("ぽ", 2),

        // f-variants & youon
        "fa" to Pair("ふぁ", 2),
        "fi" to Pair("ふぃ", 2),
        "fe" to Pair("ふぇ", 2),
        "fo" to Pair("ふぉ", 2),
        "fya" to Pair("ふゃ", 3),
        "fyu" to Pair("ふゅ", 3),
        "fyo" to Pair("ふょ", 3),
        "hwa" to Pair("ふぁ", 3),
        "hwi" to Pair("ふぃ", 3),
        "hwe" to Pair("ふぇ", 3),
        "hwo" to Pair("ふぉ", 3),
        "hwyu" to Pair("ふゅ", 4),

        // m-row youon
        "mya" to Pair("みゃ", 3),
        "myi" to Pair("みぃ", 3),
        "myu" to Pair("みゅ", 3),
        "mye" to Pair("みぇ", 3),
        "myo" to Pair("みょ", 3),
        "ma" to Pair("ま", 2),
        "mi" to Pair("み", 2),
        "mu" to Pair("む", 2),
        "me" to Pair("め", 2),
        "mo" to Pair("も", 2),

        // y-row
        "xya" to Pair("ゃ", 3),
        "lya" to Pair("ゃ", 3),
        "ya" to Pair("や", 2),
        "wyi" to Pair("ゐ", 3),
        "xyu" to Pair("ゅ", 3),
        "lyu" to Pair("ゅ", 3),
        "yu" to Pair("ゆ", 2),
        "wye" to Pair("ゑ", 3),
        "xyo" to Pair("ょ", 3),
        "lyo" to Pair("ょ", 3),
        "yo" to Pair("よ", 2),

        // r-row youon
        "rya" to Pair("りゃ", 3),
        "ryi" to Pair("りぃ", 3),
        "ryu" to Pair("りゅ", 3),
        "rye" to Pair("りぇ", 3),
        "ryo" to Pair("りょ", 3),
        "ra" to Pair("ら", 2),
        "ri" to Pair("り", 2),
        "ru" to Pair("る", 2),
        "re" to Pair("れ", 2),
        "ro" to Pair("ろ", 2),

        // w-row & variants
        "xwa" to Pair("ゎ", 3),
        "lwa" to Pair("ゎ", 3),
        "wa" to Pair("わ", 2),
        "wi" to Pair("うぃ", 2),
        "we" to Pair("うぇ", 2),
        "wo" to Pair("を", 2),
        "wha" to Pair("うぁ", 3),
        "whi" to Pair("うぃ", 3),
        "whu" to Pair("う", 3),
        "whe" to Pair("うぇ", 3),
        "who" to Pair("うぉ", 3),

        // basic vowels
        "a" to Pair("あ", 1),
        "i" to Pair("い", 1),
        "u" to Pair("う", 1),
        "wu" to Pair("う", 2),
        "e" to Pair("え", 1),
        "o" to Pair("お", 1),

        // small vowels
        "xa" to Pair("ぁ", 2),
        "xi" to Pair("ぃ", 2),
        "xu" to Pair("ぅ", 2),
        "xe" to Pair("ぇ", 2),
        "xo" to Pair("ぉ", 2),
        "la" to Pair("ぁ", 2),
        "li" to Pair("ぃ", 2),
        "lu" to Pair("ぅ", 2),
        "le" to Pair("ぇ", 2),
        "lo" to Pair("ぉ", 2),
        "lyi" to Pair("ぃ", 3),
        "xyi" to Pair("ぃ", 3),
        "lye" to Pair("ぇ", 3),
        "xye" to Pair("ぇ", 3),
        "ye" to Pair("いぇ", 2),

        // x-row small kana
        "xka" to Pair("ヵ", 3),
        "xke" to Pair("ヶ", 3),
        "lka" to Pair("ヵ", 3),
        "lke" to Pair("ヶ", 3),

        // qa/ku-variants
        "qa" to Pair("くぁ", 2),
        "qi" to Pair("くぃ", 2),
        "qu" to Pair("く", 2),
        "qe" to Pair("くぇ", 2),
        "qo" to Pair("くぉ", 2),

        // kw-variants
        "kwa" to Pair("くぁ", 3),
        "kwi" to Pair("くぃ", 3),
        "kwu" to Pair("くぅ", 3),
        "kwe" to Pair("くぇ", 3),
        "kwo" to Pair("くぉ", 3),

        // gw-variants
        "gwa" to Pair("ぐぁ", 3),
        "gwi" to Pair("ぐぃ", 3),
        "gwu" to Pair("ぐぅ", 3),
        "gwe" to Pair("ぐぇ", 3),
        "gwo" to Pair("ぐぉ", 3),

        // sw-variants
        "swa" to Pair("すぁ", 3),
        "swi" to Pair("すぃ", 3),
        "swu" to Pair("すぅ", 3),
        "swe" to Pair("すぇ", 3),
        "swo" to Pair("すぉ", 3),

        // zw-variants
        "zwa" to Pair("ずぁ", 3),
        "zwi" to Pair("ずぃ", 3),
        "zwu" to Pair("ずぅ", 3),
        "zwe" to Pair("ずぇ", 3),
        "zwo" to Pair("ずぉ", 3),

        // xtsu / ltsu variants
        "xtu" to Pair("っ", 3),
        "xtsu" to Pair("っ", 4),
        "ltu" to Pair("っ", 3),
        "ltsu" to Pair("っ", 4)
    )

    private val maxKeyLength = romajiToKana.keys.maxOf { it.length }
    private val validPrefixes: Set<String> =
        romajiToKana.keys.flatMap { key -> (1..key.length).map { key.substring(0, it) } }.toSet()

    /**
     * @return Pair( toShow, toDelete )
     *   - toShow: 新たに“追加”表示する文字列
     *   - toDelete: 画面上で“直前に”消すべき文字数
     */
    fun handleKeyEvent(event: KeyEvent): Pair<String, Int> {

        val unicode = when (event.keyCode) {
            KeyEvent.KEYCODE_COMMA -> {
                '、'.code
            }

            KeyEvent.KEYCODE_PERIOD -> {
                '。'.code
            }

            KeyEvent.KEYCODE_BACKSLASH -> {
                '￥'.code
            }

            KeyEvent.KEYCODE_LEFT_BRACKET -> {
                '「'.code
            }

            KeyEvent.KEYCODE_RIGHT_BRACKET -> {
                '」'.code
            }

            KeyEvent.KEYCODE_SEMICOLON -> {
                '；'.code
            }

            KeyEvent.KEYCODE_APOSTROPHE -> {
                '\u2019'.code
            }

            KeyEvent.KEYCODE_MINUS -> {
                'ー'.code
            }

            KeyEvent.KEYCODE_EQUALS -> {
                '＝'.code
            }

            KeyEvent.KEYCODE_1 -> {
                '１'.code
            }

            KeyEvent.KEYCODE_2 -> {
                '２'.code
            }

            KeyEvent.KEYCODE_3 -> {
                '３'.code
            }

            KeyEvent.KEYCODE_4 -> {
                '４'.code
            }

            KeyEvent.KEYCODE_5 -> {
                '５'.code
            }

            KeyEvent.KEYCODE_6 -> {
                '６'.code
            }

            KeyEvent.KEYCODE_7 -> {
                '７'.code
            }

            KeyEvent.KEYCODE_8 -> {
                '８'.code
            }

            KeyEvent.KEYCODE_9 -> {
                '９'.code
            }

            KeyEvent.KEYCODE_0 -> {
                '０'.code
            }

            KeyEvent.KEYCODE_GRAVE -> {
                '｀'.code
            }

            else -> {
                event.unicodeChar
            }
        }

        if (unicode == 0) return Pair("", 0)

        val c = unicode.toChar().lowercaseChar()

        // ────────── 1) 英字以外は確定 ──────────
        if (c !in 'a'..'z') {
            // ① buffer に残っている未確定ローマ字を確定して surface に積む
            if (buffer.isNotEmpty()) {
                surface.append(buffer.toString())
                buffer.clear()
            }
            // ② この文字を確定して画面に表示
            surface.append(c)
            return Pair(c.toString(), 0)
        }

        // ────────── 2) 英字はまず buffer に貯める ──────────
        buffer.append(c)

        // ────────── 3) マッピング確定チェック ──────────
        for (len in maxKeyLength downTo 1) {
            if (buffer.length >= len) {
                val tail = buffer.takeLast(len).toString()
                // 「'n' 一文字だけ」は nn のためにスキップ
                if (tail == "n" && buffer.length == 1) continue
                val mapping = romajiToKana[tail]
                if (mapping != null) {
                    val (kana, consume) = mapping
                    // ① 画面上の「未確定分」を消す
                    //    → 前回 buffer の内容(長さ=consume) を消す
                    //    （consume が 2 なら "ka" の 'k','a'分を2文字消す）
                    val toDelete = (consume - 1).coerceAtLeast(0)
                    // ② surface に確定かなを追加
                    surface.append(kana)

                    // ③ buffer をクリア
                    buffer.clear()
                    when (tail) {
                        "qq", "vv", "ww", "ll", "xx", "kk", "gg", "ss", "zz", "jj", "tt", "dd", "hh", "ff", "bb", "pp", "mm", "yy", "rr", "cc" -> {
                            val charToAdd = tail[0]
                            buffer.append(charToAdd)
                            return Pair("$kana$charToAdd", toDelete)
                        }

                        "tch" -> {
                            buffer.append("ch")
                            return Pair("${kana}ch", toDelete)
                        }
                    }
                    return Pair(kana, toDelete)
                }
            }
        }

        // ────────── 4) プレフィックスマッチのみ（未確定） ──────────
        // たとえば "s" → "sh" → ここに入るa
        if (buffer.toString() in validPrefixes) {
            val str = buffer.toString()
            // 前回表示した str.dropLast(1) を消す
            val toDelete = buffer.length - 1
            return Pair(str, toDelete)
        }

        // ────────── 5) それ以外（プレフィックスにも乗らない） ──────────
        // たとえば "nk" のように、2文字以上・マップ外
        if (buffer.length >= 2) {
            val str = buffer.toString()
            val toDelete = buffer.length - 1
            // buffer の先頭以外を画面に残して、先頭だけ確定とみなすなら……
            // surface.append(str.dropLast(1))
            // buffer.delete(0, str.length-1)
            return Pair(str, toDelete)
        }

        // ────────── 6) 最後の手段：1文字だけ確定扱い ──────────
        val str = buffer.toString()
        surface.append(str)
        buffer.clear()
        return Pair(str, 0)
    }

    fun handleDelete(event: KeyEvent): Pair<String, Int> {
        if (event.keyCode == KeyEvent.KEYCODE_DEL) {
            // 0-a) If you’re in the middle of composing (buffer not empty),
            // remove the last romaji char and tell the UI to delete one char
            if (buffer.isNotEmpty()) {
                buffer.deleteCharAt(buffer.length - 1)
                // Show the remaining buffer as “toShow” so the UI can redisplay it,
                // and delete just 1 character on screen
                return Pair(buffer.toString(), 1)
            }
            // 0-b) If buffer is empty but surface has committed kana/text,
            // remove the last committed char from surface and delete it on screen
            if (surface.isNotEmpty()) {
                surface.deleteCharAt(surface.length - 1)
                return Pair("", 1)
            }
            // Nothing to delete
            return Pair("", 0)
        }
        return Pair("", 0)
    }

    /**
     * 与えられたローマ字文字列をひらがな／記号にまとめて変換します。
     * 例:
     *   convert("a")   == "あ"
     *   convert("shi") == "し"
     *   convert("konnichiwa") == "こんにちは"
     */
    /**
     * 与えられたローマ字文字列をひらがな／記号にまとめて変換します。
     * 二重子音（qq,vv,ww,…,tch など）は「っ+子音」に。
     */
    fun convert(text: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < text.length) {

            when (text[i]) {
                '.' -> {
                    result.append('.')
                    i++
                    continue
                }

                ',' -> {
                    result.append(',')
                    i++
                    continue
                }
            }

            var matched = false

            // できるだけ長いキーからマッチを試みる
            for (len in maxKeyLength downTo 1) {
                if (i + len > text.length) continue
                val segment = text.substring(i, i + len)
                val mapping = romajiToKana[segment]
                if (mapping != null) {
                    val (kana, consume) = mapping

                    // ─── 促音（っ）が返ってきたら、末尾の子音を付け足す ───
                    if (kana == "っ" && segment.length >= 2) {
                        // segment[0] が子音なので、"っ"+子音 を出力
                        result.append("っ").append(segment[0])
                    } else {
                        // 普通のマッピング結果
                        result.append(kana)
                    }

                    i += consume
                    matched = true
                    break
                }
            }

            if (!matched) {
                // マップにない文字はそのまま
                result.append(text[i])
                i++
            }
        }

        return result.toString()
    }

    fun clear() {
        buffer.clear()
        surface.clear()
    }
}
