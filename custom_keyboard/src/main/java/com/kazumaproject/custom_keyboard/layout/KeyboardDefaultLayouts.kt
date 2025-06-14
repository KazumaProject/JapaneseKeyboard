package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout

object KeyboardDefaultLayouts {

    // --- Public Method ---

    /**
     * Creates the final keyboard layout based on the mode and dynamic key states.
     * @param mode The keyboard input mode (HIRAGANA, ENGLISH, etc.).
     * @param dynamicKeyStates A map of dynamic key states [keyId: String, stateIndex: Int].
     * @return The final, state-applied KeyboardLayout.
     */
    fun createFinalLayout(
        mode: KeyboardInputMode,
        dynamicKeyStates: Map<String, Int>
    ): KeyboardLayout {
        val baseLayout = when (mode) {
            KeyboardInputMode.HIRAGANA -> createHiraganaLayout()
            KeyboardInputMode.ENGLISH -> createEnglishLayout(false) // isUpperCase is managed separately
            KeyboardInputMode.SYMBOLS -> createSymbolLayout()
        }

        var finalLayout = baseLayout
        dynamicKeyStates.forEach { (keyId, stateIndex) ->
            finalLayout = applyKeyState(finalLayout, keyId, stateIndex)
        }

        return finalLayout
    }

    // --- Private Helpers and Layout Definitions ---

    // All dynamic key states are managed centrally here
    private val enterKeyStates = listOf(
        FlickAction.Action(KeyAction.NewLine, "改行"),
        FlickAction.Action(KeyAction.Confirm, "確定"),
        FlickAction.Action(
            KeyAction.Enter,
            "実行",
            com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        ),
    )

    private val dakutenToggleStates = listOf(
        FlickAction.Action(KeyAction.InputText("^_^"), label = "^_^"),
        FlickAction.Action(
            KeyAction.ToggleDakuten,
            label = "゛゜",
            drawableResId = com.kazumaproject.core.R.drawable.kana_small
        )
    )

    // ▼▼▼ NEW: Define states for the Space/Convert key ▼▼▼
    private val spaceConvertStates = listOf(
        FlickAction.Action(KeyAction.Space, "空白"),
        FlickAction.Action(KeyAction.Convert, "変換")
    )

    /**
     * A generic helper to update the state of a specific key.
     */
    private fun applyKeyState(
        baseLayout: KeyboardLayout,
        keyId: String,
        stateIndex: Int
    ): KeyboardLayout {
        val keyIndex = baseLayout.keys.indexOfFirst { it.keyId == keyId }
        if (keyIndex == -1) return baseLayout

        val oldKey = baseLayout.keys[keyIndex]
        val states = oldKey.dynamicStates
        val selectedState =
            states?.getOrNull(stateIndex) ?: states?.firstOrNull() ?: return baseLayout

        val newKey = oldKey.copy(
            label = selectedState.label ?: "",
            action = selectedState.action,
            drawableResId = selectedState.drawableResId
        )

        val newKeys = baseLayout.keys.toMutableList().apply {
            this[keyIndex] = newKey
        }

        return baseLayout.copy(keys = newKeys)
    }

