package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout

object KeyboardDefaultLayouts {
    /**
     * Creates the final keyboard layout based on the mode and dynamic key states.
     * @param mode The keyboard input mode (HIRAGANA, ENGLISH, etc.).
     * @param dynamicKeyStates A map of dynamic key states [keyId: String, stateIndex: Int].
     * @return The final, state-applied KeyboardLayout.
     */
    fun createFinalLayout(
        mode: KeyboardInputMode,
        dynamicKeyStates: Map<String, Int>,
        inputType: String,
        isFlick: Boolean?
    ): KeyboardLayout {
        val baseLayout = when (inputType) {
            "flick-default" -> {
                when (mode) {
                    KeyboardInputMode.HIRAGANA -> {
                        if (isFlick == true) {
                            createFlickKanaLayout(isDefaultKey = true)
                        } else {
                            createHiraganaStandardFlickLayout(isDefaultKey = true)
                        }
                    }

                    KeyboardInputMode.ENGLISH -> {
                        if (isFlick == true) {
                            createFlickEnglishLayout(
                                isDefaultKey = true,
                                isUpperCase = false
                            )
                        } else {
                            createEnglishStandardFlickLayout(
                                isUpperCase = false,
                                isDefaultKey = true
                            )
                        }
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        if (isFlick == true) {
                            createFlickNumberLayout(isDefaultKey = true)
                        } else {
                            createSymbolStandardFlickLayout(isDefaultKey = true)
                        }
                    }
                }
            }

            "flick-circle" -> {
                when (mode) {
                    KeyboardInputMode.HIRAGANA -> createHiraganaStandardFlickLayout(isDefaultKey = false)
                    KeyboardInputMode.ENGLISH -> createEnglishStandardFlickLayout(
                        isUpperCase = false,
                        isDefaultKey = false
                    )

                    KeyboardInputMode.SYMBOLS -> createSymbolStandardFlickLayout(isDefaultKey = false)
                }
            }

            "flick-sumire" -> {
                when (mode) {
                    KeyboardInputMode.HIRAGANA -> createHiraganaLayout()
                    KeyboardInputMode.ENGLISH -> createEnglishLayout(isUpperCase = false)
                    KeyboardInputMode.SYMBOLS -> createSymbolLayout()
                }
            }

            else -> {
                when (mode) {
                    KeyboardInputMode.HIRAGANA -> createHiraganaStandardFlickLayout(
                        isDefaultKey = true
                    )

                    KeyboardInputMode.ENGLISH -> createEnglishStandardFlickLayout(
                        isUpperCase = false,
                        isDefaultKey = true
                    )

                    KeyboardInputMode.SYMBOLS -> createSymbolStandardFlickLayout(
                        isDefaultKey = true
                    )
                }
            }
        }

        var finalLayout = baseLayout
        dynamicKeyStates.forEach { (keyId, stateIndex) ->
            finalLayout = applyKeyState(finalLayout, keyId, stateIndex)
        }

        return finalLayout
    }


    fun defaultLayout(): KeyboardLayout {
        return createDefaultFlickLayout(isDefaultKey = true)
    }

    private val enterKeyStates = listOf(
        FlickAction.Action(KeyAction.NewLine, "改行"),
        FlickAction.Action(KeyAction.Confirm, "確定"),
        FlickAction.Action(
            KeyAction.Enter, "実行", com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        ),
    )

    private val dakutenToggleStates = listOf(
        FlickAction.Action(
            KeyAction.InputText("^_^"),
            label = "^_^",
            drawableResId = com.kazumaproject.core.R.drawable.ic_custom_icon
        ),
        FlickAction.Action(
            KeyAction.ToggleDakuten,
            label = " 小゛゜",
        )
    )

    private val spaceConvertStates = listOf(
        FlickAction.Action(KeyAction.Space, "空白"),
        FlickAction.Action(KeyAction.Convert, "変換")
    )

