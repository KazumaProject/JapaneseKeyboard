package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import android.view.KeyEvent

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
