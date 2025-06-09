import android.view.KeyEvent

class RomajiKanaConverter {
    private val buffer = StringBuilder()

    /**
     * ローマ字からひらがなへの変換
     *
     * https://github.com/google/mozc/blob/master/src/data/preedit/romanji-hiragana.tsv
     *
     * **/
    private val romajiToKana: Map<String, Pair<String, Int>> = mapOf(
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
        "va" to Pair("ゔぁ", 2),
        "vi" to Pair("ゔぃ", 2),
        "vu" to Pair("ゔ", 2),
        "ve" to Pair("ゔぇ", 2),
        "vo" to Pair("ゔぉ", 2),
        "ka" to Pair("か", 2),
        "ca" to Pair("か", 2),
        "ki" to Pair("き", 2),
        "ku" to Pair("く", 2),
        "cu" to Pair("く", 2),
        "ke" to Pair("け", 2),
        "ko" to Pair("こ", 2),
        "co" to Pair("こ", 2),
        "kya" to Pair("きゃ", 3),
        "kyi" to Pair("きぃ", 3),
        "kyu" to Pair("きゅ", 3),
        "kye" to Pair("きぇ", 3),
        "kyo" to Pair("きょ", 3),
        "kwa" to Pair("くぁ", 3),
        "ga" to Pair("が", 2),
        "gi" to Pair("ぎ", 2),
        "gu" to Pair("ぐ", 2),
        "ge" to Pair("げ", 2),
        "go" to Pair("ご", 2),
        "gya" to Pair("ぎゃ", 3),
        "gyi" to Pair("ぎぃ", 3),
        "gyu" to Pair("ぎゅ", 3),
        "gye" to Pair("ぎぇ", 3),
        "gyo" to Pair("ぎょ", 3),
        "gwa" to Pair("ぐぁ", 3),
        "sa" to Pair("さ", 2),
        "si" to Pair("し", 2),
        "shi" to Pair("し", 3),
        "su" to Pair("す", 2),
        "se" to Pair("せ", 2),
        "so" to Pair("そ", 2),
        "sya" to Pair("しゃ", 3),
        "sha" to Pair("しゃ", 3),
        "syi" to Pair("しぃ", 3),
        "syu" to Pair("しゅ", 3),
        "shu" to Pair("しゅ", 3),
        "sye" to Pair("しぇ", 3),
        "she" to Pair("しぇ", 3),
        "syo" to Pair("しょ", 3),
        "sho" to Pair("しょ", 3),
        "za" to Pair("ざ", 2),
        "zi" to Pair("じ", 2),
        "ji" to Pair("じ", 2),
        "zu" to Pair("ず", 2),
        "ze" to Pair("ぜ", 2),
        "zo" to Pair("ぞ", 2),
        "zya" to Pair("じゃ", 3),
        "ja" to Pair("じゃ", 2),
        "zyi" to Pair("じぃ", 3),
        "jyi" to Pair("じぃ", 3),
        "zyu" to Pair("じゅ", 3),
        "ju" to Pair("じゅ", 2),
        "zye" to Pair("じぇ", 3),
        "je" to Pair("じぇ", 2),
        "zyo" to Pair("じょ", 3),
        "jo" to Pair("じょ", 2),
        "ta" to Pair("た", 2),
        "ti" to Pair("ち", 2),
        "chi" to Pair("ち", 3),
        "tu" to Pair("つ", 2),
        "tsu" to Pair("つ", 3),
        "te" to Pair("て", 2),
        "to" to Pair("と", 2),
        "tya" to Pair("ちゃ", 3),
        "cha" to Pair("ちゃ", 3),
        "tyi" to Pair("ちぃ", 3),
        "cyi" to Pair("ちぃ", 3),
        "tyu" to Pair("ちゅ", 3),
        "chu" to Pair("ちゅ", 3),
        "tye" to Pair("ちぇ", 3),
        "che" to Pair("ちぇ", 3),
        "tyo" to Pair("ちょ", 3),
        "cho" to Pair("ちょ", 3),
        "twu" to Pair("とぅ", 3),
        "da" to Pair("だ", 2),
        "di" to Pair("ぢ", 2),
        "du" to Pair("づ", 2),
        "de" to Pair("で", 2),
        "do" to Pair("ど", 2),
        "dya" to Pair("ぢゃ", 3),
        "dyi" to Pair("ぢぃ", 3),
        "dyu" to Pair("ぢゅ", 3),
        "dye" to Pair("ぢぇ", 3),
        "dyo" to Pair("ぢょ", 3),
        "dwu" to Pair("どぅ", 3),
        "dha" to Pair("でゃ", 3),
        "dhi" to Pair("でぃ", 3),
        "dhu" to Pair("でゅ", 3),
        "dhe" to Pair("でぇ", 3),
        "dho" to Pair("でょ", 3),
        "ts" to Pair("つ", 2),
        "tsa" to Pair("つぁ", 3),
        "tsi" to Pair("つぃ", 3),
        "tse" to Pair("つぇ", 3),
        "tso" to Pair("つぉ", 3),
        "th" to Pair("て", 2),
        "tha" to Pair("てゃ", 3),
        "thi" to Pair("てぃ", 3),
        "thu" to Pair("てゅ", 3),
        "the" to Pair("てぇ", 3),
        "tho" to Pair("てょ", 3),
        "nn" to Pair("ん", 2),
        "na" to Pair("な", 2),
        "ni" to Pair("に", 2),
        "nu" to Pair("ぬ", 2),
        "ne" to Pair("ね", 2),
        "no" to Pair("の", 2),
        "nya" to Pair("にゃ", 3),
        "nyi" to Pair("にぃ", 3),
        "nyu" to Pair("にゅ", 3),
        "nye" to Pair("にぇ", 3),
        "nyo" to Pair("にょ", 3),
        "ha" to Pair("は", 2),
        "hi" to Pair("ひ", 2),
        "fu" to Pair("ふ", 2),
        "hu" to Pair("ふ", 2),
        "he" to Pair("へ", 2),
        "ho" to Pair("ほ", 2),
        "hya" to Pair("ひゃ", 3),
        "hyi" to Pair("ひぃ", 3),
        "hyu" to Pair("ひゅ", 3),
        "hye" to Pair("ひぇ", 3),
        "hyo" to Pair("ひょ", 3),
        "ba" to Pair("ば", 2),
        "bi" to Pair("び", 2),
        "bu" to Pair("ぶ", 2),
        "be" to Pair("べ", 2),
        "bo" to Pair("ぼ", 2),
        "bya" to Pair("びゃ", 3),
        "byi" to Pair("びぃ", 3),
        "byu" to Pair("びゅ", 3),
        "bye" to Pair("びぇ", 3),
        "byo" to Pair("びょ", 3),
        "pa" to Pair("ぱ", 2),
        "pi" to Pair("ぴ", 2),
        "pu" to Pair("ぷ", 2),
        "pe" to Pair("ぺ", 2),
        "po" to Pair("ぽ", 2),
        "pya" to Pair("ぴゃ", 3),
        "pyi" to Pair("ぴぃ", 3),
        "pyu" to Pair("ぴゅ", 3),
        "pye" to Pair("ぴぇ", 3),
        "pyo" to Pair("ぴょ", 3),
        "fa" to Pair("ふぁ", 2),
        "fi" to Pair("ふぃ", 2),
        "fe" to Pair("ふぇ", 2),
        "fo" to Pair("ふぉ", 2),
        "fya" to Pair("ふゃ", 3),
        "fyi" to Pair("ふぃ", 3),
        "fyu" to Pair("ふゅ", 3),
        "fye" to Pair("ふぇ", 3),
        "fyo" to Pair("ふょ", 3),
        "ma" to Pair("ま", 2),
        "mi" to Pair("み", 2),
        "mu" to Pair("む", 2),
        "me" to Pair("め", 2),
        "mo" to Pair("も", 2),
        "mya" to Pair("みゃ", 3),
        "myi" to Pair("みぃ", 3),
        "myu" to Pair("みゅ", 3),
        "mye" to Pair("みぇ", 3),
        "myo" to Pair("みょ", 3),
        "ya" to Pair("や", 2),
        "yu" to Pair("ゆ", 2),
        "yo" to Pair("よ", 2),
        "ya" to Pair("や", 2),
        "ra" to Pair("ら", 2),
        "ri" to Pair("り", 2),
        "ru" to Pair("る", 2),
        "re" to Pair("れ", 2),
        "ro" to Pair("ろ", 2),
        "rya" to Pair("りゃ", 3),
        "ryi" to Pair("りぃ", 3),
        "ryu" to Pair("りゅ", 3),
        "rye" to Pair("りぇ", 3),
        "ryo" to Pair("りょ", 3),
        "wa" to Pair("わ", 2),
        "wi" to Pair("うぃ", 2),
        "we" to Pair("うぇ", 2),
        "wo" to Pair("を", 2),
        "xtu" to Pair("っ", 3),
        "ltu" to Pair("っ", 3),
        "qq" to Pair("っq", 2),
        "vv" to Pair("っv", 2),
        "ll" to Pair("っl", 2),
        "xx" to Pair("っx", 2),
        "kk" to Pair("っk", 2),
        "gg" to Pair("っg", 2),
        "ss" to Pair("っs", 2),
        "zz" to Pair("っz", 2),
        "jj" to Pair("っj", 2),
        "tt" to Pair("っt", 2),
        "tch" to Pair("っch", 3),
        "dd" to Pair("っd", 2),
        "hh" to Pair("っh", 2),
        "ff" to Pair("っf", 2),
        "bb" to Pair("っb", 2),
        "pp" to Pair("っp", 2),
        "mm" to Pair("っm", 2),
        "yy" to Pair("っy", 2),
        "rr" to Pair("っr", 2),
        "ww" to Pair("っw", 2),
        "cc" to Pair("っc", 2),
        "www" to Pair("www", 3),
        "a" to Pair("あ", 1),
        "i" to Pair("い", 1),
        "u" to Pair("う", 1),
        "e" to Pair("え", 1),
        "o" to Pair("お", 1),
    )

