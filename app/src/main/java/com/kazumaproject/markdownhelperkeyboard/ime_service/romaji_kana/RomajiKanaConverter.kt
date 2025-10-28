package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import android.view.KeyEvent
import com.kazumaproject.convertFullWidthToHalfWidth
import timber.log.Timber

class RomajiKanaConverter(private val romajiToKana: Map<String, Pair<String, Int>>) {
    private val buffer = StringBuilder()
    private val surface = StringBuilder()

    private val maxKeyLength = romajiToKana.keys.maxOf { it.length }
    private val validPrefixes: Set<String> =
        romajiToKana.keys.flatMap { key -> (1..key.length).map { key.substring(0, it) } }.toSet()

    init {

    }

    /**
     * かなからローマ字への逆引きマップ。
     * 初期化時に一度だけ生成されるように `lazy` を使用します。
     * 例: { "あ" -> "a", "し" -> "shi" }
     */
    private val kanaToRomaji: Map<String, String> by lazy {
        romajiToKana.entries
            // 促音(っ)のマッピングは文脈に依存するため、逆引きマップからは除外する
            .filterNot { it.value.first == "っ" }
            .groupBy(
                keySelector = { it.value.first }, // keyは "か" などの「かな」
                valueTransform = { it.key }       // valueは "ka" などの「ローマ字」
            )
            .mapValues { (_, romajiList) ->
                // 同じかなに複数のローマ字が割り当てられている場合 (例: し -> shi, si)、
                // 最も短いものを代表として選択する。
                romajiList.minByOrNull { it.length } ?: ""
            }
    }

    /**
     * ひらがな（またはカタカナ）の文字列をローマ字に変換します。
     *
     * @param kanaText ひらがな、カタカナ、記号などを含む変換対象の文字列。
     * @return 変換後のローマ字文字列。
     *
     * 機能:
     * - 「がっこう」 -> "gakkou" (促音 'っ' の処理)
     * - 「ラーメン」 -> "raamen" (長音 'ー' の処理)
     * - 「こんにちは」 -> "konnichiha" (基本的なかな変換)
     * - 変換テーブルにない文字（漢字など）はそのまま出力します。
     */
    fun hiraganaToRomaji(kanaText: String): String {
        val result = StringBuilder()
        var i = 0
        // 変換マップのかなの最大長を取得（例：「きゃ」は2文字）
        val maxKanaLength = kanaToRomaji.keys.maxOfOrNull { it.length } ?: 1

        while (i < kanaText.length) {
            val char = kanaText[i]

            // 1. 促音 'っ' (ひらがな) または 'ッ' (カタカナ) の処理
            if (char == 'っ' || char == 'ッ') {
                if (i + 1 < kanaText.length) {
                    var nextRomaji: String? = null
                    // 次に来る文字（群）に一致するローマ字を探す（最長一致）
                    for (len in maxKanaLength downTo 1) {
                        if (i + 1 + len > kanaText.length) continue
                        val nextKana = kanaText.substring(i + 1, i + 1 + len)
                        if (kanaToRomaji.containsKey(nextKana)) {
                            nextRomaji = kanaToRomaji[nextKana]
                            break
                        }
                    }

                    if (nextRomaji != null && nextRomaji.isNotEmpty()) {
                        // 次のかなのローマ字表記の最初の子音を追加して、'っ'をスキップ
                        result.append(nextRomaji[0])
                        i++
                        continue
                    }
                }
            }

            // 2. 長音 'ー' の処理
            if (char == 'ー') {
                if (result.isNotEmpty()) {
                    val lastCharInResult = result.last().lowercaseChar()
                    // 直前の文字が母音なら、その母音を繰り返す
                    if (lastCharInResult in "aiueo") {
                        result.append(lastCharInResult)
                        i++
                        continue
                    }
                }
            }

            // 3. 通常のかな・記号の変換（最長一致）
            var matched = false
            for (len in maxKanaLength downTo 1) {
                if (i + len > kanaText.length) continue
                val segment = kanaText.substring(i, i + len)
                kanaToRomaji[segment]?.let { romaji ->
                    result.append(romaji)
                    i += len
                    matched = true
                }
                if (matched) break
            }

            // 4. マップにない文字（漢字など）の処理
            if (!matched) {
                result.append(kanaText[i])
                i++
            }
        }
        return result.toString()
    }

