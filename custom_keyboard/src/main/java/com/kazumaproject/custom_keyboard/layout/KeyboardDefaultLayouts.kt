package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyboardLayout

object KeyboardDefaultLayouts {

    //region ひらがなレイアウト
    fun createHiraganaLayout(): KeyboardLayout {
        val keys = listOf(
            // 0列目
            KeyData(
                "絵文字",
                0,
                0,
                false,
                action = KeyAction.ShowEmojiKeyboard,
                isSpecialKey = true
            ),
            KeyData("←", 1, 0, false, action = KeyAction.MoveCursorLeft, isSpecialKey = true),
            KeyData("モード", 2, 0, false, action = KeyAction.ChangeInputMode, isSpecialKey = true),
            KeyData(
                "",
                3,
                0,
                false,
                action = KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
            ),

            // 1-3列目 (ひらがなフリックキー)
            KeyData("あ", 0, 1, true), KeyData("か", 0, 2, true), KeyData("さ", 0, 3, true),
            KeyData("た", 1, 1, true), KeyData("な", 1, 2, true), KeyData("は", 1, 3, true),
            KeyData("ま", 2, 1, true), KeyData("や", 2, 2, true), KeyData("ら", 2, 3, true),
            KeyData(
                "^_^",
                3,
                1,
                false,
                isSpecialKey = false
            ),
            KeyData("わ", 3, 2, true), KeyData("、。?!", 3, 3, true),

            // 4列目（右端の特殊キー）
            KeyData(
                "Del", 0, 4, false,
                action = KeyAction.Delete, // actionを指定
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px,
                isSpecialKey = true
            ),
            KeyData(
                "空白", 1, 4, false, action = KeyAction.Space, rowSpan = 1, isSpecialKey = true
            ),
            KeyData(
                "改行", 2, 4, false,
                action = KeyAction.NewLine, // actionを指定
                rowSpan = 2,
                drawableResId = null,
                isSpecialKey = true
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
            FlickDirection.DOWN to "小゛"
        )
        val small_a = mapOf(
            FlickDirection.TAP to "ぁ",
            FlickDirection.UP_LEFT_FAR to "ぃ",
            FlickDirection.UP_LEFT to "ぅ",
            FlickDirection.UP to "ぇ",
            FlickDirection.UP_RIGHT to "ぉ",
            FlickDirection.UP_RIGHT_FAR to "ゔ",
            FlickDirection.DOWN to "大"
        )
        val ka = mapOf(
            FlickDirection.TAP to "か",
            FlickDirection.UP_LEFT_FAR to "か",
            FlickDirection.UP_LEFT to "き",
            FlickDirection.UP to "く",
            FlickDirection.UP_RIGHT to "け",
            FlickDirection.UP_RIGHT_FAR to "こ",
            FlickDirection.DOWN to "゛"
        )
        val ga = mapOf(
            FlickDirection.TAP to "が",
            FlickDirection.UP_LEFT_FAR to "が",
            FlickDirection.UP_LEFT to "ぎ",
            FlickDirection.UP to "ぐ",
            FlickDirection.UP_RIGHT to "げ",
            FlickDirection.UP_RIGHT_FAR to "ご",
            FlickDirection.DOWN to "゛"
        )
        val sa = mapOf(
            FlickDirection.TAP to "さ",
            FlickDirection.UP_LEFT_FAR to "さ",
            FlickDirection.UP_LEFT to "し",
            FlickDirection.UP to "す",
            FlickDirection.UP_RIGHT to "せ",
            FlickDirection.UP_RIGHT_FAR to "そ",
            FlickDirection.DOWN to "゛"
        )
        val za = mapOf(
            FlickDirection.TAP to "ざ",
            FlickDirection.UP_LEFT_FAR to "ざ",
            FlickDirection.UP_LEFT to "じ",
            FlickDirection.UP to "ず",
            FlickDirection.UP_RIGHT to "ぜ",
            FlickDirection.UP_RIGHT_FAR to "ぞ",
            FlickDirection.DOWN to "゛"
        )
        val ta = mapOf(
            FlickDirection.TAP to "た",
            FlickDirection.UP_LEFT_FAR to "た",
            FlickDirection.UP_LEFT to "ち",
            FlickDirection.UP to "つ",
            FlickDirection.UP_RIGHT to "て",
            FlickDirection.UP_RIGHT_FAR to "と",
            FlickDirection.DOWN to "゛"
        )
        val da = mapOf(
            FlickDirection.TAP to "っ",
            FlickDirection.UP_LEFT_FAR to "だ",
            FlickDirection.UP_LEFT to "ぢ",
            FlickDirection.UP to "づ",
            FlickDirection.UP_RIGHT to "で",
            FlickDirection.UP_RIGHT_FAR to "ど",
            FlickDirection.DOWN to "゛"
        )
        val na = mapOf(
            FlickDirection.TAP to "な",
            FlickDirection.UP_LEFT_FAR to "な",
            FlickDirection.UP_LEFT to "に",
            FlickDirection.UP to "ぬ",
            FlickDirection.UP_RIGHT to "ね",
            FlickDirection.UP_RIGHT_FAR to "の"
        )
        val ha = mapOf(
            FlickDirection.TAP to "は",
            FlickDirection.UP_LEFT_FAR to "は",
            FlickDirection.UP_LEFT to "ひ",
            FlickDirection.UP to "ふ",
            FlickDirection.UP_RIGHT to "へ",
            FlickDirection.UP_RIGHT_FAR to "ほ",
            FlickDirection.DOWN to "゛゜"
        )
        val ba = mapOf(
            FlickDirection.TAP to "ば",
            FlickDirection.UP_LEFT_FAR to "ば",
            FlickDirection.UP_LEFT to "び",
            FlickDirection.UP to "ぶ",
            FlickDirection.UP_RIGHT to "べ",
            FlickDirection.UP_RIGHT_FAR to "ぼ",
            FlickDirection.DOWN to "゜"
        )
        val pa = mapOf(
            FlickDirection.TAP to "ぱ",
            FlickDirection.UP_LEFT_FAR to "ぱ",
            FlickDirection.UP_LEFT to "ぴ",
            FlickDirection.UP to "ぷ",
            FlickDirection.UP_RIGHT to "ぺ",
            FlickDirection.UP_RIGHT_FAR to "ぽ",
            FlickDirection.DOWN to "゛"
        )
        val ma = mapOf(
            FlickDirection.TAP to "ま",
            FlickDirection.UP_LEFT_FAR to "ま",
            FlickDirection.UP_LEFT to "み",
            FlickDirection.UP to "む",
            FlickDirection.UP_RIGHT to "め",
            FlickDirection.UP_RIGHT_FAR to "も"
        )
        val ya = mapOf(
            FlickDirection.TAP to "や",
            FlickDirection.UP_LEFT_FAR to "や",
            FlickDirection.UP to "ゆ",
            FlickDirection.UP_RIGHT_FAR to "よ",
            FlickDirection.DOWN to "小"
        )
        val ya_small = mapOf(
            FlickDirection.TAP to "ゃ",
            FlickDirection.UP_LEFT_FAR to "ゃ",
            FlickDirection.UP to "ゅ",
            FlickDirection.UP_RIGHT_FAR to "ょ",
            FlickDirection.DOWN to "大"
        )
        val ra = mapOf(
            FlickDirection.TAP to "ら",
            FlickDirection.UP_LEFT_FAR to "ら",
            FlickDirection.UP_LEFT to "り",
            FlickDirection.UP to "る",
            FlickDirection.UP_RIGHT to "れ",
            FlickDirection.UP_RIGHT_FAR to "ろ"
        )
        val wa = mapOf(
            FlickDirection.TAP to "わ",
            FlickDirection.UP_LEFT_FAR to "わ",
            FlickDirection.UP_LEFT to "を",
            FlickDirection.UP to "ん",
            FlickDirection.UP_RIGHT to "ー"
        )
        val kuten = mapOf(
            FlickDirection.TAP to "、",
            FlickDirection.UP_LEFT_FAR to "、",
            FlickDirection.UP_LEFT to "。",
            FlickDirection.UP to "？",
            FlickDirection.UP_RIGHT to "！"
        )

        val flickMaps = mapOf(
            "あ" to listOf(a, small_a), "か" to listOf(ka, ga), "さ" to listOf(sa, za),
            "た" to listOf(ta, da), "な" to listOf(na), "は" to listOf(ha, ba, pa),
            "ま" to listOf(ma), "や" to listOf(ya, ya_small), "ら" to listOf(ra),
            "わ" to listOf(wa), "、。?!" to listOf(kuten)
        )

        // ひらがなレイアウトは 5列 x 4行
        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion

    //region 英語レイアウト
    fun createEnglishLayout(isUpperCase: Boolean): KeyboardLayout {
        val keys = listOf(
            // 特殊キー
            KeyData("モード", 0, 0, false, action = KeyAction.ChangeInputMode),
            KeyData("a/A", 1, 0, false, action = KeyAction.ToggleCase),
            KeyData("顔文字", 2, 0, false, action = KeyAction.ShowEmojiKeyboard),
            KeyData(
                "記号",
                3,
                0,
                false,
                action = KeyAction.ChangeInputMode
            ), // Consider a dedicated action

            // 文字キー
            KeyData("ABC", 0, 1, true), KeyData("DEF", 0, 2, true), KeyData("GHI", 0, 3, true),
            KeyData("JKL", 1, 1, true), KeyData("MNO", 1, 2, true), KeyData("PQRS", 1, 3, true),
            KeyData("TUV", 2, 1, true), KeyData("WXYZ", 2, 2, true), KeyData("'", 2, 3, true),
            KeyData(".", 3, 1, true),
            KeyData("空白", 3, 2, false, action = KeyAction.Space),
            KeyData("?!", 3, 3, true),

            // 右端の特殊キー
            KeyData("Del", 0, 4, false, action = KeyAction.Delete, rowSpan = 2),
            KeyData("Enter", 2, 4, false, action = KeyAction.Enter, rowSpan = 2)
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

    //region 記号レイアウト
    fun createSymbolLayout(): KeyboardLayout {
        val keys = listOf(
            // 特殊キー
            KeyData("モード", 0, 0, false, action = KeyAction.ChangeInputMode),
            KeyData("", 1, 0, false, action = null),
            KeyData("", 2, 0, false, action = null),
            KeyData("", 3, 0, false, action = null),

            // 数字・記号キー
            KeyData("1", 0, 1, true), KeyData("2", 0, 2, true), KeyData("3", 0, 3, true),
            KeyData("4", 1, 1, true), KeyData("5", 1, 2, true), KeyData("6", 1, 3, true),
            KeyData("7", 2, 1, true), KeyData("8", 2, 2, true), KeyData("9", 2, 3, true),
            KeyData("*", 3, 1, false, action = KeyAction.InputText("*")),
            KeyData("0", 3, 2, true),
            KeyData("#", 3, 3, false, action = KeyAction.InputText("#")),

            // 右端の特殊キー
            KeyData("Del", 0, 4, false, action = KeyAction.Delete, rowSpan = 2),
            KeyData("Enter", 2, 4, false, action = KeyAction.Enter, rowSpan = 2)
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
}