    private val maxKeyLength = romajiToKana.keys.maxOf { it.length }
    private val validPrefixes: Set<String> = romajiToKana.keys
        .flatMap { key -> (1..key.length).map { key.substring(0, it) } }
        .toSet()

    /**
     * 押下イベントに応じて「出力すべき 文字列」を返す。
     * 出力不要なら null を返す。
     */
    fun inputKeyEvent(event: KeyEvent): Pair<String, Int>? {
        val unicode = event.unicodeChar
        if (unicode == 0) return null

        val c = unicode.toChar().lowercaseChar()
        if (c !in 'a'..'z') {
            if (buffer.isNotEmpty()) {
                val out = buffer[0].toString()
                buffer.deleteCharAt(0)
                return Pair(out, 1)
            }
            return Pair(c.toString(), 1)
        }

        buffer.append(c)
        if (buffer.length == 1 && buffer.toString() !in validPrefixes) {
            val out = buffer[0].toString()
            buffer.clear()
            return Pair(out, 1)
        }

        for (len in maxKeyLength downTo 1) {
            if (buffer.length >= len) {
                val tail = buffer.takeLast(len).toString()
                romajiToKana[tail]?.let { (kana, consume) ->
                    buffer.setLength(buffer.length - consume)
                    return Pair(kana, consume)
                }
            }
        }

        if (buffer.toString() !in validPrefixes) {
            val out = buffer[0].toString()
            buffer.deleteCharAt(0)
            return Pair(out, 1)
        }

        return null
    }
}