    /**
     * A generic helper to update the state of a specific key.
     */
    private fun applyKeyState(
        baseLayout: KeyboardLayout, keyId: String, stateIndex: Int
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
            KeyData("あ", 0, 1, true),
            KeyData("か", 0, 2, true),
            KeyData("さ", 0, 3, true),
            KeyData("た", 1, 1, true),
            KeyData("な", 1, 2, true),
            KeyData("は", 1, 3, true),
            KeyData("ま", 2, 1, true),
            KeyData("や", 2, 2, true),
            KeyData("ら", 2, 3, true),
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
            KeyData("わ", 3, 2, true),
            KeyData(
                "、。?!",
                3,
                3,
                true,
            ),
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
            ), FlickDirection.UP to FlickAction.Action(
                KeyAction.SelectAll,
                drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.Copy, drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
            )
        )
        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
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
            FlickDirection.DOWN to FlickAction.Input("小")
        )
        val small_a = mapOf(
            FlickDirection.TAP to FlickAction.Input("ぁ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ぁ"),
            FlickDirection.UP_LEFT to FlickAction.Input("ぃ"),
            FlickDirection.UP to FlickAction.Input("ぅ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("ぇ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ぉ"),
            FlickDirection.DOWN to FlickAction.Input("”")
        )
        val dakuten_a = mapOf(
            FlickDirection.TAP to FlickAction.Input("ゔ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ゔ"),
            FlickDirection.DOWN to FlickAction.Input("”")
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
            FlickDirection.DOWN to FlickAction.Input("゛")
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
            FlickDirection.DOWN to FlickAction.Input("゛゜")
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
            FlickDirection.UP_LEFT to FlickAction.Input("を"),
            FlickDirection.UP to FlickAction.Input("ん"),
            FlickDirection.UP_RIGHT to FlickAction.Input("ー"),
            FlickDirection.DOWN to FlickAction.Input(""),
        )

        val wa_small = mapOf(
            FlickDirection.TAP to FlickAction.Input("ゎ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ゎ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("~"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("〜"),
            FlickDirection.DOWN to FlickAction.Input(""),

            )
        val kuten = mapOf(
            FlickDirection.TAP to FlickAction.Input("、"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("、"),
            FlickDirection.UP_LEFT to FlickAction.Input("。"),
            FlickDirection.UP to FlickAction.Input("？"),
            FlickDirection.UP_RIGHT to FlickAction.Input("！"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("…"),
        )

        val kuten_small = mapOf(
            FlickDirection.TAP to FlickAction.Input("-"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("-"),
            FlickDirection.UP_LEFT to FlickAction.Input("_"),
            FlickDirection.UP to FlickAction.Input("「"),
            FlickDirection.UP_RIGHT to FlickAction.Input("」"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("・"),
        )

        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> = mapOf(
            "PasteActionKey" to listOf(pasteActionMap),
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "あ" to listOf(a, small_a, dakuten_a),
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
        // KeyDataのリストは変更ありません
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
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom
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
            KeyData("@#/_", 0, 1, true),
            KeyData("ABC", 0, 2, true),
            KeyData("DEF", 0, 3, true),
            KeyData("GHI", 1, 1, true),
            KeyData("JKL", 1, 2, true),
            KeyData("MNO", 1, 3, true),
            KeyData("PQRS", 2, 1, true),
            KeyData("TUV", 2, 2, true),
            KeyData("WXYZ", 2, 3, true),
            KeyData("a/A", 3, 1, false, action = KeyAction.ToggleCase),
            KeyData("' \" ( )", 3, 2, true),
            KeyData(". , ? !", 3, 3, true),
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

        // ▼▼▼ MODIFIED: 各キーのflickMapを個別に定義 ▼▼▼

        // --- 特殊キーのflickMap ---
        val pasteActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Paste,
                drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px
            ), FlickDirection.UP to FlickAction.Action(
                KeyAction.SelectAll,
                drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.Copy, drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
            )
        )
        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            )
        )

        // --- 英字キーのflickMap (小文字) ---
        val abcLower = mapOf(
            FlickDirection.TAP to FlickAction.Input("a"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("a"),
            FlickDirection.UP_LEFT to FlickAction.Input("b"),
            FlickDirection.UP to FlickAction.Input("c"),
            FlickDirection.UP_RIGHT to FlickAction.Input("2"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val defLower = mapOf(
            FlickDirection.TAP to FlickAction.Input("d"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("d"),
            FlickDirection.UP_LEFT to FlickAction.Input("e"),
            FlickDirection.UP to FlickAction.Input("f"),
            FlickDirection.UP_RIGHT to FlickAction.Input("3"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val ghiLower = mapOf(
            FlickDirection.TAP to FlickAction.Input("g"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("g"),
            FlickDirection.UP_LEFT to FlickAction.Input("h"),
            FlickDirection.UP to FlickAction.Input("i"),
            FlickDirection.UP_RIGHT to FlickAction.Input("4"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val jklLower = mapOf(
            FlickDirection.TAP to FlickAction.Input("j"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("j"),
            FlickDirection.UP_LEFT to FlickAction.Input("k"),
            FlickDirection.UP to FlickAction.Input("l"),
            FlickDirection.UP_RIGHT to FlickAction.Input("5"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val mnoLower = mapOf(
            FlickDirection.TAP to FlickAction.Input("m"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("m"),
            FlickDirection.UP_LEFT to FlickAction.Input("n"),
            FlickDirection.UP to FlickAction.Input("o"),
            FlickDirection.UP_RIGHT to FlickAction.Input("6"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val pqrsLower = mapOf(
            FlickDirection.TAP to FlickAction.Input("p"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("p"),
            FlickDirection.UP_LEFT to FlickAction.Input("q"),
            FlickDirection.UP to FlickAction.Input("r"),
            FlickDirection.UP_RIGHT to FlickAction.Input("s"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("7"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val tuvLower = mapOf(
            FlickDirection.TAP to FlickAction.Input("t"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("t"),
            FlickDirection.UP_LEFT to FlickAction.Input("u"),
            FlickDirection.UP to FlickAction.Input("v"),
            FlickDirection.UP_RIGHT to FlickAction.Input("8"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val wxyzLower = mapOf(
            FlickDirection.TAP to FlickAction.Input("w"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("w"),
            FlickDirection.UP_LEFT to FlickAction.Input("x"),
            FlickDirection.UP to FlickAction.Input("y"),
            FlickDirection.UP_RIGHT to FlickAction.Input("z"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("9"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )

        // --- 英字キーのflickMap (大文字) ---
        val abcUpper = mapOf(
            FlickDirection.TAP to FlickAction.Input("A"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("A"),
            FlickDirection.UP_LEFT to FlickAction.Input("B"),
            FlickDirection.UP to FlickAction.Input("C"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val defUpper = mapOf(
            FlickDirection.TAP to FlickAction.Input("D"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("D"),
            FlickDirection.UP_LEFT to FlickAction.Input("E"),
            FlickDirection.UP to FlickAction.Input("F"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val ghiUpper = mapOf(
            FlickDirection.TAP to FlickAction.Input("G"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("G"),
            FlickDirection.UP_LEFT to FlickAction.Input("H"),
            FlickDirection.UP to FlickAction.Input("I"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val jklUpper = mapOf(
            FlickDirection.TAP to FlickAction.Input("J"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("J"),
            FlickDirection.UP_LEFT to FlickAction.Input("K"),
            FlickDirection.UP to FlickAction.Input("L"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val mnoUpper = mapOf(
            FlickDirection.TAP to FlickAction.Input("M"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("M"),
            FlickDirection.UP_LEFT to FlickAction.Input("N"),
            FlickDirection.UP to FlickAction.Input("O"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val pqrsUpper = mapOf(
            FlickDirection.TAP to FlickAction.Input("P"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("P"),
            FlickDirection.UP_LEFT to FlickAction.Input("Q"),
            FlickDirection.UP to FlickAction.Input("R"),
            FlickDirection.UP_RIGHT to FlickAction.Input("S"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val tuvUpper = mapOf(
            FlickDirection.TAP to FlickAction.Input("T"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("T"),
            FlickDirection.UP_LEFT to FlickAction.Input("U"),
            FlickDirection.UP to FlickAction.Input("V"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )
        val wxyzUpper = mapOf(
            FlickDirection.TAP to FlickAction.Input("W"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("W"),
            FlickDirection.UP_LEFT to FlickAction.Input("X"),
            FlickDirection.UP to FlickAction.Input("Y"),
            FlickDirection.UP_RIGHT to FlickAction.Input("Z"),
            FlickDirection.DOWN to FlickAction.Input("a/A")
        )

        // --- 記号キーのflickMap ---
        val symbols1 = mapOf(
            FlickDirection.TAP to FlickAction.Input("@"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("@"),
            FlickDirection.UP_LEFT to FlickAction.Input("#"),
            FlickDirection.UP to FlickAction.Input("/"),
            FlickDirection.UP_RIGHT to FlickAction.Input("_"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("1"),
            FlickDirection.DOWN to FlickAction.Input("")
        )
        val symbols2 = mapOf(
            FlickDirection.TAP to FlickAction.Input("'"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("'"),
            FlickDirection.UP_LEFT to FlickAction.Input("\""),
            FlickDirection.UP to FlickAction.Input("("),
            FlickDirection.UP_RIGHT to FlickAction.Input(")"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("0"),
            FlickDirection.DOWN to FlickAction.Input("")
        )
        val symbols3 = mapOf(
            FlickDirection.TAP to FlickAction.Input("."),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("."),
            FlickDirection.UP_LEFT to FlickAction.Input(","),
            FlickDirection.UP to FlickAction.Input("?"),
            FlickDirection.UP_RIGHT to FlickAction.Input("!"),
            FlickDirection.DOWN to FlickAction.Input("")
        )

        val basicMathOperators = mapOf(
            FlickDirection.TAP to FlickAction.Input("-"),   // Minus / Hyphen is very common
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("-"),
            FlickDirection.UP_LEFT to FlickAction.Input("+"),
            FlickDirection.UP to FlickAction.Input("="),
            FlickDirection.UP_RIGHT to FlickAction.Input("*"), // Multiplication
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("/"),   // Division
            FlickDirection.DOWN to FlickAction.Input("")
        )

        val advancedMathSymbols = mapOf(
            FlickDirection.TAP to FlickAction.Input("x"),      // Multiplication
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("x"),
            FlickDirection.UP_LEFT to FlickAction.Input("÷"),   // Division
            FlickDirection.UP to FlickAction.Input("√"),      // Square Root
            FlickDirection.UP_RIGHT to FlickAction.Input("^"),  // Exponent (for powers like x²)
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("±"),     // Plus-minus sign
            FlickDirection.DOWN to FlickAction.Input("")
        )

        val programmingSymbols = mapOf(
            FlickDirection.TAP to FlickAction.Input("`"),      // Backtick (for code blocks/template literals)
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("`"),
            FlickDirection.UP_RIGHT to FlickAction.Input("{"),   // Open curly brace
            FlickDirection.UP_LEFT to FlickAction.Input(":"),  // Close curly brace
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("}"),      // Colon
            FlickDirection.UP to FlickAction.Input(";"),
            FlickDirection.DOWN to FlickAction.Input("")
        )

        // isUpperCaseフラグに基づいてflickMapのリストの順序を決定し、最終的なflickMapsを作成
        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> = mapOf(
            "PasteActionKey" to listOf(pasteActionMap),
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "@#/_" to listOf(symbols1, basicMathOperators, advancedMathSymbols),
            "' \" ( )" to listOf(symbols2, programmingSymbols),
            ". , ? !" to listOf(symbols3),

            "ABC" to if (isUpperCase) listOf(abcUpper, abcLower) else listOf(abcLower, abcUpper),
            "DEF" to if (isUpperCase) listOf(defUpper, defLower) else listOf(defLower, defUpper),
            "GHI" to if (isUpperCase) listOf(ghiUpper, ghiLower) else listOf(ghiLower, ghiUpper),
            "JKL" to if (isUpperCase) listOf(jklUpper, jklLower) else listOf(jklLower, jklUpper),
            "MNO" to if (isUpperCase) listOf(mnoUpper, mnoLower) else listOf(mnoLower, mnoUpper),
            "PQRS" to if (isUpperCase) listOf(pqrsUpper, pqrsLower) else listOf(
                pqrsLower, pqrsUpper
            ),
            "TUV" to if (isUpperCase) listOf(tuvUpper, tuvLower) else listOf(tuvLower, tuvUpper),
            "WXYZ" to if (isUpperCase) listOf(wxyzUpper, wxyzLower) else listOf(
                wxyzLower, wxyzUpper
            )
        )

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    //region Symbol Layout
    private fun createSymbolLayout(): KeyboardLayout {
        // --- KEY DEFINITIONS (Cleaned up) ---
        val keys = listOf(
            KeyData(
                label = "PasteActionKey",
                row = 0,
                column = 0,
                isFlickable = true, // Enabled flick
                action = KeyAction.Paste,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px,
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                label = "CursorMoveLeft",
                row = 1,
                column = 0,
                isFlickable = true, // Enabled flick
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
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom
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
            // Number keys remain the same
            KeyData("1\n☆♪→", 0, 1, true),
            KeyData("2\n￥$€", 0, 2, true),
            KeyData("3\n%°#", 0, 3, true),
            KeyData("4\n○*・", 1, 1, true),
            KeyData("5\n+x÷", 1, 2, true),
            KeyData("6\n< = >", 1, 3, true),
            KeyData("7\n「」:", 2, 1, true),
            KeyData("8\n〒々〆", 2, 2, true),
            KeyData("9\n^|\\", 2, 3, true),
            KeyData("( ) [ ]", 3, 1, true),
            KeyData("0\n〜…", 3, 2, true),
            KeyData(". , - /", 3, 3, true),
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

        val pasteActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Paste,
                drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px
            ), FlickDirection.UP to FlickAction.Action(
                KeyAction.SelectAll,
                drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.Copy, drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
            )
        )
        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            )
        )

        // Final map combining all flick definitions
        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> = mapOf(
            // Added for consistency
            "PasteActionKey" to listOf(pasteActionMap),
            "CursorMoveLeft" to listOf(cursorMoveActionMap),

            "1\n☆♪→" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("1"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("1"),
                    FlickDirection.UP_LEFT to FlickAction.Input("☆"),
                    FlickDirection.UP to FlickAction.Input("♪"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("→"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("I"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ), mapOf(
                    FlickDirection.TAP to FlickAction.Input("一"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("一"),
                    FlickDirection.UP_LEFT to FlickAction.Input("壱"),
                    FlickDirection.UP to FlickAction.Input("ⅰ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            ),
            "2\n￥$€" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("2"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("2"),
                    FlickDirection.UP_LEFT to FlickAction.Input("￥"),
                    FlickDirection.UP to FlickAction.Input("＄"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("€"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("Ⅱ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ), mapOf(
                    FlickDirection.TAP to FlickAction.Input("二"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("二"),
                    FlickDirection.UP_LEFT to FlickAction.Input("ⅱ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            ),
            "3\n%°#" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("3"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("3"),
                    FlickDirection.UP_LEFT to FlickAction.Input("%"),
                    FlickDirection.UP to FlickAction.Input("°"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("#"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("Ⅲ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ), mapOf(
                    FlickDirection.TAP to FlickAction.Input("三"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("三"),
                    FlickDirection.UP_LEFT to FlickAction.Input("ⅲ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            ),
            "4\n○*・" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("4"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("4"),
                    FlickDirection.UP_LEFT to FlickAction.Input("○"),
                    FlickDirection.UP to FlickAction.Input("*"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("・"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("Ⅳ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ),
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("四"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("四"),
                    FlickDirection.UP_LEFT to FlickAction.Input("ⅳ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ),
            ),
            "5\n+x÷" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("5"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("5"),
                    FlickDirection.UP_LEFT to FlickAction.Input("+"),
                    FlickDirection.UP to FlickAction.Input("x"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("÷"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("Ⅴ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ), mapOf(
                    FlickDirection.TAP to FlickAction.Input("五"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("五"),
                    FlickDirection.UP_LEFT to FlickAction.Input("ⅴ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            ),
            "6\n< = >" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("6"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("6"),
                    FlickDirection.UP_LEFT to FlickAction.Input("<"),
                    FlickDirection.UP to FlickAction.Input("="),
                    FlickDirection.UP_RIGHT to FlickAction.Input(">"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("Ⅵ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ),
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("六"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("六"),
                    FlickDirection.UP_LEFT to FlickAction.Input("ⅵ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            ),
            "7\n「」:" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("7"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("7"),
                    FlickDirection.UP_LEFT to FlickAction.Input("「"),
                    FlickDirection.UP to FlickAction.Input("」"),
                    FlickDirection.UP_RIGHT to FlickAction.Input(":"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("Ⅶ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ),
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("七"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("七"),
                    FlickDirection.UP_LEFT to FlickAction.Input("ⅶ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ),
            ),
            "8\n〒々〆" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("8"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("8"),
                    FlickDirection.UP_LEFT to FlickAction.Input("〒"), // Double quote
                    FlickDirection.UP to FlickAction.Input("々"),   // Single quote
                    FlickDirection.UP_RIGHT to FlickAction.Input("〆"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("Ⅷ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ),
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("八"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("八"),
                    FlickDirection.UP_LEFT to FlickAction.Input("ⅷ"), // Double quote
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            ),
            "9\n^|\\" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("9"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("9"),
                    FlickDirection.UP_LEFT to FlickAction.Input("^"),   // Square brackets
                    FlickDirection.UP to FlickAction.Input("|"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("\\"),   // Curly braces
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("Ⅸ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                ),
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("九"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("九"),
                    FlickDirection.UP_LEFT to FlickAction.Input("ⅸ"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            ),
            "0\n〜…" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("0"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("〜"),
                    FlickDirection.UP_LEFT to FlickAction.Input("…"),
                    FlickDirection.UP to FlickAction.Input("零"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("０"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("∞"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            ),
            "( ) [ ]" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("("),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("("),
                    FlickDirection.UP_LEFT to FlickAction.Input(")"),
                    FlickDirection.UP to FlickAction.Input("["),
                    FlickDirection.UP_RIGHT to FlickAction.Input("]"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            ),
            ". , - /" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("."),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("."),
                    FlickDirection.UP_LEFT to FlickAction.Input(","),
                    FlickDirection.UP to FlickAction.Input("-"),
                    FlickDirection.UP_RIGHT to FlickAction.Input("/"),
                    FlickDirection.DOWN to FlickAction.Input(""),
                )
            )
        )

        // The layout uses 5 columns and 4 rows
        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion

    private fun createDefaultFlickLayout(
        isDefaultKey: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "",
                0,
                0,
                false,
                isSpecialKey = true,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "",
                1,
                0,
                false,
                isSpecialKey = true,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "",
                2,
                0,
                false,
                isSpecialKey = true,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "",
                3,
                0,
                false,
                isSpecialKey = true,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "あ",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "か",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "さ",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "た",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "な",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "は",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "ま",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "や",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "ら",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "",
                3,
                1,
                false,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "わ",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "、。?!",
                3,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "",
                1,
                4,
                true,
                spaceConvertStates[0].action,
                isSpecialKey = true,
                rowSpan = 1,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "",
                2,
                4,
                false,
                isSpecialKey = true,
                rowSpan = 1,
            ), KeyData(
                "",
                3,
                4,
                false,
                isSpecialKey = true,
                rowSpan = 1,
            )
        )

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

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

            )
        )

        val conversionActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Convert,
            ),
        )

        // 状態0 (^_^): タップ操作のみを持つマップ
        val emojiStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.InputText("^_^"),
                label = "^_^"
            )
            // この状態ではフリックアクションを定義しない
        )

        // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
        val dakutenStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.ToggleDakuten,
                label = " 小゛゜"
            ),
            FlickDirection.UP to FlickAction.Action(
                KeyAction.InputText("ひらがな小文字"),
                label = "小"
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.InputText("濁点"),
                label = "゛"
            ),
            FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.InputText("半濁点"),
                label = "゜"
            )
        )

        val a = mapOf(
            FlickDirection.TAP to FlickAction.Input("あ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("い"),
            FlickDirection.UP to FlickAction.Input("う"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("え"),
            FlickDirection.DOWN to FlickAction.Input("お")
        )
        val ka = mapOf(
            FlickDirection.TAP to FlickAction.Input("か"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("き"),
            FlickDirection.UP to FlickAction.Input("く"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("け"),
            FlickDirection.DOWN to FlickAction.Input("こ")
        )
        val sa = mapOf(
            FlickDirection.TAP to FlickAction.Input("さ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("し"),
            FlickDirection.UP to FlickAction.Input("す"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("せ"),
            FlickDirection.DOWN to FlickAction.Input("そ")
        )
        val ta = mapOf(
            FlickDirection.TAP to FlickAction.Input("た"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ち"),
            FlickDirection.UP to FlickAction.Input("つ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("て"),
            FlickDirection.DOWN to FlickAction.Input("と")
        )
        val na = mapOf(
            FlickDirection.TAP to FlickAction.Input("な"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("に"),
            FlickDirection.UP to FlickAction.Input("ぬ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ね"),
            FlickDirection.DOWN to FlickAction.Input("の")
        )
        val ha = mapOf(
            FlickDirection.TAP to FlickAction.Input("は"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ひ"),
            FlickDirection.UP to FlickAction.Input("ふ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("へ"),
            FlickDirection.DOWN to FlickAction.Input("ほ")
        )
        val ma = mapOf(
            FlickDirection.TAP to FlickAction.Input("ま"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("み"),
            FlickDirection.UP to FlickAction.Input("む"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("め"),
            FlickDirection.DOWN to FlickAction.Input("も")
        )
        val ya = mapOf(
            FlickDirection.TAP to FlickAction.Input("や"),
            FlickDirection.UP to FlickAction.Input("ゆ"),
            FlickDirection.DOWN to FlickAction.Input("よ")
        )
        val ra = mapOf(
            FlickDirection.TAP to FlickAction.Input("ら"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("り"),
            FlickDirection.UP to FlickAction.Input("る"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("れ"),
            FlickDirection.DOWN to FlickAction.Input("ろ")
        )
        val wa = mapOf(
            FlickDirection.TAP to FlickAction.Input("わ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("を"),
            FlickDirection.UP to FlickAction.Input("ん"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ー")
        )
        val symbols = mapOf(
            FlickDirection.TAP to FlickAction.Input("、"),
            FlickDirection.UP to FlickAction.Input("？"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("。"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("！"),
            FlickDirection.DOWN to FlickAction.Input("…")
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> = mutableMapOf(
            "PasteActionKey" to listOf(pasteActionMap),
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "あ" to listOf(a),
            "か" to listOf(ka),
            "さ" to listOf(sa),
            "た" to listOf(ta),
            "な" to listOf(na),
            "は" to listOf(ha),
            "ま" to listOf(ma),
            "や" to listOf(ya),
            "ら" to listOf(ra),
            "わ" to listOf(wa),
            "、。?!" to listOf(symbols),
        )

        dakutenToggleStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(emojiStateFlickMap))
        }
        dakutenToggleStates.getOrNull(1)?.label?.let { label ->
            flickMaps.put(label, listOf(dakutenStateFlickMap))
        }

        spaceConvertStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(spaceActionMap))
        }

        spaceConvertStates.getOrNull(1)?.label?.let { label ->
            flickMaps.put(label, listOf(conversionActionMap))
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    //region Hiragana Layout
    fun createNumberLayout(): KeyboardLayout {
        val keys = listOf(
            KeyData(
                label = "1",
                row = 0,
                column = 0,
                isFlickable = false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                label = "4",
                row = 1,
                column = 0,
                isFlickable = false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                label = "7",
                row = 2,
                column = 0,
                isFlickable = false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                label = ",",
                row = 3,
                column = 0,
                isFlickable = false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                "2", 0, 1,
                false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                "3", 0, 2,
                false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                label = "",
                row = 0,
                column = 3,
                isFlickable = false,
                action = KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyType = KeyType.NORMAL
            ),
            KeyData("5", 1, 1, false, keyType = KeyType.STANDARD_FLICK),
            KeyData("6", 1, 2, false, keyType = KeyType.STANDARD_FLICK),
            KeyData(
                "",
                1,
                3,
                false,
                spaceConvertStates[0].action,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24,
                rowSpan = 1,
                keyType = KeyType.NORMAL
            ),
            KeyData("8", 2, 1, false, keyType = KeyType.STANDARD_FLICK),
            KeyData("9", 2, 2, false, keyType = KeyType.STANDARD_FLICK),
            KeyData(
                "",
                2,
                3,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px,
                keyType = KeyType.NORMAL
            ),
            KeyData("0", 3, 1, false, keyType = KeyType.STANDARD_FLICK),
            KeyData(".", 3, 2, false, keyType = KeyType.STANDARD_FLICK),
            KeyData(
                label = enterKeyStates[0].label ?: "",
                row = 3,
                column = 3,
                isFlickable = false,
                action = enterKeyStates[0].action,
                rowSpan = 1,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
                keyId = "enter_key",
                dynamicStates = enterKeyStates,
            )
        )

        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> = mapOf(
            "1" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("1"))),
            "2" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("2"))),
            "3" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("3"))),
            "4" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("4"))),
            "5" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("5"))),
            "6" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("6"))),
            "7" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("7"))),
            "8" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("8"))),
            "9" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("9"))),
            "0" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("0"))),
            "," to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input(","),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("+"),
                    FlickDirection.UP to FlickAction.Input("-"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("*"),
                    FlickDirection.DOWN to FlickAction.Input("/"),
                )
            ),
            "." to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("."),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("("),
                    FlickDirection.UP to FlickAction.Input("%"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input(")"),
                    FlickDirection.DOWN to FlickAction.Input("="),
                )
            )
        )

        return KeyboardLayout(keys, flickMaps, 4, 4)
    }

    private fun createHiraganaStandardFlickLayout(
        isDefaultKey: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "PasteActionKey",
                0,
                0,
                false,
                KeyAction.Paste,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px,
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "CursorMoveLeft",
                1,
                0,
                false,
                KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
                keyType = KeyType.CROSS_FLICK,
            ),
            KeyData(
                "モード",
                2,
                0,
                false,
                KeyAction.ChangeInputMode,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
            ),
            KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp
            ),
            KeyData(
                "あ",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "か",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "さ",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "た",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "な",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "は",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "ま",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "や",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "ら",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                dakutenToggleStates[0].label ?: "",
                3,
                1,
                false,
                dakutenToggleStates[0].action,
                dynamicStates = dakutenToggleStates,
                keyId = "dakuten_toggle_key",
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "わ",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "、。?!",
                3,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), // Label fixed to match map key
            KeyData(
                "Del",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
            ),
            KeyData(
                spaceConvertStates[0].label ?: "",
                1,
                4,
                true,
                spaceConvertStates[0].action,
                dynamicStates = spaceConvertStates,
                isSpecialKey = true,
                rowSpan = 1,
                keyId = "space_convert_key",
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                enterKeyStates[0].label ?: "",
                2,
                4,
                false,
                enterKeyStates[0].action,
                dynamicStates = enterKeyStates,
                isSpecialKey = true,
                rowSpan = 2,
                drawableResId = enterKeyStates[0].drawableResId,
                keyId = "enter_key"
            )
        )

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

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

            )
        )

        val conversionActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Convert,
            ),
        )

        // 状態0 (^_^): タップ操作のみを持つマップ
        val emojiStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.InputText("^_^"),
                label = "^_^"
            )
            // この状態ではフリックアクションを定義しない
        )

        // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
        val dakutenStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.ToggleDakuten,
                label = " 小゛゜"
            ),
            FlickDirection.UP to FlickAction.Action(
                KeyAction.InputText("ひらがな小文字"),
                label = "小"
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.InputText("濁点"),
                label = "゛"
            ),
            FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.InputText("半濁点"),
                label = "゜"
            )
        )

        val a = mapOf(
            FlickDirection.TAP to FlickAction.Input("あ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("い"),
            FlickDirection.UP to FlickAction.Input("う"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("え"),
            FlickDirection.DOWN to FlickAction.Input("お")
        )
        val ka = mapOf(
            FlickDirection.TAP to FlickAction.Input("か"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("き"),
            FlickDirection.UP to FlickAction.Input("く"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("け"),
            FlickDirection.DOWN to FlickAction.Input("こ")
        )
        val sa = mapOf(
            FlickDirection.TAP to FlickAction.Input("さ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("し"),
            FlickDirection.UP to FlickAction.Input("す"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("せ"),
            FlickDirection.DOWN to FlickAction.Input("そ")
        )
        val ta = mapOf(
            FlickDirection.TAP to FlickAction.Input("た"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ち"),
            FlickDirection.UP to FlickAction.Input("つ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("て"),
            FlickDirection.DOWN to FlickAction.Input("と")
        )
        val na = mapOf(
            FlickDirection.TAP to FlickAction.Input("な"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("に"),
            FlickDirection.UP to FlickAction.Input("ぬ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ね"),
            FlickDirection.DOWN to FlickAction.Input("の")
        )
        val ha = mapOf(
            FlickDirection.TAP to FlickAction.Input("は"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ひ"),
            FlickDirection.UP to FlickAction.Input("ふ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("へ"),
            FlickDirection.DOWN to FlickAction.Input("ほ")
        )
        val ma = mapOf(
            FlickDirection.TAP to FlickAction.Input("ま"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("み"),
            FlickDirection.UP to FlickAction.Input("む"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("め"),
            FlickDirection.DOWN to FlickAction.Input("も")
        )
        val ya = mapOf(
            FlickDirection.TAP to FlickAction.Input("や"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("("),
            FlickDirection.UP to FlickAction.Input("ゆ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(")"),
            FlickDirection.DOWN to FlickAction.Input("よ")
        )
        val ra = mapOf(
            FlickDirection.TAP to FlickAction.Input("ら"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("り"),
            FlickDirection.UP to FlickAction.Input("る"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("れ"),
            FlickDirection.DOWN to FlickAction.Input("ろ")
        )
        val wa = mapOf(
            FlickDirection.TAP to FlickAction.Input("わ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("を"),
            FlickDirection.UP to FlickAction.Input("ん"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ー")
        )
        val symbols = mapOf(
            FlickDirection.TAP to FlickAction.Input("、"),
            FlickDirection.UP to FlickAction.Input("？"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("。"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("！"),
            FlickDirection.DOWN to FlickAction.Input("…")
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> = mutableMapOf(
            "PasteActionKey" to listOf(pasteActionMap),
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "あ" to listOf(a),
            "か" to listOf(ka),
            "さ" to listOf(sa),
            "た" to listOf(ta),
            "な" to listOf(na),
            "は" to listOf(ha),
            "ま" to listOf(ma),
            "や" to listOf(ya),
            "ら" to listOf(ra),
            "わ" to listOf(wa),
            "、。?!" to listOf(symbols),
        )

        dakutenToggleStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(emojiStateFlickMap))
        }
        dakutenToggleStates.getOrNull(1)?.label?.let { label ->
            flickMaps.put(label, listOf(dakutenStateFlickMap))
        }

        spaceConvertStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(spaceActionMap))
        }

        spaceConvertStates.getOrNull(1)?.label?.let { label ->
            flickMaps.put(label, listOf(conversionActionMap))
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    //region English Layout
    private fun createEnglishStandardFlickLayout(
        isUpperCase: Boolean,
        isDefaultKey: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "PasteActionKey",
                0,
                0,
                false,
                KeyAction.Paste,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px,
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "CursorMoveLeft",
                1,
                0,
                false,
                KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "モード",
                2,
                0,
                false,
                KeyAction.ChangeInputMode,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom
            ),
            KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp
            ),
            KeyData(
                "@#/_",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "ABC",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "DEF",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "GHI",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "JKL",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "MNO",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "PQRS",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "TUV",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "WXYZ",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "a/A",
                3,
                1,
                false,
                action = KeyAction.ToggleCase,
                isSpecialKey = false
            ),
            KeyData(
                "' \" ( )",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                ". , ? !",
                3,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "Del",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
            ),
            KeyData(
                spaceConvertStates[0].label ?: "",
                1,
                4,
                true,
                spaceConvertStates[0].action,
                dynamicStates = spaceConvertStates,
                isSpecialKey = true,
                rowSpan = 1,
                keyId = "space_convert_key",
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                enterKeyStates[0].label ?: "",
                2,
                4,
                false,
                enterKeyStates[0].action,
                dynamicStates = enterKeyStates,
                isSpecialKey = true,
                rowSpan = 2,
                drawableResId = enterKeyStates[0].drawableResId,
                keyId = "enter_key"
            )
        )

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

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

            )
        )

        fun getCase(c: Char) = if (isUpperCase) c.uppercaseChar() else c
        val symbols1 = mapOf(
            FlickDirection.TAP to FlickAction.Input("@"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("#"),
            FlickDirection.UP to FlickAction.Input("/"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("_"),
            FlickDirection.DOWN to FlickAction.Input("1")
        )
        val abc = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('a').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('b').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('c').toString()),
            FlickDirection.DOWN to FlickAction.Input("2")
        )
        val def = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('d').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('e').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('f').toString()),
            FlickDirection.DOWN to FlickAction.Input("3")
        )
        val ghi = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('g').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('h').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('i').toString()),
            FlickDirection.DOWN to FlickAction.Input("4")
        )
        val jkl = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('j').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('k').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('l').toString()),
            FlickDirection.DOWN to FlickAction.Input("5")
        )
        val mno = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('m').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('n').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('o').toString()),
            FlickDirection.DOWN to FlickAction.Input("6")
        )
        val pqrs = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('p').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('q').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('r').toString()),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(getCase('s').toString()),
            FlickDirection.DOWN to FlickAction.Input("7")
        )
        val tuv = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('t').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('u').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('v').toString()),
            FlickDirection.DOWN to FlickAction.Input("8")
        )
        val wxyz = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('w').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('x').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('y').toString()),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(getCase('z').toString()),
            FlickDirection.DOWN to FlickAction.Input("9")
        )
        val symbols2 = mapOf(
            FlickDirection.TAP to FlickAction.Input("'"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("\""),
            FlickDirection.UP to FlickAction.Input("("),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(")"),
            FlickDirection.DOWN to FlickAction.Input("0")
        )
        val symbols3 = mapOf(
            FlickDirection.TAP to FlickAction.Input("."),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(","),
            FlickDirection.UP to FlickAction.Input("?"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("!"),
            FlickDirection.DOWN to FlickAction.Input("-")
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> = mutableMapOf(
            "PasteActionKey" to listOf(pasteActionMap),
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "@#/_" to listOf(symbols1),
            "ABC" to listOf(abc),
            "DEF" to listOf(def),
            "GHI" to listOf(ghi),
            "JKL" to listOf(jkl),
            "MNO" to listOf(mno),
            "PQRS" to listOf(pqrs),
            "TUV" to listOf(tuv),
            "WXYZ" to listOf(wxyz),
            "' \" ( )" to listOf(symbols2),
            ". , ? !" to listOf(symbols3)
        )

        spaceConvertStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(spaceActionMap))
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }
    //endregion

    //region Symbol Layout
    private fun createSymbolStandardFlickLayout(
        isDefaultKey: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "PasteActionKey",
                0,
                0,
                true,
                KeyAction.Paste,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px,
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "CursorMoveLeft",
                1,
                0,
                true,
                KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "モード",
                2,
                0,
                false,
                KeyAction.ChangeInputMode,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom
            ),
            KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp
            ),
            KeyData(
                "1\n☆♪→",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "2\n￥$€",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "3\n%°#",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "4\n○*・",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "5\n+x÷",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "6\n< = >",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "7\n「」:",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "8\n〒々〆",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "9\n^|\\",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "0\n〜…",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "( ) [ ]",
                3,
                1,
                true,
                isSpecialKey = false,
                colSpan = 1,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                ".,-/",
                3,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "Del",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
            ),
            KeyData(
                spaceConvertStates[0].label ?: "",
                1,
                4,
                true,
                spaceConvertStates[0].action,
                dynamicStates = spaceConvertStates,
                isSpecialKey = true,
                rowSpan = 1,
                keyId = "space_convert_key",
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                enterKeyStates[0].label ?: "",
                2,
                4,
                false,
                enterKeyStates[0].action,
                dynamicStates = enterKeyStates,
                isSpecialKey = true,
                rowSpan = 2,
                drawableResId = enterKeyStates[0].drawableResId,
                keyId = "enter_key"
            )
        )

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
        val symbols3 = mapOf(
            FlickDirection.TAP to FlickAction.Input("."),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(","),
            FlickDirection.UP to FlickAction.Input("-"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("/")
        )

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

            )
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> = mutableMapOf(
            "PasteActionKey" to listOf(pasteActionMap),
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "1\n☆♪→" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("1"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("☆"),
                    FlickDirection.UP to FlickAction.Input("♪"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("→"),
                )
            ),
            "2\n￥$€" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("2"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("￥"),
                    FlickDirection.UP to FlickAction.Input("$"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("€"),
                )
            ),
            "3\n%°#" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("3"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("%"),
                    FlickDirection.UP to FlickAction.Input("°"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("#"),
                )
            ),
            "4\n○*・" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("4"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("○"),
                    FlickDirection.UP to FlickAction.Input("*"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("・"),

                    )
            ),
            "5\n+x÷" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("5"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("+"),
                    FlickDirection.UP to FlickAction.Input("x"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("÷"),
                ),
            ),
            "6\n< = >" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("6"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("<"),
                    FlickDirection.UP to FlickAction.Input("="),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input(">"),
                )
            ),
            "7\n「」:" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("7"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("「"),
                    FlickDirection.UP to FlickAction.Input("」"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input(":"),
                )
            ),
            "8\n〒々〆" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("8"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("〒"), // Double quote
                    FlickDirection.UP to FlickAction.Input("々"),   // Single quote
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("〆"),
                )
            ),
            "9\n^|\\" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("9"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("^"),   // Square brackets
                    FlickDirection.UP to FlickAction.Input("|"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("\\"),   // Curly braces
                )
            ),
            "0\n〜…" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("0"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("〜"),
                    FlickDirection.UP to FlickAction.Input("…"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("√"),
                )
            ),
            "( ) [ ]" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("("),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input(")"),
                    FlickDirection.UP to FlickAction.Input("["),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("]"),
                )
            ),
            ".,-/" to listOf(symbols3),
        )

        spaceConvertStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(spaceActionMap))
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    private fun createFlickKanaLayout(
        isDefaultKey: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "SwitchToNumber",
                0,
                0,
                false,
                KeyAction.SwitchToNumberLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom,
            ),
            KeyData(
                "SwitchToEnglish",
                1,
                0,
                false,
                KeyAction.SwitchToEnglishLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
            ),
            KeyData(
                "SwitchToKana",
                2,
                0,
                false,
                KeyAction.SwitchToKanaLayout,
                isSpecialKey = true,
                isHiLighted = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom,
            ),
            KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp
            ),
            KeyData(
                "あ",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "か",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "さ",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "た",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "な",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "は",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "ま",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "や",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "ら",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                dakutenToggleStates[0].label ?: "",
                3,
                1,
                false,
                dakutenToggleStates[0].action,
                dynamicStates = dakutenToggleStates,
                keyId = "dakuten_toggle_key",
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "わ",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "、。?!",
                3,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), // Label fixed to match map key
            KeyData(
                "Del",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
            ),
            KeyData(
                spaceConvertStates[0].label ?: "",
                1,
                4,
                true,
                spaceConvertStates[0].action,
                dynamicStates = spaceConvertStates,
                isSpecialKey = true,
                rowSpan = 1,
                keyId = "space_convert_key",
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "CursorMoveLeft",
                2,
                4,
                false,
                KeyAction.MoveCursorRight,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
                keyType = KeyType.CROSS_FLICK,
            ),
            KeyData(
                enterKeyStates[0].label ?: "",
                3,
                4,
                false,
                enterKeyStates[0].action,
                dynamicStates = enterKeyStates,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = enterKeyStates[0].drawableResId,
                keyId = "enter_key"
            )
        )

        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            )
        )

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

            )
        )

        val conversionActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Convert,
            ),
        )

        // 状態0 (^_^): タップ操作のみを持つマップ
        val emojiStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.InputText("^_^"),
                label = "^_^"
            )
            // この状態ではフリックアクションを定義しない
        )

        // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
        val dakutenStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.ToggleDakuten,
                label = " 小゛゜"
            ),
            FlickDirection.UP to FlickAction.Action(
                KeyAction.InputText("ひらがな小文字"),
                label = "小"
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.InputText("濁点"),
                label = "゛"
            ),
            FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.InputText("半濁点"),
                label = "゜"
            )
        )

        val a = mapOf(
            FlickDirection.TAP to FlickAction.Input("あ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("い"),
            FlickDirection.UP to FlickAction.Input("う"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("え"),
            FlickDirection.DOWN to FlickAction.Input("お")
        )
        val ka = mapOf(
            FlickDirection.TAP to FlickAction.Input("か"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("き"),
            FlickDirection.UP to FlickAction.Input("く"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("け"),
            FlickDirection.DOWN to FlickAction.Input("こ")
        )
        val sa = mapOf(
            FlickDirection.TAP to FlickAction.Input("さ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("し"),
            FlickDirection.UP to FlickAction.Input("す"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("せ"),
            FlickDirection.DOWN to FlickAction.Input("そ")
        )
        val ta = mapOf(
            FlickDirection.TAP to FlickAction.Input("た"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ち"),
            FlickDirection.UP to FlickAction.Input("つ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("て"),
            FlickDirection.DOWN to FlickAction.Input("と")
        )
        val na = mapOf(
            FlickDirection.TAP to FlickAction.Input("な"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("に"),
            FlickDirection.UP to FlickAction.Input("ぬ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ね"),
            FlickDirection.DOWN to FlickAction.Input("の")
        )
        val ha = mapOf(
            FlickDirection.TAP to FlickAction.Input("は"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ひ"),
            FlickDirection.UP to FlickAction.Input("ふ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("へ"),
            FlickDirection.DOWN to FlickAction.Input("ほ")
        )
        val ma = mapOf(
            FlickDirection.TAP to FlickAction.Input("ま"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("み"),
            FlickDirection.UP to FlickAction.Input("む"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("め"),
            FlickDirection.DOWN to FlickAction.Input("も")
        )
        val ya = mapOf(
            FlickDirection.TAP to FlickAction.Input("や"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("("),
            FlickDirection.UP to FlickAction.Input("ゆ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(")"),
            FlickDirection.DOWN to FlickAction.Input("よ")
        )
        val ra = mapOf(
            FlickDirection.TAP to FlickAction.Input("ら"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("り"),
            FlickDirection.UP to FlickAction.Input("る"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("れ"),
            FlickDirection.DOWN to FlickAction.Input("ろ")
        )
        val wa = mapOf(
            FlickDirection.TAP to FlickAction.Input("わ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("を"),
            FlickDirection.UP to FlickAction.Input("ん"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ー")
        )
        val symbols = mapOf(
            FlickDirection.TAP to FlickAction.Input("、"),
            FlickDirection.UP to FlickAction.Input("？"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("。"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("！"),
            FlickDirection.DOWN to FlickAction.Input("…")
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> = mutableMapOf(
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "あ" to listOf(a),
            "か" to listOf(ka),
            "さ" to listOf(sa),
            "た" to listOf(ta),
            "な" to listOf(na),
            "は" to listOf(ha),
            "ま" to listOf(ma),
            "や" to listOf(ya),
            "ら" to listOf(ra),
            "わ" to listOf(wa),
            "、。?!" to listOf(symbols),
        )

        dakutenToggleStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(emojiStateFlickMap))
        }
        dakutenToggleStates.getOrNull(1)?.label?.let { label ->
            flickMaps.put(label, listOf(dakutenStateFlickMap))
        }

        spaceConvertStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(spaceActionMap))
        }

        spaceConvertStates.getOrNull(1)?.label?.let { label ->
            flickMaps.put(label, listOf(conversionActionMap))
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    private fun createFlickEnglishLayout(
        isDefaultKey: Boolean,
        isUpperCase: Boolean,
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "SwitchToNumber",
                0,
                0,
                false,
                KeyAction.SwitchToNumberLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom,
            ),
            KeyData(
                "SwitchToEnglish",
                1,
                0,
                false,
                KeyAction.SwitchToEnglishLayout,
                isSpecialKey = true,
                isHiLighted = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
            ),
            KeyData(
                "SwitchToKana",
                2,
                0,
                false,
                KeyAction.SwitchToKanaLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom,
            ),
            KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp
            ),
            KeyData(
                "@#/_",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "ABC",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "DEF",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "GHI",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "JKL",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "MNO",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "PQRS",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "TUV",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "WXYZ",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "a/A",
                3,
                1,
                false,
                action = KeyAction.ToggleCase,
                isSpecialKey = false
            ),
            KeyData(
                "' \" ( )",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                ". , ? !",
                3,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "Del",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
            ),
            KeyData(
                spaceConvertStates[0].label ?: "",
                1,
                4,
                true,
                spaceConvertStates[0].action,
                dynamicStates = spaceConvertStates,
                isSpecialKey = true,
                rowSpan = 1,
                keyId = "space_convert_key",
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "CursorMoveLeft",
                2,
                4,
                false,
                KeyAction.MoveCursorRight,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
                keyType = KeyType.CROSS_FLICK,
            ),
            KeyData(
                enterKeyStates[0].label ?: "",
                3,
                4,
                false,
                enterKeyStates[0].action,
                dynamicStates = enterKeyStates,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = enterKeyStates[0].drawableResId,
                keyId = "enter_key"
            )
        )

        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            )
        )

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

            )
        )

        fun getCase(c: Char) = if (isUpperCase) c.uppercaseChar() else c
        val symbols1 = mapOf(
            FlickDirection.TAP to FlickAction.Input("@"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("#"),
            FlickDirection.UP to FlickAction.Input("/"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("_"),
            FlickDirection.DOWN to FlickAction.Input("1")
        )
        val abc = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('a').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('b').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('c').toString()),
            FlickDirection.DOWN to FlickAction.Input("2")
        )
        val def = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('d').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('e').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('f').toString()),
            FlickDirection.DOWN to FlickAction.Input("3")
        )
        val ghi = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('g').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('h').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('i').toString()),
            FlickDirection.DOWN to FlickAction.Input("4")
        )
        val jkl = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('j').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('k').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('l').toString()),
            FlickDirection.DOWN to FlickAction.Input("5")
        )
        val mno = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('m').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('n').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('o').toString()),
            FlickDirection.DOWN to FlickAction.Input("6")
        )
        val pqrs = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('p').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('q').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('r').toString()),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(getCase('s').toString()),
            FlickDirection.DOWN to FlickAction.Input("7")
        )
        val tuv = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('t').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('u').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('v').toString()),
            FlickDirection.DOWN to FlickAction.Input("8")
        )
        val wxyz = mapOf(
            FlickDirection.TAP to FlickAction.Input(getCase('w').toString()),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(getCase('x').toString()),
            FlickDirection.UP to FlickAction.Input(getCase('y').toString()),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(getCase('z').toString()),
            FlickDirection.DOWN to FlickAction.Input("9")
        )
        val symbols2 = mapOf(
            FlickDirection.TAP to FlickAction.Input("'"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("\""),
            FlickDirection.UP to FlickAction.Input("("),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(")"),
            FlickDirection.DOWN to FlickAction.Input("0")
        )
        val symbols3 = mapOf(
            FlickDirection.TAP to FlickAction.Input("."),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(","),
            FlickDirection.UP to FlickAction.Input("?"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("!"),
            FlickDirection.DOWN to FlickAction.Input("-")
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> = mutableMapOf(
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "@#/_" to listOf(symbols1),
            "ABC" to listOf(abc),
            "DEF" to listOf(def),
            "GHI" to listOf(ghi),
            "JKL" to listOf(jkl),
            "MNO" to listOf(mno),
            "PQRS" to listOf(pqrs),
            "TUV" to listOf(tuv),
            "WXYZ" to listOf(wxyz),
            "' \" ( )" to listOf(symbols2),
            ". , ? !" to listOf(symbols3)
        )

        spaceConvertStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(spaceActionMap))
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    private fun createFlickNumberLayout(
        isDefaultKey: Boolean,
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "SwitchToNumber",
                0,
                0,
                false,
                KeyAction.SwitchToNumberLayout,
                isSpecialKey = true,
                isHiLighted = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom,
            ),
            KeyData(
                "SwitchToEnglish",
                1,
                0,
                false,
                KeyAction.SwitchToEnglishLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
            ),
            KeyData(
                "SwitchToKana",
                2,
                0,
                false,
                KeyAction.SwitchToKanaLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom,
            ),
            KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp
            ),
            KeyData(
                "1\n☆♪→",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "2\n￥$€",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "3\n%°#",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "4\n○*・",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "5\n+x÷",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "6\n< = >",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "7\n「」:",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "8\n〒々〆",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "9\n^|\\",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "0\n〜…",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "( ) [ ]",
                3,
                1,
                true,
                isSpecialKey = false,
                colSpan = 1,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                ".,-/",
                3,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ),
            KeyData(
                "Del",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
            ),
            KeyData(
                spaceConvertStates[0].label ?: "",
                1,
                4,
                true,
                spaceConvertStates[0].action,
                dynamicStates = spaceConvertStates,
                isSpecialKey = true,
                rowSpan = 1,
                keyId = "space_convert_key",
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                "CursorMoveLeft",
                2,
                4,
                false,
                KeyAction.MoveCursorRight,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
                keyType = KeyType.CROSS_FLICK,
            ),
            KeyData(
                enterKeyStates[0].label ?: "",
                3,
                4,
                false,
                enterKeyStates[0].action,
                dynamicStates = enterKeyStates,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = enterKeyStates[0].drawableResId,
                keyId = "enter_key"
            )
        )

        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            )
        )

        val symbols3 = mapOf(
            FlickDirection.TAP to FlickAction.Input("."),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input(","),
            FlickDirection.UP to FlickAction.Input("-"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("/")
        )

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

            )
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> = mutableMapOf(
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "1\n☆♪→" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("1"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("☆"),
                    FlickDirection.UP to FlickAction.Input("♪"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("→"),
                )
            ),
            "2\n￥$€" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("2"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("￥"),
                    FlickDirection.UP to FlickAction.Input("$"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("€"),
                )
            ),
            "3\n%°#" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("3"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("%"),
                    FlickDirection.UP to FlickAction.Input("°"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("#"),
                )
            ),
            "4\n○*・" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("4"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("○"),
                    FlickDirection.UP to FlickAction.Input("*"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("・"),

                    )
            ),
            "5\n+x÷" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("5"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("+"),
                    FlickDirection.UP to FlickAction.Input("x"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("÷"),
                ),
            ),
            "6\n< = >" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("6"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("<"),
                    FlickDirection.UP to FlickAction.Input("="),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input(">"),
                )
            ),
            "7\n「」:" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("7"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("「"),
                    FlickDirection.UP to FlickAction.Input("」"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input(":"),
                )
            ),
            "8\n〒々〆" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("8"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("〒"), // Double quote
                    FlickDirection.UP to FlickAction.Input("々"),   // Single quote
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("〆"),
                )
            ),
            "9\n^|\\" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("9"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("^"),   // Square brackets
                    FlickDirection.UP to FlickAction.Input("|"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("\\"),   // Curly braces
                )
            ),
            "0\n〜…" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("0"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("〜"),
                    FlickDirection.UP to FlickAction.Input("…"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("√"),
                )
            ),
            "( ) [ ]" to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("("),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input(")"),
                    FlickDirection.UP to FlickAction.Input("["),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("]"),
                )
            ),
            ".,-/" to listOf(symbols3),
        )

        spaceConvertStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(spaceActionMap))
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

}