    //region Hiragana Layout
    private fun createHiraganaLayout(): KeyboardLayout {
        val keys = listOf(
            KeyData(
                label = "PasteActionKey",
                row = 0,
                column = 0,
                isFlickable = false,
                action = KeyAction.Paste,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px,
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                label = "CursorMoveLeft",
                row = 1,
                column = 0,
                isFlickable = false,
                action = KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                label = "モード",
                row = 2,
                column = 0,
                isFlickable = false,
                action = KeyAction.ChangeInputMode,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
            ),
            KeyData(
                label = "",
                row = 3,
                column = 0,
                isFlickable = false,
                action = KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp
            ),
            KeyData("あ", 0, 1, true), KeyData("か", 0, 2, true), KeyData("さ", 0, 3, true),
            KeyData("た", 1, 1, true), KeyData("な", 1, 2, true), KeyData("は", 1, 3, true),
            KeyData("ま", 2, 1, true), KeyData("や", 2, 2, true), KeyData("ら", 2, 3, true),
            KeyData(
                label = dakutenToggleStates[0].label ?: "",
                row = 3,
                column = 1,
                isFlickable = false,
                action = dakutenToggleStates[0].action,
                isSpecialKey = false,
                keyId = "dakuten_toggle_key",
                dynamicStates = dakutenToggleStates
            ),
            KeyData("わ", 3, 2, true), KeyData("、。?!", 3, 3, true),
            KeyData(
                label = "Del",
                row = 0,
                column = 4,
                isFlickable = false,
                action = KeyAction.Delete,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px,
                isSpecialKey = true
            ),
            // ▼▼▼ MODIFIED: Space key is now dynamic ▼▼▼
            KeyData(
                label = spaceConvertStates[0].label ?: "",
                row = 1,
                column = 4,
                isFlickable = false,
                action = spaceConvertStates[0].action,
                rowSpan = 1,
                isSpecialKey = true,
                keyId = "space_convert_key",
                dynamicStates = spaceConvertStates
            ),
            KeyData(
                label = enterKeyStates[0].label ?: "",
                row = 2,
                column = 4,
                isFlickable = false,
                action = enterKeyStates[0].action,
                rowSpan = 2,
                isSpecialKey = true,
                drawableResId = enterKeyStates[0].drawableResId,
                keyId = "enter_key",
                dynamicStates = enterKeyStates
            )
        )

        // Flick maps remain the same...
        val pasteActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Paste,
                drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px
            ),
            FlickDirection.UP to FlickAction.Action(
                KeyAction.SelectAll,
                drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
            ),
            FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.Copy,
                drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
            )
        )
        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            ),
            FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
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
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("の"),
            FlickDirection.DOWN to FlickAction.Input("")
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
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("も"),
            FlickDirection.DOWN to FlickAction.Input("")
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
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ろ"),
            FlickDirection.DOWN to FlickAction.Input("")
        )
        val wa = mapOf(
            FlickDirection.TAP to FlickAction.Input("わ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("わ"),
            FlickDirection.UP to FlickAction.Input("を"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ん"),
            FlickDirection.DOWN to FlickAction.Input("小")
        )

        val wa_small = mapOf(
            FlickDirection.TAP to FlickAction.Input("ゎ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ゎ"),
            FlickDirection.UP to FlickAction.Input("ー"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("〜"),
            FlickDirection.DOWN to FlickAction.Input("大")

        )
        val kuten = mapOf(
            FlickDirection.TAP to FlickAction.Input("、"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("、"),
            FlickDirection.UP_LEFT to FlickAction.Input("。"),
            FlickDirection.UP to FlickAction.Input("？"),
            FlickDirection.UP_RIGHT to FlickAction.Input("！"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ー"),
            FlickDirection.DOWN to FlickAction.Input("")
        )

        val kuten_small = mapOf(
            FlickDirection.TAP to FlickAction.Input("-"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("-"),
            FlickDirection.UP_LEFT to FlickAction.Input("_"),
            FlickDirection.UP to FlickAction.Input("「"),
            FlickDirection.UP_RIGHT to FlickAction.Input("」"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("・"),
            FlickDirection.DOWN to FlickAction.Input("")
        )

        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> = mapOf(
            "PasteActionKey" to listOf(pasteActionMap),
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "あ" to listOf(a, small_a),
            "か" to listOf(ka, ga),
            "さ" to listOf(sa, za),
            "た" to listOf(ta, da),
            "な" to listOf(na),
            "は" to listOf(ha, ba, pa),
            "ま" to listOf(ma),
            "や" to listOf(ya, ya_small),
            "ら" to listOf(ra),
            "わ" to listOf(wa, wa_small),
            "、。?!" to listOf(kuten, kuten_small)
        )

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion

    //region English Layout
    private fun createEnglishLayout(isUpperCase: Boolean): KeyboardLayout {
        val keys = listOf(
            KeyData("顔文字", 0, 0, false, KeyAction.ShowEmojiKeyboard, isSpecialKey = true),
            KeyData("a/A", 1, 0, false, KeyAction.ToggleCase, isSpecialKey = true),
            KeyData(
                "モード",
                2,
                0,
                false,
                KeyAction.ChangeInputMode,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom
            ),
            KeyData("記号", 3, 0, false, KeyAction.ChangeInputMode, isSpecialKey = true),
            KeyData("ABC", 0, 1, true), KeyData("DEF", 0, 2, true), KeyData("GHI", 0, 3, true),
            KeyData("JKL", 1, 1, true), KeyData("MNO", 1, 2, true), KeyData("PQRS", 1, 3, true),
            KeyData("TUV", 2, 1, true), KeyData("WXYZ", 2, 2, true), KeyData("'", 2, 3, true),
            KeyData(".", 3, 1, true),
            // ▼▼▼ MODIFIED: Space key is dynamic, but split for English layout's wider key ▼▼▼
            KeyData(
                label = spaceConvertStates[0].label ?: "",
                row = 3,
                column = 2,
                isFlickable = false,
                action = spaceConvertStates[0].action,
                colSpan = 2,
                isSpecialKey = true,
                keyId = "space_convert_key",
                dynamicStates = spaceConvertStates
            ),
            KeyData("?!", 3, 4, true), // Adjusted column
            KeyData(
                "Del",
                0,
                4,
                false,
                KeyAction.Delete,
                rowSpan = 2,
                isSpecialKey = true
            ), // Adjusted column
            KeyData(
                label = enterKeyStates[0].label ?: "",
                row = 2,
                column = 4,
                isFlickable = false,
                action = enterKeyStates[0].action,
                rowSpan = 2,
                isSpecialKey = true,
                drawableResId = enterKeyStates[0].drawableResId,
                keyId = "enter_key",
                dynamicStates = enterKeyStates
            ) // Adjusted column
        )

        val flickMaps = mutableMapOf<String, List<Map<FlickDirection, FlickAction>>>()
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
            val lowerMap = mutableMapOf<FlickDirection, FlickAction>()
            val upperMap = mutableMapOf<FlickDirection, FlickAction>()
            val directions = listOf(
                FlickDirection.TAP,
                FlickDirection.UP_LEFT,
                FlickDirection.UP,
                FlickDirection.UP_RIGHT,
                FlickDirection.UP_RIGHT_FAR
            )
            for (i in chars.indices) {
                lowerMap[directions[i]] = FlickAction.Input(chars[i].toString())
                upperMap[directions[i]] = FlickAction.Input(chars[i].uppercase())
            }
            flickMaps[label] =
                if (isUpperCase) listOf(upperMap, lowerMap) else listOf(lowerMap, upperMap)
        }

        // English layout might need 6 columns due to the change
        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion

    //region Symbol Layout
    private fun createSymbolLayout(): KeyboardLayout {
        val keys = listOf(
            KeyData("モード", 0, 0, false, KeyAction.ChangeInputMode),
            KeyData("", 1, 0, false), KeyData("", 2, 0, false), KeyData("", 3, 0, false),
            KeyData("1", 0, 1, true), KeyData("2", 0, 2, true), KeyData("3", 0, 3, true),
            KeyData("4", 1, 1, true), KeyData("5", 1, 2, true), KeyData("6", 1, 3, true),
            KeyData("7", 2, 1, true), KeyData("8", 2, 2, true), KeyData("9", 2, 3, true),
            // ▼▼▼ MODIFIED: Space key is now dynamic ▼▼▼
            KeyData(
                label = spaceConvertStates[0].label ?: "",
                row = 3,
                column = 1,
                colSpan = 2,
                isFlickable = false,
                action = spaceConvertStates[0].action,
                isSpecialKey = true,
                keyId = "space_convert_key",
                dynamicStates = spaceConvertStates
            ),
            KeyData("0", 3, 3, true), // Adjusted column
            KeyData("Del", 0, 4, false, KeyAction.Delete, rowSpan = 2, isSpecialKey = true),
            KeyData(
                label = enterKeyStates[0].label ?: "",
                row = 2,
                column = 4,
                isFlickable = false,
                action = enterKeyStates[0].action,
                rowSpan = 2,
                isSpecialKey = true,
                drawableResId = enterKeyStates[0].drawableResId,
                keyId = "enter_key",
                dynamicStates = enterKeyStates
            )
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

        // Symbol layout might need 5 columns
        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion
}