    /**
     * @return Pair( toShow, toDelete )
     *   - toShow: 新たに“追加”表示する文字列
     *   - toDelete: 画面上で“直前に”消すべき文字数
     */
    fun handleKeyEvent(event: KeyEvent): Pair<String, Int> {

        val unicode = when (event.keyCode) {
            KeyEvent.KEYCODE_COMMA -> '、'.code
            KeyEvent.KEYCODE_PERIOD -> '。'.code
            KeyEvent.KEYCODE_BACKSLASH -> '￥'.code
            KeyEvent.KEYCODE_LEFT_BRACKET -> '「'.code
            KeyEvent.KEYCODE_RIGHT_BRACKET -> '」'.code
            KeyEvent.KEYCODE_SEMICOLON -> '；'.code
            KeyEvent.KEYCODE_APOSTROPHE -> '\u2019'.code
            KeyEvent.KEYCODE_MINUS -> 'ー'.code
            KeyEvent.KEYCODE_EQUALS -> '＝'.code
            KeyEvent.KEYCODE_1 -> '１'.code
            KeyEvent.KEYCODE_2 -> '２'.code
            KeyEvent.KEYCODE_3 -> '３'.code
            KeyEvent.KEYCODE_4 -> '４'.code
            KeyEvent.KEYCODE_5 -> '５'.code
            KeyEvent.KEYCODE_6 -> '６'.code
            KeyEvent.KEYCODE_7 -> '７'.code
            KeyEvent.KEYCODE_8 -> '８'.code
            KeyEvent.KEYCODE_9 -> '９'.code
            KeyEvent.KEYCODE_0 -> '０'.code
            KeyEvent.KEYCODE_GRAVE -> '｀'.code
            else -> event.unicodeChar
        }

        if (unicode == 0) return Pair("", 0)

        val c = unicode.toChar().lowercaseChar()

        // ────────── 1) 英字以外は確定 ──────────
        if (c !in 'a'..'z') {
            // バッファに'n'が残っている状態で記号などが入力された場合の処理
            if (buffer.isNotEmpty()) {
                // バッファが"n"なら"ん"に変換して確定させる
                val toCommit = if (buffer.toString() == "n") "ん" else buffer.toString()
                val toDelete = buffer.length
                surface.append(toCommit)
                buffer.clear()
                // 確定した文字と、今回入力された記号を両方表示
                return Pair("$toCommit$c", toDelete)
            }
            // バッファが空なら、入力された記号をそのまま表示
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
                    val toDelete = (consume - 1).coerceAtLeast(0)
                    surface.append(kana)
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
        if (buffer.toString() in validPrefixes) {
            val str = buffer.toString()
            val toDelete = buffer.length - 1
            return Pair(str, toDelete)
        }

        // ────────── 5) それ以外（プレフィックスにも乗らない） ──────────
        if (buffer.length >= 2) {
            val str = buffer.toString()

            // 「n」の次に子音が入力された場合の処理
            val firstChar = str[0]
            val secondChar = str[1]
            if (firstChar == 'n' && secondChar !in "aiueoyn") {
                surface.append("ん")
                buffer.delete(0, 1)
                // 画面上の"n"(1文字)を削除し、"ん"＋子音を表示する
                return Pair("ん${buffer.toString()}", 1)
            }

            val toDelete = buffer.length - 1
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
     * 与えられたローマ字文字列をひらがな／記号にまとめて変換します。
     * 二重子音（qq,vv,ww,…,tch など）は「っ+子音」に。
     * 与えられたローマ字文字列をひらがな／記号にまとめて変換します。
     * 「n」の後に母音(a,i,u,e,o)や「y」「n」が続かない場合は「ん」として処理します。
     */
    fun convert(text: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < text.length) {
            val currentChar = text[i]

            // 1. まず「n」の特別ルールをチェックする
            if (currentChar == 'n' && i + 1 < text.length && text[i + 1] !in "aiueoyn") {
                result.append("ん")
                i++
                continue
            }

            // ★★★ 変更点①：促音（「っ」）の特別ルールを追加 ★★★
            //    - 次の文字が存在し、現在の文字と同じ子音である場合（'n'を除く）
            if (i + 1 < text.length &&
                currentChar == text[i + 1] &&
                currentChar in "kstcpbdfghjmqrvwz"
            ) { // 促音になりうる子音を指定
                result.append("っ")
                i++ // ★重要★ インデックスを1つだけ進める
                continue
            }

            var matched = false
            // 2. 上記のルールに当てはまらない場合、通常通りもっとも長い組み合わせから探す
            for (len in maxKeyLength downTo 1) {
                if (i + len > text.length) continue

                val segment = text.substring(i, i + len)
                val mapping = romajiToKana[segment]

                if (mapping != null) {
                    val (kana, consume) = mapping

                    // ★★★ 変更点②：複雑な促音処理を削除し、単純化 ★★★
                    result.append(kana)

                    i += consume
                    matched = true
                    break
                }
            }

            // 3. マッチしなかった文字はそのまま追加
            if (!matched) {
                result.append(text[i])
                i++
            }
        }
        return result.toString()
    }

    /**
     * romajiToKanaのキーをすべて半角に変換したキャッシュ用のマップ。
     * lazyを使っているので、最初にアクセスされた時に一度だけ変換処理が実行される。
     */
    private val halfWidthRomajiToKana: Map<String, Pair<String, Int>> by lazy {
        romajiToKana.mapKeys { (key, _) -> key.convertFullWidthToHalfWidth() }
    }

    /**
     * Converts a given Romaji string into Hiragana/symbols based on a set of rules.
     *
     * This function adheres to the following conversion logic:
     *
     * 1.  **Longest Match First**: Prioritizes longer Romaji combinations (e.g., "shi" over "s").
     * 2.  **Sokuon (っ)**: Handles double consonants like "kk", "tt", "pp" by converting them to a "っ" followed by the next character's conversion (e.g., "chotto" -> "ちょっと").
     * 3.  **Hatsuon (ん)**: Treats an 'n' as "ん" if it is not followed by a vowel (a, i, u, e, o), 'y', or another 'n' (e.g., "kantan" -> "かんたん").
     * 4.  **Width Insensitive**: Processes both full-width and half-width Romaji characters by normalizing them to half-width internally.
     *
     * @param text The Romaji string to be converted.
     * @return The resulting Hiragana/symbol string.
     *
     * Example Usage:
     * convertCustomLayout("konnichiwa") // returns "こんにちは"
     * convertCustomLayout("chotto")     // returns "ちょっと"
     * convertCustomLayout("kantan")     // returns "かんたん"
     * convertCustomLayout("ｇｒｅａｔ")   // returns "ぐれあt" (assuming "g" and "r" are mapped)
     */
    fun convertCustomLayout(text: String): String {
        val result = StringBuilder()
        var i = 0
        val normalizedText = text.convertFullWidthToHalfWidth() // Normalize the entire string once

        while (i < normalizedText.length) {
            val currentChar = normalizedText[i]

            // Rule 1: Special handling for 'n' (Hatsuon)
            // If 'n' is the last character or not followed by a vowel/y/n.
            if (currentChar == 'n') {
                if (i + 1 >= normalizedText.length || normalizedText[i + 1] !in "aiueoyn") {
                    result.append("ん")
                    i++
                    continue
                }
            }

            // Rule 2: Special handling for double consonants (Sokuon)
            // Check if the current character is a consonant and is followed by the same one.
            if (i + 1 < normalizedText.length &&
                currentChar == normalizedText[i + 1] &&
                currentChar in "kstcpbdfghjmqrvwz" // Consonants that can form a sokuon
            ) {
                result.append("っ")
                i++ // Consume one of the double consonants, the loop will handle the next one
                continue
            }

            // Rule 3: Longest match lookup
            var matched = false
            // Iterate from the longest possible key length down to 1
            for (len in maxKeyLength downTo 1) {
                if (i + len > normalizedText.length) continue

                val segment = normalizedText.substring(i, i + len)
                val mapping = halfWidthRomajiToKana[segment] // Use the cached, half-width map

                if (mapping != null) {
                    val (kana, consume) = mapping
                    result.append(kana)
                    i += consume
                    matched = true
                    break // Exit the inner loop once the longest match is found
                }
            }

            // If no match was found in the map, append the character as is
            if (!matched) {
                result.append(normalizedText[i])
                i++
            }
        }
        return result.toString()
    }

    /**
     * QWERTYレイアウトからの入力を想定し、ローマ字文字列をひらがな／記号に変換します。
     * 半角の `[` と `]` は変換せずにそのまま出力します。
     *
     * @param text 変換対象の文字列。
     * @return 変換後の文字列。
     */
    fun convertQWERTY(text: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < text.length) {
            val currentChar = text[i]

            // 1. '[' または ']' の場合は、変換せずにそのまま追加し、次の文字へ進む
            if (currentChar == '[' || currentChar == ']') {
                result.append(currentChar)
                i++
                continue
            }

            // ★★★ この関数の中核的な変更点 ★★★
            // 2. 促音（「っ」）のルールを追加
            //    - 次の文字が存在し、現在の文字と同じ子音である場合（'n'を除く）
            if (i + 1 < text.length &&
                currentChar == text[i + 1] &&
                currentChar in "kstcpbdfghljmqrvwxyz"
            ) { // 促音になりうる子音を指定
                result.append("っ")
                i++ // ★重要★ インデックスを1つだけ進める
                continue
            }

            // 3. 「n」の特別ルールをチェックする
            //    - 次の文字が存在し、それが「a,i,u,e,o,y,n」のいずれでもない場合
            if (currentChar == 'n' && i + 1 < text.length && text[i + 1] !in "aiueoyn") {
                result.append("ん")
                i++
                continue
            }

            var matched = false
            // 4. 上記のルールに当てはまらない場合、通常通りもっとも長い組み合わせから探す
            for (len in maxKeyLength downTo 1) {
                if (i + len > text.length) continue

                val segment = text.substring(i, i + len)
                // 'ss'のような組み合わせはマッピングテーブルから削除するか、このロジックで処理されるので不要
                val mapping = romajiToKana[segment]

                if (mapping != null) {
                    val (kana, consume) = mapping
                    result.append(kana)
                    i += consume
                    matched = true
                    break
                }
            }

            // 5. マッチしなかった文字はそのまま追加
            if (!matched) {
                result.append(text[i])
                i++
            }
        }
        return result.toString()
    }

    /**
     * QWERTYレイアウトからの入力を想定し、ローマ字文字列をひらがな／記号に変換します。
     * 半角の `[` と `]` は変換せずにそのまま出力します。
     *
     * @param text 変換対象の文字列。
     * @return 変換後の文字列。
     */

    fun convertQWERTYZenkaku(text: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < text.length) {
            val currentChar = text[i]

            // 1. '[' または ']' の場合は、変換せずにそのまま追加し、次の文字へ進む
            if (currentChar == '[' || currentChar == ']') {
                result.append(currentChar)
                i++
                continue
            }

            // ★★★ この関数の中核的な変更点 ★★★
            // 2. 促音（「っ」）のルールを追加
            //    - 次の文字が存在し、現在の文字と同じ子音である場合（'n'を除く）
            if (i + 1 < text.length &&
                currentChar == text[i + 1] &&
                currentChar in "ｋｓｔｃｐｂｄｆｇｈｌｊｍｑｒｖｗｘｙｚ"
            ) { // 促音になりうる子音を指定
                result.append("っ")
                i++ // ★重要★ インデックスを1つだけ進める
                continue
            }

            // 3. 「n」の特別ルールをチェックする
            //    - 次の文字が存在し、それが「a,i,u,e,o,y,n」のいずれでもない場合
            if (currentChar == 'ｎ' && i + 1 < text.length && text[i + 1] !in "ａｉｕｅｏｙｎ") {
                result.append("ん")
                i++
                continue
            }

            var matched = false
            // 4. 上記のルールに当てはまらない場合、通常通りもっとも長い組み合わせから探す
            for (len in maxKeyLength downTo 1) {
                if (i + len > text.length) continue

                val segment = text.substring(i, i + len)
                // 'ss'のような組み合わせはマッピングテーブルから削除するか、このロジックで処理されるので不要
                val mapping = romajiToKana[segment]

                if (mapping != null) {
                    val (kana, consume) = mapping
                    result.append(kana)
                    i += consume
                    matched = true
                    break
                }
            }

            // 5. マッチしなかった文字はそのまま追加
            if (!matched) {
                result.append(text[i])
                i++
            }
        }
        return result.toString()
    }

