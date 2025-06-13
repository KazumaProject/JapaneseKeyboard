package com.kazumaproject.custom_keyboard.layout

import android.R
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyboardLayout

object KeyboardDefaultLayouts {

    //region ひらがなレイアウト
    // ▼▼▼ 変更 ▼▼▼ この関数のキー定義を正しいひらがなレイアウトに戻しました
    fun createHiraganaLayout(): KeyboardLayout {
        // --- キーの見た目（ラベル）を定義 ---
        val keys = listOf(
            // 0列目
            KeyData("", 0, 0, false),
            KeyData("", 1, 0, false),
            KeyData("モード", 2, 0, false, isSpecialKey = true),
            KeyData("", 3, 0, false),
            // 1-3列目 (ひらがなフリックキー)
            KeyData("あ", 0, 1, true), KeyData("か", 0, 2, true), KeyData("さ", 0, 3, true),
            KeyData("た", 1, 1, true), KeyData("な", 1, 2, true), KeyData("は", 1, 3, true),
            KeyData("ま", 2, 1, true), KeyData("や", 2, 2, true), KeyData("ら", 2, 3, true),
            KeyData("^_^", 3, 1, false), KeyData("わ", 3, 2, true), KeyData("、。?!", 3, 3, false),
            // 4列目（右端の特殊キー）
            KeyData(
                "Del",
                0,
                4,
                false,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = R.drawable.ic_input_delete
            ),
            KeyData("空白", 1, 4, false, isSpecialKey = true, rowSpan = 1),
            // Enter: ラベルを"Enter"にし、Android標準の改行アイコンを指定
            KeyData(
                "改行",
                2,
                4,
                false,
                isSpecialKey = true,
                rowSpan = 2,
                drawableResId = null
            )
        )

        // --- フリック文字マップを定義 (濁点・半濁点をリストで管理) ---
        val a = mapOf(
            FlickDirection.TAP to "あ",
            FlickDirection.UP_LEFT_FAR to "あ",
            FlickDirection.UP_LEFT to "い",
            FlickDirection.UP to "う",
            FlickDirection.UP_RIGHT to "え",
            FlickDirection.UP_RIGHT_FAR to "お",
            FlickDirection.DOWN to "小"
        )

        val small_a = mapOf(
            FlickDirection.TAP to "ぁ",
            FlickDirection.UP_LEFT_FAR to "ぁ",
            FlickDirection.UP_LEFT to "ぃ",
            FlickDirection.UP to "ぅ",
            FlickDirection.UP_RIGHT to "ぇ",
            FlickDirection.UP_RIGHT_FAR to "ぉ",
            FlickDirection.DOWN to "大"
        )
        val ka = mapOf(
            FlickDirection.TAP to "か",
            FlickDirection.UP_LEFT to "き",
            FlickDirection.UP to "く",
            FlickDirection.UP_RIGHT to "け",
            FlickDirection.UP_RIGHT_FAR to "こ",
            FlickDirection.DOWN to "゛"
        )
        val ga = mapOf(
            FlickDirection.TAP to "が",
            FlickDirection.UP_LEFT to "ぎ",
            FlickDirection.UP to "ぐ",
            FlickDirection.UP_RIGHT to "げ",
            FlickDirection.UP_RIGHT_FAR to "ご",
            FlickDirection.DOWN to "゛"
        )
        val sa = mapOf(
            FlickDirection.TAP to "さ",
            FlickDirection.UP_LEFT to "し",
            FlickDirection.UP to "す",
            FlickDirection.UP_RIGHT to "せ",
            FlickDirection.UP_RIGHT_FAR to "そ",
            FlickDirection.DOWN to "゛"
        )
        val za = mapOf(
            FlickDirection.TAP to "ざ",
            FlickDirection.UP_LEFT to "じ",
            FlickDirection.UP to "ず",
            FlickDirection.UP_RIGHT to "ぜ",
            FlickDirection.UP_RIGHT_FAR to "ぞ",
            FlickDirection.DOWN to "゛"
        )
        val ta = mapOf(
            FlickDirection.TAP to "た",
            FlickDirection.UP_LEFT to "ち",
            FlickDirection.UP to "つ",
            FlickDirection.UP_RIGHT to "て",
            FlickDirection.UP_RIGHT_FAR to "と",
            FlickDirection.DOWN to "゛"
        )
        val da = mapOf(
            FlickDirection.TAP to "だ",
            FlickDirection.UP_LEFT to "ぢ",
            FlickDirection.UP to "づ",
            FlickDirection.UP_RIGHT to "で",
            FlickDirection.UP_RIGHT_FAR to "ど",
            FlickDirection.DOWN to "゛"
        )
        val na = mapOf(
            FlickDirection.TAP to "な", FlickDirection.UP_LEFT to "に", FlickDirection.UP to "ぬ",
            FlickDirection.UP_RIGHT to "ね", FlickDirection.UP_RIGHT_FAR to "の"
        )
        val ha = mapOf(
            FlickDirection.TAP to "は",
            FlickDirection.UP_LEFT to "ひ",
            FlickDirection.UP to "ふ",
            FlickDirection.UP_RIGHT to "へ",
            FlickDirection.UP_RIGHT_FAR to "ほ",
            FlickDirection.DOWN to "゛゜"
        )
        val ba = mapOf(
            FlickDirection.TAP to "ば",
            FlickDirection.UP_LEFT to "び",
            FlickDirection.UP to "ぶ",
            FlickDirection.UP_RIGHT to "べ",
            FlickDirection.UP_RIGHT_FAR to "ぼ",
            FlickDirection.DOWN to "゜"
        )
        val pa = mapOf(
            FlickDirection.TAP to "ぱ",
            FlickDirection.UP_LEFT to "ぴ",
            FlickDirection.UP to "ぷ",
            FlickDirection.UP_RIGHT to "ぺ",
            FlickDirection.UP_RIGHT_FAR to "ぽ",
            FlickDirection.DOWN to "゛"
        )
        val ma = mapOf(
            FlickDirection.TAP to "ま", FlickDirection.UP_LEFT to "み", FlickDirection.UP to "む",
            FlickDirection.UP_RIGHT to "め", FlickDirection.UP_RIGHT_FAR to "も"
        )
        val ya = mapOf(
            FlickDirection.TAP to "や", FlickDirection.UP_LEFT to "ゃ", FlickDirection.UP to "ゆ",
            FlickDirection.UP_RIGHT to "ゅ", FlickDirection.UP_RIGHT_FAR to "よ"
        )
        val ra = mapOf(
            FlickDirection.TAP to "ら", FlickDirection.UP_LEFT to "り", FlickDirection.UP to "る",
            FlickDirection.UP_RIGHT to "れ", FlickDirection.UP_RIGHT_FAR to "ろ"
        )
        val wa = mapOf(
            FlickDirection.TAP to "わ", FlickDirection.UP_LEFT to "を", FlickDirection.UP to "ん",
            FlickDirection.UP_RIGHT to "ー"
        )
        val kuten = mapOf(
            FlickDirection.TAP to "、", FlickDirection.UP_LEFT to "。", FlickDirection.UP to "？",
            FlickDirection.UP_RIGHT to "！"
        )

        val flickMaps = mapOf(
            "あ" to listOf(a, small_a), "か" to listOf(ka, ga), "さ" to listOf(sa, za),
            "た" to listOf(ta, da), "な" to listOf(na), "は" to listOf(ha, ba, pa),
            "ま" to listOf(ma), "や" to listOf(ya), "ら" to listOf(ra), "わ" to listOf(wa),
            "、。?!" to listOf(kuten)
        )

        // ひらがなレイアウトは 5列 x 4行
        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion

    //region 英語レイアウト (この関数は問題ありません)
    fun createEnglishLayout(isUpperCase: Boolean): KeyboardLayout {
        val keys = listOf(
            // 特殊キー
            KeyData("モード", 0, 0, false, isSpecialKey = true),
            KeyData("a/A", 1, 0, false, isSpecialKey = true),
            KeyData("顔文字", 2, 0, false, isSpecialKey = true),
            KeyData("記号", 3, 0, false, isSpecialKey = true),

            // 文字キー
            KeyData("ABC", 0, 1, true), KeyData("DEF", 0, 2, true), KeyData("GHI", 0, 3, true),
            KeyData("JKL", 1, 1, true), KeyData("MNO", 1, 2, true), KeyData("PQRS", 1, 3, true),
            KeyData("TUV", 2, 1, true), KeyData("WXYZ", 2, 2, true), KeyData("'", 2, 3, true),
            KeyData(".", 3, 1, true),
            KeyData("空白", 3, 2, false, isSpecialKey = true),
            KeyData("?!", 3, 3, true),

            // 右端の特殊キー
            KeyData("Del", 0, 4, false, isSpecialKey = true, rowSpan = 2),
            KeyData("Enter", 2, 4, false, isSpecialKey = true, rowSpan = 2)
        )

        // --- フリック文字マップを定義 ---
        val flickMaps = mutableMapOf<String, List<Map<FlickDirection, String>>>()
        val keyMapping = mapOf(
            "ABC" to "abc",
            "DEF" to "def",
            "GHI" to "ghi",
            "JKL" to "jkl",
            "MNO" to "mno",
            "PQRS" to "pqrs",
            "TUV" to "tuv",
            "WXYZ" to "wxyz",
            "'" to "'\"",
            "." to ".,",
            "?!" to "?!"
        )

        keyMapping.forEach { (label, chars) ->
            val lowerMap = mutableMapOf<FlickDirection, String>()
            val upperMap = mutableMapOf<FlickDirection, String>()
            val directions = listOf(
                FlickDirection.TAP, FlickDirection.UP_LEFT, FlickDirection.UP,
                FlickDirection.UP_RIGHT, FlickDirection.UP_RIGHT_FAR
            )
            for (i in chars.indices) {
                lowerMap[directions[i]] = chars[i].toString()
                upperMap[directions[i]] = chars[i].uppercase()
            }
            flickMaps[label] =
                if (isUpperCase) listOf(upperMap, lowerMap) else listOf(lowerMap, upperMap)
        }

        // 英語レイアウトは 5列 x 4行
        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion

    //region 記号レイアウト (この関数は問題ありません)
    fun createSymbolLayout(): KeyboardLayout {
        val keys = listOf(
            // 特殊キー
            KeyData("モード", 0, 0, false, isSpecialKey = true),
            KeyData("", 1, 0, false),
            KeyData("", 2, 0, false), KeyData("", 3, 0, false),

            // 数字・記号キー
            KeyData("1", 0, 1, true), KeyData("2", 0, 2, true), KeyData("3", 0, 3, true),
            KeyData("4", 1, 1, true), KeyData("5", 1, 2, true), KeyData("6", 1, 3, true),
            KeyData("7", 2, 1, true), KeyData("8", 2, 2, true), KeyData("9", 2, 3, true),
            KeyData("*", 3, 1, false, isSpecialKey = true),
            KeyData("0", 3, 2, true),
            KeyData("#", 3, 3, false, isSpecialKey = true),

            // 右端の特殊キー
            KeyData("Del", 0, 4, false, isSpecialKey = true, rowSpan = 2),
            KeyData("Enter", 2, 4, false, isSpecialKey = true, rowSpan = 2)
        )

        val flickMaps = mapOf(
            "1" to listOf(
                mapOf(
                    FlickDirection.TAP to "1",
                    FlickDirection.UP_LEFT to "@",
                    FlickDirection.UP to "#",
                    FlickDirection.UP_RIGHT to "$",
                    FlickDirection.UP_RIGHT_FAR to "%"
                )
            ),
            "2" to listOf(
                mapOf(
                    FlickDirection.TAP to "2",
                    FlickDirection.UP_LEFT to "&",
                    FlickDirection.UP to "*",
                    FlickDirection.UP_RIGHT to "(",
                    FlickDirection.UP_RIGHT_FAR to ")"
                )
            ),
            "3" to listOf(
                mapOf(
                    FlickDirection.TAP to "3",
                    FlickDirection.UP_LEFT to "「",
                    FlickDirection.UP to "」",
                    FlickDirection.UP_RIGHT to "【",
                    FlickDirection.UP_RIGHT_FAR to "】"
                )
            ),
            "4" to listOf(
                mapOf(
                    FlickDirection.TAP to "4",
                    FlickDirection.UP_LEFT to "-",
                    FlickDirection.UP to "_",
                    FlickDirection.UP_RIGHT to "=",
                    FlickDirection.UP_RIGHT_FAR to "+"
                )
            ),
            "5" to listOf(
                mapOf(
                    FlickDirection.TAP to "5",
                    FlickDirection.UP_LEFT to ":",
                    FlickDirection.UP to ";",
                    FlickDirection.UP_RIGHT to "/",
                    FlickDirection.UP_RIGHT_FAR to "~"
                )
            ),
            "6" to listOf(
                mapOf(
                    FlickDirection.TAP to "6",
                    FlickDirection.UP_LEFT to "<",
                    FlickDirection.UP to ">",
                    FlickDirection.UP_RIGHT to "≦",
                    FlickDirection.UP_RIGHT_FAR to "≧"
                )
            ),
            "7" to listOf(
                mapOf(
                    FlickDirection.TAP to "7",
                    FlickDirection.UP_LEFT to "…",
                    FlickDirection.UP to "・",
                    FlickDirection.UP_RIGHT to "→",
                    FlickDirection.UP_RIGHT_FAR to "←"
                )
            ),
            "8" to listOf(mapOf(FlickDirection.TAP to "8")),
            "9" to listOf(mapOf(FlickDirection.TAP to "9")),
            "0" to listOf(mapOf(FlickDirection.TAP to "0"))
        )

        // 記号レイアウトは 5列 x 4行
        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion
}
