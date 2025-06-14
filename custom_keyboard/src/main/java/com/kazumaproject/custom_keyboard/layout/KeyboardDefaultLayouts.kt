package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout

object KeyboardDefaultLayouts {

    //region ひらがなレイアウト
    fun createHiraganaLayout(): KeyboardLayout {
        val keys = listOf(
            // 0列目
            KeyData(
                label = "PasteActionKey", // flickKeyMapsで使うための一意なキー
                row = 0,
                column = 0,
                isFlickable = false,
                action = KeyAction.Paste,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px,
                keyType = KeyType.CROSS_FLICK // 十字フリックキーに設定
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
            KeyData("^_^", 3, 1, false),KeyData("わ", 3, 2, true), KeyData("、。?!", 3, 3, true),

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

        // --- すべてのフリックマップを FlickAction を使って定義 ---

        val pasteActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Paste,
                com.kazumaproject.core.R.drawable.content_paste_24px
            ),
            FlickDirection.UP to FlickAction.Action(
                KeyAction.SelectAll,
                com.kazumaproject.core.R.drawable.text_select_start_24dp // このリソースは仮です
            ),
            FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.Copy,
                com.kazumaproject.core.R.drawable.content_copy_24dp // このリソースは仮です
            )
        )

        val a = mapOf(
            FlickDirection.TAP to FlickAction.Input("あ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("あ"),
            FlickDirection.UP_LEFT to FlickAction.Input("い"),
            FlickDirection.UP to FlickAction.Input("う"),
            FlickDirection.UP_RIGHT to FlickAction.Input("え"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("お"),
            FlickDirection.DOWN to FlickAction.Input("小゛")
        )
        val small_a = mapOf(
            FlickDirection.TAP to FlickAction.Input("ぁ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ぃ"),
            FlickDirection.UP_LEFT to FlickAction.Input("ぅ"),
            FlickDirection.UP to FlickAction.Input("ぇ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("ぉ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ゔ"),
            FlickDirection.DOWN to FlickAction.Input("大")
        )
        val ka = mapOf(
            FlickDirection.TAP to FlickAction.Input("か"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("か"),
            FlickDirection.UP_LEFT to FlickAction.Input("き"),
            FlickDirection.UP to FlickAction.Input("く"),
            FlickDirection.UP_RIGHT to FlickAction.Input("け"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("こ"),
            FlickDirection.DOWN to FlickAction.Input("゛")
        )
        val ga = mapOf(
            FlickDirection.TAP to FlickAction.Input("が"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("が"),
            FlickDirection.UP_LEFT to FlickAction.Input("ぎ"),
            FlickDirection.UP to FlickAction.Input("ぐ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("げ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ご"),
            FlickDirection.DOWN to FlickAction.Input("゛")
        )
        val sa = mapOf(
            FlickDirection.TAP to FlickAction.Input("さ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("さ"),
            FlickDirection.UP_LEFT to FlickAction.Input("し"),
            FlickDirection.UP to FlickAction.Input("す"),
            FlickDirection.UP_RIGHT to FlickAction.Input("せ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("そ"),
            FlickDirection.DOWN to FlickAction.Input("゛")
        )
        val za = mapOf(
            FlickDirection.TAP to FlickAction.Input("ざ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ざ"),
            FlickDirection.UP_LEFT to FlickAction.Input("じ"),
            FlickDirection.UP to FlickAction.Input("ず"),
            FlickDirection.UP_RIGHT to FlickAction.Input("ぜ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ぞ"),
            FlickDirection.DOWN to FlickAction.Input("゛")
        )
        val ta = mapOf(
            FlickDirection.TAP to FlickAction.Input("た"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("た"),
            FlickDirection.UP_LEFT to FlickAction.Input("ち"),
            FlickDirection.UP to FlickAction.Input("つ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("て"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("と"),
            FlickDirection.DOWN to FlickAction.Input("゛")
        )
        val da = mapOf(
            FlickDirection.TAP to FlickAction.Input("っ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("だ"),
            FlickDirection.UP_LEFT to FlickAction.Input("ぢ"),
            FlickDirection.UP to FlickAction.Input("づ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("で"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ど"),
            FlickDirection.DOWN to FlickAction.Input("゛")
        )
        val na = mapOf(
            FlickDirection.TAP to FlickAction.Input("な"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("な"),
            FlickDirection.UP_LEFT to FlickAction.Input("に"),
            FlickDirection.UP to FlickAction.Input("ぬ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("ね"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("の")
        )
        val ha = mapOf(
            FlickDirection.TAP to FlickAction.Input("は"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("は"),
            FlickDirection.UP_LEFT to FlickAction.Input("ひ"),
            FlickDirection.UP to FlickAction.Input("ふ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("へ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ほ"),
            FlickDirection.DOWN to FlickAction.Input("゛゜")
        )
        val ba = mapOf(
            FlickDirection.TAP to FlickAction.Input("ば"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ば"),
            FlickDirection.UP_LEFT to FlickAction.Input("び"),
            FlickDirection.UP to FlickAction.Input("ぶ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("べ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ぼ"),
            FlickDirection.DOWN to FlickAction.Input("゜")
        )
        val pa = mapOf(
            FlickDirection.TAP to FlickAction.Input("ぱ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ぱ"),
            FlickDirection.UP_LEFT to FlickAction.Input("ぴ"),
            FlickDirection.UP to FlickAction.Input("ぷ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("ぺ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ぽ"),
            FlickDirection.DOWN to FlickAction.Input("゛")
        )
        val ma = mapOf(
            FlickDirection.TAP to FlickAction.Input("ま"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ま"),
            FlickDirection.UP_LEFT to FlickAction.Input("み"),
            FlickDirection.UP to FlickAction.Input("む"),
            FlickDirection.UP_RIGHT to FlickAction.Input("め"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("も")
        )
        val ya = mapOf(
            FlickDirection.TAP to FlickAction.Input("や"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("や"),
            FlickDirection.UP to FlickAction.Input("ゆ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("よ"),
            FlickDirection.DOWN to FlickAction.Input("小")
        )
        val ya_small = mapOf(
            FlickDirection.TAP to FlickAction.Input("ゃ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ゃ"),
            FlickDirection.UP to FlickAction.Input("ゅ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ょ"),
            FlickDirection.DOWN to FlickAction.Input("大")
        )
        val ra = mapOf(
            FlickDirection.TAP to FlickAction.Input("ら"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ら"),
            FlickDirection.UP_LEFT to FlickAction.Input("り"),
            FlickDirection.UP to FlickAction.Input("る"),
            FlickDirection.UP_RIGHT to FlickAction.Input("れ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ろ")
        )
        val wa = mapOf(
            FlickDirection.TAP to FlickAction.Input("わ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("わ"),
            FlickDirection.UP_LEFT to FlickAction.Input("を"),
            FlickDirection.UP to FlickAction.Input("ん"),
            FlickDirection.UP_RIGHT to FlickAction.Input("ー")
        )
        val kuten = mapOf(
            FlickDirection.TAP to FlickAction.Input("、"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("、"),
            FlickDirection.UP_LEFT to FlickAction.Input("。"),
            FlickDirection.UP to FlickAction.Input("？"),
            FlickDirection.UP_RIGHT to FlickAction.Input("！")
        )

        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> = mapOf(
            "PasteActionKey" to listOf(pasteActionMap),
            "あ" to listOf(a, small_a), "か" to listOf(ka, ga), "さ" to listOf(sa, za),
            "た" to listOf(ta, da), "な" to listOf(na), "は" to listOf(ha, ba, pa),
            "ま" to listOf(ma), "や" to listOf(ya, ya_small), "ら" to listOf(ra),
            "わ" to listOf(wa), "、。?!" to listOf(kuten)
        )

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion

    //region 英語レイアウト
    fun createEnglishLayout(isUpperCase: Boolean): KeyboardLayout {
        val keys = listOf(
            KeyData("モード", 0, 0, false, action = KeyAction.ChangeInputMode),
            KeyData("a/A", 1, 0, false, action = KeyAction.ToggleCase),
            KeyData("顔文字", 2, 0, false, action = KeyAction.ShowEmojiKeyboard),
            KeyData("記号", 3, 0, false, action = KeyAction.ChangeInputMode),
            KeyData("ABC", 0, 1, true), KeyData("DEF", 0, 2, true), KeyData("GHI", 0, 3, true),
            KeyData("JKL", 1, 1, true), KeyData("MNO", 1, 2, true), KeyData("PQRS", 1, 3, true),
            KeyData("TUV", 2, 1, true), KeyData("WXYZ", 2, 2, true), KeyData("'", 2, 3, true),
            KeyData(".", 3, 1, true),
            KeyData("空白", 3, 2, false, action = KeyAction.Space),
            KeyData("?!", 3, 3, true),
            KeyData("Del", 0, 4, false, action = KeyAction.Delete, rowSpan = 2),
            KeyData("Enter", 2, 4, false, action = KeyAction.Enter, rowSpan = 2)
        )

        val flickMaps = mutableMapOf<String, List<Map<FlickDirection, FlickAction>>>()
        val keyMapping = mapOf(
            "ABC" to "abc", "DEF" to "def", "GHI" to "ghi", "JKL" to "jkl",
            "MNO" to "mno", "PQRS" to "pqrs", "TUV" to "tuv", "WXYZ" to "wxyz",
            "'" to "'\"", "." to ".,", "?!" to "?!"
        )

        keyMapping.forEach { (label, chars) ->
            val lowerMap = mutableMapOf<FlickDirection, FlickAction>()
            val upperMap = mutableMapOf<FlickDirection, FlickAction>()
            val directions = listOf(
                FlickDirection.TAP, FlickDirection.UP_LEFT, FlickDirection.UP,
                FlickDirection.UP_RIGHT, FlickDirection.UP_RIGHT_FAR
            )
            for (i in chars.indices) {
                lowerMap[directions[i]] = FlickAction.Input(chars[i].toString())
                upperMap[directions[i]] = FlickAction.Input(chars[i].uppercase())
            }
            flickMaps[label] =
                if (isUpperCase) listOf(upperMap, lowerMap) else listOf(lowerMap, upperMap)
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion

    //region 記号レイアウト
    fun createSymbolLayout(): KeyboardLayout {
        val keys = listOf(
            KeyData("モード", 0, 0, false, action = KeyAction.ChangeInputMode),
            KeyData("", 1, 0, false, action = null),
            KeyData("", 2, 0, false, action = null),
            KeyData("", 3, 0, false, action = null),
            KeyData("1", 0, 1, true), KeyData("2", 0, 2, true), KeyData("3", 0, 3, true),
            KeyData("4", 1, 1, true), KeyData("5", 1, 2, true), KeyData("6", 1, 3, true),
            KeyData("7", 2, 1, true), KeyData("8", 2, 2, true), KeyData("9", 2, 3, true),
            KeyData("*", 3, 1, false, action = KeyAction.InputText("*")),
            KeyData("0", 3, 2, true),
            KeyData("#", 3, 3, false, action = KeyAction.InputText("#")),
            KeyData("Del", 0, 4, false, action = KeyAction.Delete, rowSpan = 2),
            KeyData("Enter", 2, 4, false, action = KeyAction.Enter, rowSpan = 2)
        )

        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> = mapOf(
            "1" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("1"),
                    FlickDirection.UP_LEFT to FlickAction.Input("@"),
                    FlickDirection.UP to FlickAction.Input("#"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("$"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("%")
                )
            ),
            "2" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("2"),
                    FlickDirection.UP_LEFT to FlickAction.Input("&"),
                    FlickDirection.UP to FlickAction.Input("*"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("("),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input(")")
                )
            ),
            "3" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("3"),
                    FlickDirection.UP_LEFT to FlickAction.Input("「"),
                    FlickDirection.UP to FlickAction.Input("」"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("【"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("】")
                )
            ),
            "4" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("4"),
                    FlickDirection.UP_LEFT to FlickAction.Input("-"),
                    FlickDirection.UP to FlickAction.Input("_"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("="),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("+")
                )
            ),
            "5" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("5"),
                    FlickDirection.UP_LEFT to FlickAction.Input(":"),
                    FlickDirection.UP to FlickAction.Input(";"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("/"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("~")
                )
            ),
            "6" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("6"),
                    FlickDirection.UP_LEFT to FlickAction.Input("<"),
                    FlickDirection.UP to FlickAction.Input(">"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("≦"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("≧")
                )
            ),
            "7" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("7"),
                    FlickDirection.UP_LEFT to FlickAction.Input("…"),
                    FlickDirection.UP to FlickAction.Input("・"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("→"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("←")
                )
            ),
            "8" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("8"))),
            "9" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("9"))),
            "0" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("0")))
        )

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion
}
