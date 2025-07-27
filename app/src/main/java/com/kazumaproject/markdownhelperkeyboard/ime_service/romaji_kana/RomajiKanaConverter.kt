package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import android.view.KeyEvent
import timber.log.Timber

class RomajiKanaConverter(private val romajiToKana: Map<String, Pair<String, Int>>) {
    private val buffer = StringBuilder()
    private val surface = StringBuilder()

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
            // ★★★ 最重要の変更点 ★★★
            // 1. まず「n」の特別ルールをチェックする
            //    - 次の文字が存在し、それが「a,i,u,e,o,y,n」のいずれでもない場合
            if (text[i] == 'n' && i + 1 < text.length && text[i + 1] !in "aiueoyn") {
                // 先に「ん」を追加し、インデックスを1つ進める
                result.append("ん")
                i++
                // このループは終了し、次の文字（子音や記号など）の処理に移る
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

                    // 促音の処理
                    if (kana == "っ" && segment.length >= 2) {
                        result.append("っ").append(segment[0])
                    } else {
                        result.append(kana)
                    }

                    i += consume
                    matched = true
                    break // マッチしたので内側のループを抜ける
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

    fun clear() {
        buffer.clear()
        surface.clear()
    }
}