    /**
     * バッファに残っている未確定文字列を確定させ、UIに表示すべき文字列を返します。
     * 末尾の 'n' は 'ん' に変換します。
     * @return Pair( toShow, toDelete )
     */
    fun flush(string: String): Pair<String, Int> {
        Timber.d("Enter Key flush: $string")
        if (string.isEmpty()) {
            return Pair("", 0)
        }

        // バッファの末尾が "n" なら "ん" に変換し、そうでなければバッファの文字をそのまま確定
        val toCommit = if (string.endsWith("n")) {
            // "n" より前の部分と "ん" を結合する (例: "ろn" -> "ろ" + "ん")
            string.dropLast(1) + "ん"
        } else {
            string
        }

        // UI上で削除すべき文字数は、変換前のバッファ全体の文字数
        // 元のコードでは '1' に固定されていましたが、"ろn" (2文字) などを正しく置換するために、
        // 本来はこちらが意図した動作だと思われます。
        val toDelete = string.length

        // 確定した文字列と、削除すべき文字数を返す
        return Pair(toCommit, toDelete)
    }

    fun flushZenkaku(string: String): Pair<String, Int> {
        Timber.d("Enter Key flush: $string")
        if (string.isEmpty()) {
            return Pair("", 0)
        }

        // バッファの末尾が "n" なら "ん" に変換し、そうでなければバッファの文字をそのまま確定
        val toCommit = if (string.endsWith("ｎ")) {
            // "n" より前の部分と "ん" を結合する (例: "ろn" -> "ろ" + "ん")
            string.dropLast(1) + "ん"
        } else {
            string
        }

        // UI上で削除すべき文字数は、変換前のバッファ全体の文字数
        // 元のコードでは '1' に固定されていましたが、"ろn" (2文字) などを正しく置換するために、
        // 本来はこちらが意図した動作だと思われます。
        val toDelete = string.length

        // 確定した文字列と、削除すべき文字数を返す
        return Pair(toCommit, toDelete)
    }

    fun clear() {
        buffer.clear()
        surface.clear()
    }
}
