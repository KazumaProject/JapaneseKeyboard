package com.kazumaproject.custom_keyboard.layout

import android.util.Log
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyMode
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

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
        inputLayoutType: String,
        inputStyle: String,
        isDeleteFlickEnabled: Boolean
    ): KeyboardLayout {
        Log.d("createFinalLayout:", "$inputLayoutType $inputStyle $mode")
        val baseLayout = when (inputLayoutType) {
            "toggle" -> {
                when (mode) {
                    KeyboardInputMode.HIRAGANA -> {
                        createHiraganaToggleLayout(
                            inputStyle, isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }

                    KeyboardInputMode.ENGLISH -> {
                        createEnglishToggleLayout(
                            isUpperCase = false,
                            inputStyle = inputStyle,
                            isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        createSymbolToggleLayout(
                            inputStyle = inputStyle, isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }
                }
            }

            "flick" -> {
                when (mode) {
                    KeyboardInputMode.HIRAGANA -> {
                        createHiraganaFlickLayout(
                            inputStyle, isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }

                    KeyboardInputMode.ENGLISH -> {
                        createEnglishFlickLayout(
                            isUpperCase = false,
                            inputStyle = inputStyle,
                            isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        createSymbolFlickLayout(
                            inputStyle = inputStyle, isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }
                }
            }

            "switch-mode-effective" -> {
                when (mode) {
                    KeyboardInputMode.HIRAGANA -> {
                        createHiraganaEffectiveLayout(
                            inputStyle, isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }

                    KeyboardInputMode.ENGLISH -> {
                        createEnglishEffectiveLayout(
                            isUpperCase = false,
                            inputStyle = inputStyle,
                            isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        createNumberEffectiveLayout(isFlickDeleteEnabled = isDeleteFlickEnabled)
                    }
                }
            }

            else -> {
                when (mode) {
                    KeyboardInputMode.HIRAGANA -> {
                        createHiraganaToggleLayout(
                            inputStyle, isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }

                    KeyboardInputMode.ENGLISH -> {
                        createEnglishToggleLayout(
                            isUpperCase = false,
                            inputStyle = inputStyle,
                            isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        createSymbolToggleLayout(
                            inputStyle = inputStyle, isFlickDeleteEnabled = isDeleteFlickEnabled
                        )
                    }
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
        FlickAction.Action(
            KeyAction.Confirm,
            drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_alt_24
        ), FlickAction.Action(
            KeyAction.Enter,
            drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
        ), FlickAction.Action(
            KeyAction.Enter, "検索",
        ), FlickAction.Action(KeyAction.Enter, "次"),
        FlickAction.Action(KeyAction.Enter, "確定")
    )

    private val dakutenToggleStates = listOf(
        FlickAction.Action(
            KeyAction.InputText("^_^"),
            label = "^_^",
            drawableResId = com.kazumaproject.core.R.drawable.ic_custom_icon
        ), FlickAction.Action(
            KeyAction.ToggleDakuten,
            label = " 小゛゜",
        )
    )

    private val deleteFlickStates = listOf(
        FlickAction.Action(
            KeyAction.Delete, drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
        ),
        FlickAction.Action(
            KeyAction.DeleteUntilSymbol,
            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol
        ),
    )

    private val katakanaToggleStates = listOf(
        FlickAction.Action(
            KeyAction.SwitchToNumberLayout,
            drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom
        ), FlickAction.Action(
            KeyAction.ToggleKatakana,
            label = " カナ",
        )
    )

    private val spaceConvertStates = listOf(
        FlickAction.Action(KeyAction.Space, "空白"), FlickAction.Action(KeyAction.Convert, "変換")
    )

    private val spaceConvertStatesCursor = listOf(
        FlickAction.Action(
            KeyAction.Space,
            label = "空白",
            drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24
        ), FlickAction.Action(KeyAction.Convert, "変換")
    )

    private val enterKeyStatesCursor = listOf(
        FlickAction.Action(KeyAction.NewLine, "改行"),
        FlickAction.Action(
            KeyAction.Confirm,
            drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_alt_24
        ), FlickAction.Action(
            KeyAction.Enter,
            drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
        ), FlickAction.Action(
            KeyAction.Enter, "Go",
        ), FlickAction.Action(KeyAction.Enter, "Next"),
        FlickAction.Action(KeyAction.Enter, "確定")
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

    private fun createHiraganaLayout(isFlickDeleteEnabled: Boolean): KeyboardLayout {
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
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
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
            if (isFlickDeleteEnabled) {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    deleteFlickStates[0].action,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = deleteFlickStates[0].drawableResId,
                    keyId = "delete_key",
                    keyType = KeyType.CROSS_FLICK
                )
            } else {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    KeyAction.Delete,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                )
            },
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
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
            ),
            FlickDirection.UP to FlickAction.Action(
                KeyAction.MoveCursorUp,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
            ),
            FlickDirection.DOWN to FlickAction.Action(
                KeyAction.MoveCursorDown,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
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

        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> =
            if (isFlickDeleteEnabled) {
                val deleteActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Delete,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.DeleteUntilSymbol,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                    )
                )
                mapOf(
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
                    "、。?!" to listOf(kuten, kuten_small),
                    "Del" to listOf(deleteActionMap)
                )
            } else {
                mapOf(
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
            }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    private fun createEnglishLayout(
        isUpperCase: Boolean, isFlickDeleteEnabled: Boolean
    ): KeyboardLayout {
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
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
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
            if (isFlickDeleteEnabled) {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    deleteFlickStates[0].action,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = deleteFlickStates[0].drawableResId,
                    keyId = "delete_key",
                    keyType = KeyType.CROSS_FLICK
                )
            } else {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    KeyAction.Delete,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                )
            },
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
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
            ),
            FlickDirection.UP to FlickAction.Action(
                KeyAction.MoveCursorUp,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
            ),
            FlickDirection.DOWN to FlickAction.Action(
                KeyAction.MoveCursorDown,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
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
        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> =
            if (isFlickDeleteEnabled) {
                val deleteActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Delete,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.DeleteUntilSymbol,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                    )
                )
                mapOf(
                    "PasteActionKey" to listOf(pasteActionMap),
                    "CursorMoveLeft" to listOf(cursorMoveActionMap),
                    "@#/_" to listOf(symbols1, basicMathOperators, advancedMathSymbols),
                    "' \" ( )" to listOf(symbols2, programmingSymbols),
                    ". , ? !" to listOf(symbols3),

                    "ABC" to if (isUpperCase) listOf(abcUpper, abcLower) else listOf(
                        abcLower, abcUpper
                    ),
                    "DEF" to if (isUpperCase) listOf(defUpper, defLower) else listOf(
                        defLower, defUpper
                    ),
                    "GHI" to if (isUpperCase) listOf(ghiUpper, ghiLower) else listOf(
                        ghiLower, ghiUpper
                    ),
                    "JKL" to if (isUpperCase) listOf(jklUpper, jklLower) else listOf(
                        jklLower, jklUpper
                    ),
                    "MNO" to if (isUpperCase) listOf(mnoUpper, mnoLower) else listOf(
                        mnoLower, mnoUpper
                    ),
                    "PQRS" to if (isUpperCase) listOf(pqrsUpper, pqrsLower) else listOf(
                        pqrsLower, pqrsUpper
                    ),
                    "TUV" to if (isUpperCase) listOf(tuvUpper, tuvLower) else listOf(
                        tuvLower, tuvUpper
                    ),
                    "WXYZ" to if (isUpperCase) listOf(wxyzUpper, wxyzLower) else listOf(
                        wxyzLower, wxyzUpper
                    ),
                    "Del" to listOf(deleteActionMap)
                )
            } else {
                mapOf(
                    "PasteActionKey" to listOf(pasteActionMap),
                    "CursorMoveLeft" to listOf(cursorMoveActionMap),
                    "@#/_" to listOf(symbols1, basicMathOperators, advancedMathSymbols),
                    "' \" ( )" to listOf(symbols2, programmingSymbols),
                    ". , ? !" to listOf(symbols3),

                    "ABC" to if (isUpperCase) listOf(abcUpper, abcLower) else listOf(
                        abcLower, abcUpper
                    ),
                    "DEF" to if (isUpperCase) listOf(defUpper, defLower) else listOf(
                        defLower, defUpper
                    ),
                    "GHI" to if (isUpperCase) listOf(ghiUpper, ghiLower) else listOf(
                        ghiLower, ghiUpper
                    ),
                    "JKL" to if (isUpperCase) listOf(jklUpper, jklLower) else listOf(
                        jklLower, jklUpper
                    ),
                    "MNO" to if (isUpperCase) listOf(mnoUpper, mnoLower) else listOf(
                        mnoLower, mnoUpper
                    ),
                    "PQRS" to if (isUpperCase) listOf(pqrsUpper, pqrsLower) else listOf(
                        pqrsLower, pqrsUpper
                    ),
                    "TUV" to if (isUpperCase) listOf(tuvUpper, tuvLower) else listOf(
                        tuvLower, tuvUpper
                    ),
                    "WXYZ" to if (isUpperCase) listOf(wxyzUpper, wxyzLower) else listOf(
                        wxyzLower, wxyzUpper
                    )
                )
            }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    private fun createSymbolLayout(isFlickDeleteEnabled: Boolean): KeyboardLayout {
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
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
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
            if (isFlickDeleteEnabled) {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    deleteFlickStates[0].action,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = deleteFlickStates[0].drawableResId,
                    keyId = "delete_key",
                    keyType = KeyType.CROSS_FLICK
                )
            } else {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    KeyAction.Delete,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                )
            },
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
            ),
            FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
            ),
            FlickDirection.UP to FlickAction.Action(
                KeyAction.MoveCursorUp,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
            ),
            FlickDirection.DOWN to FlickAction.Action(
                KeyAction.MoveCursorDown,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
            )
        )

        // Final map combining all flick definitions
        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> =
            if (isFlickDeleteEnabled) {
                val deleteActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Delete,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.DeleteUntilSymbol,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                    )
                )
                mapOf(
                    "Del" to listOf(deleteActionMap),
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
                        ), mapOf(
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
                        ), mapOf(
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
                        ), mapOf(
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
            } else {
                mapOf(
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
                        ), mapOf(
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
                        ), mapOf(
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
                        ), mapOf(
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
            }

        // The layout uses 5 columns and 4 rows
        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    private fun createDefaultFlickLayout(
        isDefaultKey: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "", 0, 0, false, isSpecialKey = true, keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "", 1, 0, false, isSpecialKey = true, keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "", 2, 0, false, isSpecialKey = true, keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "", 3, 0, false, isSpecialKey = true, keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "あ",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "か",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "さ",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "た",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "な",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "は",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "ま",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "や",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "ら",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "", 3, 1, false, keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "わ",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "、。?!",
                3,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "",
                1,
                4,
                true,
                spaceConvertStates[0].action,
                isSpecialKey = true,
                rowSpan = 1,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
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

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                KeyAction.InputText("^_^"), label = "^_^"
            )
            // この状態ではフリックアクションを定義しない
        )

        // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
        val dakutenStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.ToggleDakuten, label = " 小゛゜"
            ), FlickDirection.UP to FlickAction.Action(
                KeyAction.InputText("ひらがな小文字"), label = "小"
            ), FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.InputText("濁点"), label = "゛"
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.InputText("半濁点"), label = "゜"
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
                "2", 0, 1, false, keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                "3", 0, 2, false, keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                label = "",
                row = 0,
                column = 3,
                isFlickable = false,
                action = KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyType = KeyType.NORMAL,
                keyId = "switch_next_ime"
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

    fun createFlickKanaTemplateLayout(
        isDefaultKey: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "",
                0,
                0,
                false,
                KeyAction.SelectAll,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp,
            ), KeyData(
                "",
                1,
                0,
                false,
                KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            ), KeyData(
                "",
                2,
                0,
                false,
                isSpecialKey = true,
                action = KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24
            ), KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
            ), KeyData(
                "あ",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "か",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "さ",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "た",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "な",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "は",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "ま",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "や",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "ら",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                dakutenToggleStates[1].label ?: "",
                3,
                1,
                false,
                dakutenToggleStates[1].action,
                dynamicStates = dakutenToggleStates,
                keyId = "dakuten_toggle_key",
                keyType = KeyType.CROSS_FLICK
            ), KeyData(
                "わ",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
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
            ), KeyData(
                "",
                1,
                4,
                false,
                KeyAction.MoveCursorRight,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ), KeyData(
                "",
                2,
                4,
                false,
                KeyAction.Space,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24,
            ), KeyData(
                "",
                3,
                4,
                false,
                KeyAction.Enter,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
            )
        )

        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ), FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            )
        )

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                KeyAction.InputText("^_^"), label = "^_^"
            )
            // この状態ではフリックアクションを定義しない
        )

        // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
        val dakutenStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.ToggleDakuten, label = " 小゛゜"
            ),
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

    fun createFlickEnglishTemplateLayout(
        isDefaultKey: Boolean, isUpperCase: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "",
                0,
                0,
                false,
                KeyAction.SelectAll,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp,
            ), KeyData(
                "",
                1,
                0,
                false,
                KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            ), KeyData(
                "",
                2,
                0,
                false,
                isSpecialKey = true,
                action = KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24
            ), KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
            ), KeyData(
                "@#/_",
                0,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "ABC",
                0,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "DEF",
                0,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "GHI",
                1,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "JKL",
                1,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "MNO",
                1,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "PQRS",
                2,
                1,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "TUV",
                2,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "WXYZ",
                2,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "a/A", 3, 1, false, action = KeyAction.ToggleCase, isSpecialKey = false
            ), KeyData(
                "' \" ( )",
                3,
                2,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                ". , ? !",
                3,
                3,
                true,
                keyType = if (isDefaultKey) KeyType.PETAL_FLICK else KeyType.STANDARD_FLICK
            ), KeyData(
                "Del",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
            ), KeyData(
                "",
                1,
                4,
                false,
                KeyAction.MoveCursorRight,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ), KeyData(
                "",
                2,
                4,
                false,
                KeyAction.Space,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24,
            ), KeyData(
                "",
                3,
                4,
                false,
                KeyAction.Enter,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
            )
        )

        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ), FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            )
        )

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ), FlickDirection.UP_LEFT to FlickAction.Action(
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

    fun createNumberTemplateLayout(): KeyboardLayout {
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
                "2", 0, 1, false, keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                "3", 0, 2, false, keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                label = "",
                row = 0,
                column = 3,
                isFlickable = false,
                action = KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyType = KeyType.NORMAL,
                keyId = "switch_next_ime"
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
                label = "",
                row = 3,
                column = 3,
                isFlickable = false,
                action = KeyAction.Enter,
                rowSpan = 1,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
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

    private fun createHiraganaToggleLayout(
        inputStyle: String, isFlickDeleteEnabled: Boolean
    ): KeyboardLayout {
        return when (inputStyle) {
            "second-flick" -> {
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
                    ), KeyData(
                        "CursorMoveLeft",
                        1,
                        0,
                        false,
                        KeyAction.MoveCursorLeft,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
                        keyType = KeyType.CROSS_FLICK,
                    ), KeyData(
                        "モード",
                        2,
                        0,
                        false,
                        KeyAction.ChangeInputMode,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
                    ), KeyData(
                        "",
                        3,
                        0,
                        false,
                        KeyAction.SwitchToNextIme,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                        keyId = "switch_next_ime"
                    ), KeyData(
                        "あ", 0, 1, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        "か", 0, 2, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        "さ", 0, 3, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        "た", 1, 1, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        "な", 1, 2, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        "は", 1, 3, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        "ま", 2, 1, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        "や", 2, 2, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        "ら", 2, 3, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        dakutenToggleStates[0].label ?: "",
                        3,
                        1,
                        false,
                        dakutenToggleStates[0].action,
                        dynamicStates = dakutenToggleStates,
                        keyId = "dakuten_toggle_key",
                        keyType = KeyType.CROSS_FLICK
                    ), KeyData(
                        "わ", 3, 2, true, keyType = KeyType.TWO_STEP_FLICK
                    ), KeyData(
                        "、。?!", 3, 3, true, keyType = KeyType.TWO_STEP_FLICK
                    ), if (isFlickDeleteEnabled) {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            deleteFlickStates[0].action,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = deleteFlickStates[0].drawableResId,
                            keyId = "delete_key",
                            keyType = KeyType.CROSS_FLICK
                        )
                    } else {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            KeyAction.Delete,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                        )
                    }, KeyData(
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
                    ), KeyData(
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
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.SelectAll,
                        drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.Copy,
                        drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
                    )
                )
                val cursorMoveActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                    )
                )
                val spaceActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Space,
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                        KeyAction.InputText("^_^"), label = "^_^"
                    )
                    // この状態ではフリックアクションを定義しない
                )

                // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
                val dakutenStateFlickMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.ToggleDakuten, label = " 小゛゜"
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.InputText("ひらがな小文字"), label = "小"
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.InputText("濁点"), label = "゛"
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.InputText("半濁点"), label = "゜"
                    )
                )
                val symbols = mapOf(
                    FlickDirection.TAP to FlickAction.Input("、"),
                    FlickDirection.UP to FlickAction.Input("？"),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("。"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("！"),
                    FlickDirection.DOWN to FlickAction.Input("…")
                )
                val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                    if (isFlickDeleteEnabled) {
                        val deleteActionMap = mapOf(
                            FlickDirection.TAP to FlickAction.Action(
                                KeyAction.Delete,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                            ), FlickDirection.UP_LEFT to FlickAction.Action(
                                KeyAction.DeleteUntilSymbol,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                            )
                        )
                        mutableMapOf(
                            "PasteActionKey" to listOf(pasteActionMap),
                            "CursorMoveLeft" to listOf(cursorMoveActionMap),
                            "、。?!" to listOf(symbols),
                            "Del" to listOf(deleteActionMap)
                        )
                    } else {
                        mutableMapOf(
                            "PasteActionKey" to listOf(pasteActionMap),
                            "CursorMoveLeft" to listOf(cursorMoveActionMap),
                            "、。?!" to listOf(symbols),
                        )
                    }


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
                val twoStepFlickMaps = mapOf(
                    // あ行
                    "あ" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.UP_RIGHT to "ぁ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.LEFT to "い",
                            TfbiFlickDirection.DOWN_LEFT to "ぃ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.UP to "う",
                            TfbiFlickDirection.UP_LEFT to "ぅ"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.RIGHT to "え",
                            TfbiFlickDirection.DOWN_RIGHT to "ぇ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.DOWN to "お",
                            TfbiFlickDirection.DOWN_RIGHT to "ぉ",
                        )
                    ),
                    // か行
                    "か" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "か",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.UP_RIGHT to "が",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.LEFT to "き",
                            TfbiFlickDirection.DOWN_LEFT to "ぎ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.UP to "く",
                            TfbiFlickDirection.UP_LEFT to "ぐ"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.RIGHT to "け",
                            TfbiFlickDirection.DOWN_RIGHT to "げ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.DOWN to "こ",
                            TfbiFlickDirection.DOWN_RIGHT to "ご",
                        )
                    ),
                    // さ行
                    "さ" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.UP_RIGHT to "ざ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.LEFT to "し",
                            TfbiFlickDirection.DOWN_LEFT to "じ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.UP to "す",
                            TfbiFlickDirection.UP_LEFT to "ず"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.RIGHT to "せ",
                            TfbiFlickDirection.DOWN_RIGHT to "ぜ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.DOWN to "そ",
                            TfbiFlickDirection.DOWN_RIGHT to "ぞ",
                        )
                    ),
                    // た行
                    "た" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "た",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.UP_RIGHT to "だ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.LEFT to "ち",
                            TfbiFlickDirection.DOWN_LEFT to "ぢ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.UP to "つ",
                            TfbiFlickDirection.UP_LEFT to "づ",
                            TfbiFlickDirection.UP_RIGHT to "っ"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.RIGHT to "て",
                            TfbiFlickDirection.DOWN_RIGHT to "で"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.DOWN to "と",
                            TfbiFlickDirection.DOWN_RIGHT to "ど",
                        )
                    ),
                    // な行
                    "な" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "な",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.LEFT to "に",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.UP to "ぬ",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.RIGHT to "ね",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.DOWN to "の",
                        )
                    ),
                    // は行
                    "は" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "は",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.UP_RIGHT to "ば",
                        ), TfbiFlickDirection.UP_LEFT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.UP_LEFT to "ぱ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.LEFT to "ひ",
                            TfbiFlickDirection.DOWN_LEFT to "び",
                            TfbiFlickDirection.UP_LEFT to "ぴ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.UP to "ふ",
                            TfbiFlickDirection.UP_RIGHT to "ぷ",
                            TfbiFlickDirection.UP_LEFT to "ぶ",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.RIGHT to "へ",
                            TfbiFlickDirection.DOWN_RIGHT to "べ",
                            TfbiFlickDirection.UP_RIGHT to "ぺ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.DOWN to "ほ",
                            TfbiFlickDirection.DOWN_RIGHT to "ぼ",
                            TfbiFlickDirection.DOWN_LEFT to "ぽ",
                        )
                    ),
                    // ま行
                    "ま" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.LEFT to "み",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.UP to "む",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.RIGHT to "め",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.DOWN to "も",
                        )
                    ),
                    // や行
                    "や" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "や",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.UP_RIGHT to "ゃ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.LEFT to "(",
                            TfbiFlickDirection.DOWN_LEFT to "「",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.UP to "ゆ",
                            TfbiFlickDirection.UP_LEFT to "ゅ",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.RIGHT to ")",
                            TfbiFlickDirection.DOWN_RIGHT to "」",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.DOWN to "よ",
                            TfbiFlickDirection.DOWN_RIGHT to "ょ",
                        )
                    ),
                    // ら行
                    "ら" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.LEFT to "り",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.UP to "る",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.RIGHT to "れ",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.DOWN to "ろ",
                        )
                    ),
                    // わ行
                    "わ" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.UP_RIGHT to "ゎ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.LEFT to "を",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.UP to "ん",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.RIGHT to "ー",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.DOWN to "〜",
                        )
                    ),
                    // 記号
                    "、。?!" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "、",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.LEFT to "。",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.UP to "？",
                            TfbiFlickDirection.UP_LEFT to "：",
                            TfbiFlickDirection.UP_RIGHT to "・",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.RIGHT to "！",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.DOWN to "…",
                        )
                    )
                )
                KeyboardLayout(keys, flickMaps, 5, 4, twoStepFlickKeyMaps = twoStepFlickMaps)
            }

            "sumire" -> {
                return createHiraganaLayout(isFlickDeleteEnabled)
            }

            else -> {
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
                    ), KeyData(
                        "CursorMoveLeft",
                        1,
                        0,
                        false,
                        KeyAction.MoveCursorLeft,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
                        keyType = KeyType.CROSS_FLICK,
                    ), KeyData(
                        "モード",
                        2,
                        0,
                        false,
                        KeyAction.ChangeInputMode,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
                    ), KeyData(
                        "",
                        3,
                        0,
                        false,
                        KeyAction.SwitchToNextIme,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                        keyId = "switch_next_ime"
                    ), KeyData(
                        "あ", 0, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "か", 0, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "さ", 0, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "た", 1, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "な", 1, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "は", 1, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "ま", 2, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "や", 2, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "ら", 2, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        dakutenToggleStates[0].label ?: "",
                        3,
                        1,
                        false,
                        dakutenToggleStates[0].action,
                        dynamicStates = dakutenToggleStates,
                        keyId = "dakuten_toggle_key",
                        keyType = KeyType.CROSS_FLICK
                    ), KeyData(
                        "わ", 3, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "、。?!", 3, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), if (isFlickDeleteEnabled) {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            deleteFlickStates[0].action,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = deleteFlickStates[0].drawableResId,
                            keyId = "delete_key",
                            keyType = KeyType.CROSS_FLICK
                        )
                    } else {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            KeyAction.Delete,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                        )
                    }, KeyData(
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
                    ), KeyData(
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
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.SelectAll,
                        drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.Copy,
                        drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
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
                val spaceActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Space,
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                        KeyAction.InputText("^_^"), label = "^_^"
                    )
                    // この状態ではフリックアクションを定義しない
                )

                // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
                val dakutenStateFlickMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.ToggleDakuten, label = " 小゛゜"
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.InputText("ひらがな小文字"), label = "小"
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.InputText("濁点"), label = "゛"
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.InputText("半濁点"), label = "゜"
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

                val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                    if (isFlickDeleteEnabled) {
                        val deleteActionMap = mapOf(
                            FlickDirection.TAP to FlickAction.Action(
                                KeyAction.Delete,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                            ), FlickDirection.UP_LEFT to FlickAction.Action(
                                KeyAction.DeleteUntilSymbol,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                            )
                        )
                        mutableMapOf(
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
                            "Del" to listOf(deleteActionMap)
                        )
                    } else {
                        mutableMapOf(
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
                    }

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
                KeyboardLayout(keys, flickMaps, 5, 4)
            }
        }
    }

    private fun createEnglishToggleLayout(
        isUpperCase: Boolean, inputStyle: String, isFlickDeleteEnabled: Boolean
    ): KeyboardLayout {
        if (inputStyle == "sumire") {
            return createEnglishLayout(isUpperCase, isFlickDeleteEnabled)
        } else {
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
                ), KeyData(
                    "CursorMoveLeft",
                    1,
                    0,
                    false,
                    KeyAction.MoveCursorLeft,
                    isSpecialKey = true,
                    drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
                    keyType = KeyType.CROSS_FLICK
                ), KeyData(
                    "モード",
                    2,
                    0,
                    false,
                    KeyAction.ChangeInputMode,
                    isSpecialKey = true,
                    drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom
                ), KeyData(
                    "",
                    3,
                    0,
                    false,
                    KeyAction.SwitchToNextIme,
                    isSpecialKey = true,
                    drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                    keyId = "switch_next_ime"
                ), KeyData(
                    "@#/_", 0, 1, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    "ABC", 0, 2, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    "DEF", 0, 3, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    "GHI", 1, 1, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    "JKL", 1, 2, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    "MNO", 1, 3, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    "PQRS", 2, 1, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    "TUV", 2, 2, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    "WXYZ", 2, 3, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    "a/A", 3, 1, false, action = KeyAction.ToggleCase, isSpecialKey = false
                ), KeyData(
                    "' \" ( )", 3, 2, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), KeyData(
                    ". , ? !", 3, 3, true, keyType = when (inputStyle) {
                        "default" -> KeyType.PETAL_FLICK
                        "circle" -> KeyType.STANDARD_FLICK
                        else -> KeyType.PETAL_FLICK
                    }
                ), if (isFlickDeleteEnabled) {
                    KeyData(
                        "Del",
                        0,
                        4,
                        false,
                        deleteFlickStates[0].action,
                        isSpecialKey = true,
                        rowSpan = 1,
                        drawableResId = deleteFlickStates[0].drawableResId,
                        keyId = "delete_key",
                        keyType = KeyType.CROSS_FLICK
                    )
                } else {
                    KeyData(
                        "Del",
                        0,
                        4,
                        false,
                        KeyAction.Delete,
                        isSpecialKey = true,
                        rowSpan = 1,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                    )
                }, KeyData(
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
                ), KeyData(
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
                ), FlickDirection.UP to FlickAction.Action(
                    KeyAction.SelectAll,
                    drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
                ), FlickDirection.UP_RIGHT to FlickAction.Action(
                    KeyAction.Copy,
                    drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
                )
            )
            val cursorMoveActionMap = mapOf(
                FlickDirection.TAP to FlickAction.Action(
                    KeyAction.MoveCursorLeft,
                    drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
                ), FlickDirection.UP_RIGHT to FlickAction.Action(
                    KeyAction.MoveCursorRight,
                    drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
                ),
                FlickDirection.UP_LEFT to FlickAction.Action(
                    KeyAction.MoveCursorLeft,
                    drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                ),
                FlickDirection.UP to FlickAction.Action(
                    KeyAction.MoveCursorUp,
                    drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                ),
                FlickDirection.DOWN to FlickAction.Action(
                    KeyAction.MoveCursorDown,
                    drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                )
            )

            val spaceActionMap = mapOf(
                FlickDirection.TAP to FlickAction.Action(
                    KeyAction.Space,
                ), FlickDirection.UP_LEFT to FlickAction.Action(
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

            val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                if (isFlickDeleteEnabled) {
                    val deleteActionMap = mapOf(
                        FlickDirection.TAP to FlickAction.Action(
                            KeyAction.Delete,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                        ), FlickDirection.UP_LEFT to FlickAction.Action(
                            KeyAction.DeleteUntilSymbol,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                        )
                    )
                    mutableMapOf(
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
                        ". , ? !" to listOf(symbols3),
                        "Del" to listOf(deleteActionMap)
                    )
                } else {
                    mutableMapOf(
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
                }

            spaceConvertStates.getOrNull(0)?.label?.let { label ->
                flickMaps.put(label, listOf(spaceActionMap))
            }

            return KeyboardLayout(keys, flickMaps, 5, 4)
        }
    }

    private fun createSymbolToggleLayout(
        inputStyle: String, isFlickDeleteEnabled: Boolean
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
            ), KeyData(
                "CursorMoveLeft",
                1,
                0,
                true,
                KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
                keyType = KeyType.CROSS_FLICK
            ), KeyData(
                "モード",
                2,
                0,
                false,
                KeyAction.ChangeInputMode,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom
            ), KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
            ), KeyData(
                "1\n☆♪→", 0, 1, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "2\n￥$€", 0, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "3\n%°#", 0, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "4\n○*・", 1, 1, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "5\n+x÷", 1, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "6\n< = >", 1, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "7\n「」:", 2, 1, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "8\n〒々〆", 2, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "9\n^|\\", 2, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "0\n〜…", 3, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "( ) [ ]",
                3,
                1,
                true,
                isSpecialKey = false,
                colSpan = 1,
                keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                ".,-/", 3, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), if (isFlickDeleteEnabled) {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    deleteFlickStates[0].action,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = deleteFlickStates[0].drawableResId,
                    keyId = "delete_key",
                    keyType = KeyType.CROSS_FLICK
                )
            } else {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    KeyAction.Delete,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                )
            }, KeyData(
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
            ), KeyData(
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

        if (inputStyle == "sumire") {
            return createSymbolLayout(isFlickDeleteEnabled)
        } else {
            val pasteActionMap = mapOf(
                FlickDirection.TAP to FlickAction.Action(
                    KeyAction.Paste,
                    drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px
                ), FlickDirection.UP to FlickAction.Action(
                    KeyAction.SelectAll,
                    drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
                ), FlickDirection.UP_RIGHT to FlickAction.Action(
                    KeyAction.Copy,
                    drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
                )
            )
            val cursorMoveActionMap = mapOf(
                FlickDirection.TAP to FlickAction.Action(
                    KeyAction.MoveCursorLeft,
                    drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
                ), FlickDirection.UP_RIGHT to FlickAction.Action(
                    KeyAction.MoveCursorRight,
                    drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
                ),
                FlickDirection.UP_LEFT to FlickAction.Action(
                    KeyAction.MoveCursorLeft,
                    drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                ),
                FlickDirection.UP to FlickAction.Action(
                    KeyAction.MoveCursorUp,
                    drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                ),
                FlickDirection.DOWN to FlickAction.Action(
                    KeyAction.MoveCursorDown,
                    drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
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
                ), FlickDirection.UP_LEFT to FlickAction.Action(
                    KeyAction.Space,
                    drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

                )
            )

            val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                if (isFlickDeleteEnabled) {
                    val deleteActionMap = mapOf(
                        FlickDirection.TAP to FlickAction.Action(
                            KeyAction.Delete,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                        ), FlickDirection.UP_LEFT to FlickAction.Action(
                            KeyAction.DeleteUntilSymbol,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                        )
                    )
                    mutableMapOf(
                        "Del" to listOf(deleteActionMap),
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
                } else {
                    mutableMapOf(
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
                }

            spaceConvertStates.getOrNull(0)?.label?.let { label ->
                flickMaps.put(label, listOf(spaceActionMap))
            }

            return KeyboardLayout(keys, flickMaps, 5, 4)
        }
    }

    private fun createHiraganaFlickLayout(
        inputStyle: String, isFlickDeleteEnabled: Boolean
    ): KeyboardLayout {
        when (inputStyle) {
            "second-flick" -> {
                val keys = listOf(
                    KeyData(
                        "SwitchToNumber",
                        0,
                        0,
                        false,
                        KeyAction.SwitchToNumberLayout,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom,
                    ), KeyData(
                        "SwitchToEnglish",
                        1,
                        0,
                        false,
                        KeyAction.SwitchToEnglishLayout,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
                    ), KeyData(
                        "SwitchToKana",
                        2,
                        0,
                        false,
                        KeyAction.SwitchToKanaLayout,
                        isSpecialKey = true,
                        isHiLighted = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom,
                    ), KeyData(
                        "",
                        3,
                        0,
                        false,
                        KeyAction.SwitchToNextIme,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                        keyId = "switch_next_ime"
                    ), KeyData(
                        "あ", 0, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "か", 0, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "さ", 0, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "た", 1, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "な", 1, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "は", 1, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "ま", 2, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "や", 2, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "ら", 2, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        dakutenToggleStates[0].label ?: "",
                        3,
                        1,
                        false,
                        dakutenToggleStates[0].action,
                        dynamicStates = dakutenToggleStates,
                        keyId = "dakuten_toggle_key",
                        keyType = KeyType.CROSS_FLICK
                    ), KeyData(
                        "わ", 3, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "、。?!", 3, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            "second-flick" -> KeyType.TWO_STEP_FLICK
                            "sumire" -> KeyType.CIRCULAR_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), if (isFlickDeleteEnabled) {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            deleteFlickStates[0].action,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = deleteFlickStates[0].drawableResId,
                            keyId = "delete_key",
                            keyType = KeyType.CROSS_FLICK
                        )
                    } else {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            KeyAction.Delete,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                        )
                    }, KeyData(
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
                    ), KeyData(
                        "CursorMoveLeft",
                        2,
                        4,
                        false,
                        KeyAction.MoveCursorRight,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
                        keyType = KeyType.CROSS_FLICK,
                    ), KeyData(
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
                val pasteActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Paste,
                        drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.SelectAll,
                        drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.Copy,
                        drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
                    )
                )
                val cursorMoveActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                    )
                )

                val spaceActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Space,
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                        KeyAction.InputText("^_^"), label = "^_^"
                    )
                    // この状態ではフリックアクションを定義しない
                )
                // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
                val dakutenStateFlickMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.ToggleDakuten, label = " 小゛゜"
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.InputText("ひらがな小文字"), label = "小"
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.InputText("濁点"), label = "゛"
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.InputText("半濁点"), label = "゜"
                    )
                )

                val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                    if (isFlickDeleteEnabled) {
                        val deleteActionMap = mapOf(
                            FlickDirection.TAP to FlickAction.Action(
                                KeyAction.Delete,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                            ), FlickDirection.UP_LEFT to FlickAction.Action(
                                KeyAction.DeleteUntilSymbol,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                            )
                        )
                        mutableMapOf(
                            "PasteActionKey" to listOf(pasteActionMap),
                            "CursorMoveLeft" to listOf(cursorMoveActionMap),
                            "Del" to listOf(deleteActionMap)
                        )
                    } else {
                        mutableMapOf(
                            "PasteActionKey" to listOf(pasteActionMap),
                            "CursorMoveLeft" to listOf(cursorMoveActionMap),
                        )
                    }

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

                // 2段階フリック用の文字マップ (isDefaultKey = true の場合に利用)
                val twoStepFlickMaps = mapOf(
                    // あ行
                    "あ" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.UP_RIGHT to "ぁ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.LEFT to "い",
                            TfbiFlickDirection.DOWN_LEFT to "ぃ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.UP to "う",
                            TfbiFlickDirection.UP_LEFT to "ぅ"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.RIGHT to "え",
                            TfbiFlickDirection.DOWN_RIGHT to "ぇ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.DOWN to "お",
                            TfbiFlickDirection.DOWN_RIGHT to "ぉ",
                        )
                    ),
                    // か行
                    "か" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "か",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.UP_RIGHT to "が",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.LEFT to "き",
                            TfbiFlickDirection.DOWN_LEFT to "ぎ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.UP to "く",
                            TfbiFlickDirection.UP_LEFT to "ぐ"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.RIGHT to "け",
                            TfbiFlickDirection.DOWN_RIGHT to "げ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.DOWN to "こ",
                            TfbiFlickDirection.DOWN_RIGHT to "ご",
                        )
                    ),
                    // さ行
                    "さ" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.UP_RIGHT to "ざ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.LEFT to "し",
                            TfbiFlickDirection.DOWN_LEFT to "じ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.UP to "す",
                            TfbiFlickDirection.UP_LEFT to "ず"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.RIGHT to "せ",
                            TfbiFlickDirection.DOWN_RIGHT to "ぜ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.DOWN to "そ",
                            TfbiFlickDirection.DOWN_RIGHT to "ぞ",
                        )
                    ),
                    // た行
                    "た" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "た",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.UP_RIGHT to "だ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.LEFT to "ち",
                            TfbiFlickDirection.DOWN_LEFT to "ぢ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.UP to "つ",
                            TfbiFlickDirection.UP_LEFT to "づ",
                            TfbiFlickDirection.UP_RIGHT to "っ"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.RIGHT to "て",
                            TfbiFlickDirection.DOWN_RIGHT to "で"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.DOWN to "と",
                            TfbiFlickDirection.DOWN_RIGHT to "ど",
                        )
                    ),
                    // な行
                    "な" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "な",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.LEFT to "に",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.UP to "ぬ",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.RIGHT to "ね",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.DOWN to "の",
                        )
                    ),
                    // は行
                    "は" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "は",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.UP_RIGHT to "ば",
                        ), TfbiFlickDirection.UP_LEFT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.UP_LEFT to "ぱ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.LEFT to "ひ",
                            TfbiFlickDirection.DOWN_LEFT to "び",
                            TfbiFlickDirection.UP_LEFT to "ぴ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.UP to "ふ",
                            TfbiFlickDirection.UP_RIGHT to "ぷ",
                            TfbiFlickDirection.UP_LEFT to "ぶ",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.RIGHT to "へ",
                            TfbiFlickDirection.DOWN_RIGHT to "べ",
                            TfbiFlickDirection.UP_RIGHT to "ぺ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.DOWN to "ほ",
                            TfbiFlickDirection.DOWN_RIGHT to "ぼ",
                            TfbiFlickDirection.DOWN_LEFT to "ぽ",
                        )
                    ),
                    // ま行
                    "ま" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.LEFT to "み",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.UP to "む",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.RIGHT to "め",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.DOWN to "も",
                        )
                    ),
                    // や行
                    "や" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "や",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.UP_RIGHT to "ゃ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.LEFT to "(",
                            TfbiFlickDirection.DOWN_LEFT to "「",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.UP to "ゆ",
                            TfbiFlickDirection.UP_LEFT to "ゅ",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.RIGHT to ")",
                            TfbiFlickDirection.DOWN_RIGHT to "」",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.DOWN to "よ",
                            TfbiFlickDirection.DOWN_RIGHT to "ょ",
                        )
                    ),
                    // ら行
                    "ら" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.LEFT to "り",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.UP to "る",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.RIGHT to "れ",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.DOWN to "ろ",
                        )
                    ),
                    // わ行
                    "わ" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.UP_RIGHT to "ゎ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.LEFT to "を",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.UP to "ん",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.RIGHT to "ー",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.DOWN to "〜",
                        )
                    ),
                    // 記号
                    "、。?!" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "、",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.LEFT to "。",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.UP to "？",
                            TfbiFlickDirection.UP_LEFT to "：",
                            TfbiFlickDirection.UP_RIGHT to "・",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.RIGHT to "！",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.DOWN to "…",
                        )
                    )
                )

                return KeyboardLayout(keys, flickMaps, 5, 4, twoStepFlickKeyMaps = twoStepFlickMaps)
            }

            "sumire" -> {
                return createHiraganaLayout(isFlickDeleteEnabled)
            }

            else -> {
                val keys = listOf(
                    KeyData(
                        "SwitchToNumber",
                        0,
                        0,
                        false,
                        KeyAction.SwitchToNumberLayout,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom,
                    ), KeyData(
                        "SwitchToEnglish",
                        1,
                        0,
                        false,
                        KeyAction.SwitchToEnglishLayout,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
                    ), KeyData(
                        "SwitchToKana",
                        2,
                        0,
                        false,
                        KeyAction.SwitchToKanaLayout,
                        isSpecialKey = true,
                        isHiLighted = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom,
                    ), KeyData(
                        "",
                        3,
                        0,
                        false,
                        KeyAction.SwitchToNextIme,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                        keyId = "switch_next_ime"
                    ), KeyData(
                        "あ", 0, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "か", 0, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "さ", 0, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "た", 1, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "な", 1, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "は", 1, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "ま", 2, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "や", 2, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "ら", 2, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        dakutenToggleStates[0].label ?: "",
                        3,
                        1,
                        false,
                        dakutenToggleStates[0].action,
                        dynamicStates = dakutenToggleStates,
                        keyId = "dakuten_toggle_key",
                        keyType = KeyType.CROSS_FLICK
                    ), KeyData(
                        "わ", 3, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "、。?!", 3, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), if (isFlickDeleteEnabled) {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            deleteFlickStates[0].action,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = deleteFlickStates[0].drawableResId,
                            keyId = "delete_key",
                            keyType = KeyType.CROSS_FLICK
                        )
                    } else {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            KeyAction.Delete,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                        )
                    }, KeyData(
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
                    ), KeyData(
                        "CursorMoveLeft",
                        2,
                        4,
                        false,
                        KeyAction.MoveCursorRight,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
                        keyType = KeyType.CROSS_FLICK,
                    ), KeyData(
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
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
                    )
                )

                val spaceActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Space,
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                        KeyAction.InputText("^_^"), label = "^_^"
                    )
                    // この状態ではフリックアクションを定義しない
                )

                // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
                val dakutenStateFlickMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.ToggleDakuten, label = " 小゛゜"
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.InputText("ひらがな小文字"), label = "小"
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.InputText("濁点"), label = "゛"
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.InputText("半濁点"), label = "゜"
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

                val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                    if (isFlickDeleteEnabled) {
                        val deleteActionMap = mapOf(
                            FlickDirection.TAP to FlickAction.Action(
                                KeyAction.Delete,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                            ), FlickDirection.UP_LEFT to FlickAction.Action(
                                KeyAction.DeleteUntilSymbol,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                            )
                        )
                        mutableMapOf(
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
                            "Del" to listOf(deleteActionMap)
                        )
                    } else {
                        mutableMapOf(
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
                    }

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
        }
    }

    private fun createEnglishFlickLayout(
        inputStyle: String, isUpperCase: Boolean, isFlickDeleteEnabled: Boolean
    ): KeyboardLayout {
        when (inputStyle) {
            "sumire" -> {
                return createEnglishLayout(isUpperCase, isFlickDeleteEnabled)
            }

            else -> {
                val keys = listOf(
                    KeyData(
                        "SwitchToNumber",
                        0,
                        0,
                        false,
                        KeyAction.SwitchToNumberLayout,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom,
                    ), KeyData(
                        "SwitchToEnglish",
                        1,
                        0,
                        false,
                        KeyAction.SwitchToEnglishLayout,
                        isSpecialKey = true,
                        isHiLighted = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
                    ), KeyData(
                        "SwitchToKana",
                        2,
                        0,
                        false,
                        KeyAction.SwitchToKanaLayout,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom,
                    ), KeyData(
                        "",
                        3,
                        0,
                        false,
                        KeyAction.SwitchToNextIme,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                        keyId = "switch_next_ime"
                    ), KeyData(
                        "@#/_", 0, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "ABC", 0, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "DEF", 0, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "GHI", 1, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "JKL", 1, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "MNO", 1, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "PQRS", 2, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "TUV", 2, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "WXYZ", 2, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "a/A", 3, 1, false, action = KeyAction.ToggleCase, isSpecialKey = false
                    ), KeyData(
                        "' \" ( )", 3, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        ". , ? !", 3, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), if (isFlickDeleteEnabled) {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            deleteFlickStates[0].action,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = deleteFlickStates[0].drawableResId,
                            keyId = "delete_key",
                            keyType = KeyType.CROSS_FLICK
                        )
                    } else {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            KeyAction.Delete,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                        )
                    }, KeyData(
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
                    ), KeyData(
                        "CursorMoveLeft",
                        2,
                        4,
                        false,
                        KeyAction.MoveCursorRight,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
                        keyType = KeyType.CROSS_FLICK,
                    ), KeyData(
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
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                    )
                )

                val spaceActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Space,
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
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

                val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                    if (isFlickDeleteEnabled) {
                        val deleteActionMap = mapOf(
                            FlickDirection.TAP to FlickAction.Action(
                                KeyAction.Delete,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                            ), FlickDirection.UP_LEFT to FlickAction.Action(
                                KeyAction.DeleteUntilSymbol,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                            )
                        )
                        mutableMapOf(
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
                            ". , ? !" to listOf(symbols3),
                            "Del" to listOf(deleteActionMap)
                        )
                    } else {
                        mutableMapOf(
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
                    }

                spaceConvertStates.getOrNull(0)?.label?.let { label ->
                    flickMaps.put(label, listOf(spaceActionMap))
                }

                return KeyboardLayout(keys, flickMaps, 5, 4)
            }
        }
    }

    private fun createSymbolFlickLayout(
        inputStyle: String, isFlickDeleteEnabled: Boolean
    ): KeyboardLayout {

        when (inputStyle) {
            "sumire" -> {
                return createSymbolLayout(isFlickDeleteEnabled)
            }

            else -> {
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
                    ), KeyData(
                        "SwitchToEnglish",
                        1,
                        0,
                        false,
                        KeyAction.SwitchToEnglishLayout,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
                    ), KeyData(
                        "SwitchToKana",
                        2,
                        0,
                        false,
                        KeyAction.SwitchToKanaLayout,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom,
                    ), KeyData(
                        "",
                        3,
                        0,
                        false,
                        KeyAction.SwitchToNextIme,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                        keyId = "switch_next_ime"
                    ), KeyData(
                        "1\n☆♪→", 0, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "2\n￥$€", 0, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "3\n%°#", 0, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "4\n○*・", 1, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "5\n+x÷", 1, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "6\n< = >", 1, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "7\n「」:", 2, 1, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "8\n〒々〆", 2, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "9\n^|\\", 2, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "0\n〜…", 3, 2, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        "( ) [ ]",
                        3,
                        1,
                        true,
                        isSpecialKey = false,
                        colSpan = 1,
                        keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), KeyData(
                        ".,-/", 3, 3, true, keyType = when (inputStyle) {
                            "default" -> KeyType.PETAL_FLICK
                            "circle" -> KeyType.STANDARD_FLICK
                            else -> KeyType.PETAL_FLICK
                        }
                    ), if (isFlickDeleteEnabled) {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            deleteFlickStates[0].action,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = deleteFlickStates[0].drawableResId,
                            keyId = "delete_key",
                            keyType = KeyType.CROSS_FLICK
                        )
                    } else {
                        KeyData(
                            "Del",
                            0,
                            4,
                            false,
                            KeyAction.Delete,
                            isSpecialKey = true,
                            rowSpan = 1,
                            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                        )
                    }, KeyData(
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
                    ), KeyData(
                        "CursorMoveLeft",
                        2,
                        4,
                        false,
                        KeyAction.MoveCursorRight,
                        isSpecialKey = true,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
                        keyType = KeyType.CROSS_FLICK,
                    ), KeyData(
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
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
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
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.Space,
                        drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

                    )
                )

                val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                    if (isFlickDeleteEnabled) {
                        val deleteActionMap = mapOf(
                            FlickDirection.TAP to FlickAction.Action(
                                KeyAction.Delete,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                            ), FlickDirection.UP_LEFT to FlickAction.Action(
                                KeyAction.DeleteUntilSymbol,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                            )
                        )
                        mutableMapOf(
                            "Del" to listOf(deleteActionMap),
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
                    } else {
                        mutableMapOf(
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
                    }

                spaceConvertStates.getOrNull(0)?.label?.let { label ->
                    flickMaps.put(label, listOf(spaceActionMap))
                }

                return KeyboardLayout(keys, flickMaps, 5, 4)
            }
        }
    }

    private fun createHiraganaEffectiveLayout(
        inputStyle: String, isFlickDeleteEnabled: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "",
                0,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
            ), KeyData(
                "CursorMoveLeft",
                1,
                0,
                false,
                KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                keyType = KeyType.CROSS_FLICK,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
            ), KeyData(
                katakanaToggleStates[0].label ?: "",
                2,
                0,
                false,
                katakanaToggleStates[0].action,
                isSpecialKey = true,
                dynamicStates = katakanaToggleStates,
                keyId = "katakana_toggle_key",
            ), KeyData(
                "SwitchToEnglish",
                3,
                0,
                false,
                KeyAction.SwitchToEnglishLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
            ), KeyData(
                "あ", 0, 1, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "か", 0, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "さ", 0, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "た", 1, 1, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "な", 1, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "は", 1, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "ま", 2, 1, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "や", 2, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "ら", 2, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                dakutenToggleStates[0].label ?: "",
                3,
                1,
                false,
                dakutenToggleStates[0].action,
                dynamicStates = dakutenToggleStates,
                keyId = "dakuten_toggle_key",
                keyType = KeyType.CROSS_FLICK
            ), KeyData(
                "わ", 3, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "、。?!", 3, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    "second-flick" -> KeyType.TWO_STEP_FLICK
                    "third-flick" -> KeyType.HIERARCHICAL_FLICK
                    "sumire" -> KeyType.CIRCULAR_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), if (isFlickDeleteEnabled) {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    deleteFlickStates[0].action,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = deleteFlickStates[0].drawableResId,
                    keyId = "delete_key",
                    keyType = KeyType.CROSS_FLICK
                )
            } else {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    KeyAction.Delete,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                )
            }, KeyData(
                "CursorMoveRight",
                1,
                4,
                false,
                KeyAction.MoveCursorRight,
                isSpecialKey = true,
                keyType = KeyType.CROSS_FLICK,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24,
            ), KeyData(
                spaceConvertStatesCursor[0].label ?: "",
                2,
                4,
                true,
                spaceConvertStatesCursor[0].action,
                dynamicStates = spaceConvertStatesCursor,
                isSpecialKey = true,
                rowSpan = 1,
                keyId = "space_convert_key",
                keyType = KeyType.CROSS_FLICK
            ), KeyData(
                enterKeyStatesCursor[0].label ?: "",
                3,
                4,
                false,
                enterKeyStatesCursor[0].action,
                dynamicStates = enterKeyStatesCursor,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = enterKeyStatesCursor[0].drawableResId,
                keyId = "enter_key"
            )
        )

        when (inputStyle) {
            "second-flick" -> {
                val pasteActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Paste,
                        drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.SelectAll,
                        drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.Copy,
                        drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
                    )
                )

                val cursorLeftActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                    )
                )

                val cursorRightActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                    )
                )

                val spaceActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Space,
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                        KeyAction.InputText("^_^"), label = "^_^"
                    )
                    // この状態ではフリックアクションを定義しない
                )
                // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
                val dakutenStateFlickMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.ToggleDakuten, label = " 小゛゜"
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.InputText("ひらがな小文字"), label = "小"
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.InputText("濁点"), label = "゛"
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.InputText("半濁点"), label = "゜"
                    )
                )

                val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                    if (isFlickDeleteEnabled) {
                        val deleteActionMap = mapOf(
                            FlickDirection.TAP to FlickAction.Action(
                                KeyAction.Delete,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                            ), FlickDirection.UP_LEFT to FlickAction.Action(
                                KeyAction.DeleteUntilSymbol,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                            )
                        )
                        mutableMapOf(
                            "PasteActionKey" to listOf(pasteActionMap),
                            "CursorMoveLeft" to listOf(cursorLeftActionMap),
                            "CursorMoveRight" to listOf(cursorRightActionMap),
                            "Del" to listOf(deleteActionMap)
                        )
                    } else {
                        mutableMapOf(
                            "PasteActionKey" to listOf(pasteActionMap),
                            "CursorMoveLeft" to listOf(cursorLeftActionMap),
                            "CursorMoveRight" to listOf(cursorRightActionMap),
                        )
                    }

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

                // 2段階フリック用の文字マップ (isDefaultKey = true の場合に利用)
                val twoStepFlickMaps = mapOf(
                    // あ行
                    "あ" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.UP_RIGHT to "ぁ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.LEFT to "い",
                            TfbiFlickDirection.DOWN_LEFT to "ぃ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.UP to "う",
                            TfbiFlickDirection.UP_LEFT to "ぅ"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.RIGHT to "え",
                            TfbiFlickDirection.DOWN_RIGHT to "ぇ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "あ",
                            TfbiFlickDirection.DOWN to "お",
                            TfbiFlickDirection.DOWN_RIGHT to "ぉ",
                        )
                    ),
                    // か行
                    "か" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "か",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.UP_RIGHT to "が",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.LEFT to "き",
                            TfbiFlickDirection.DOWN_LEFT to "ぎ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.UP to "く",
                            TfbiFlickDirection.UP_LEFT to "ぐ"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.RIGHT to "け",
                            TfbiFlickDirection.DOWN_RIGHT to "げ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "か",
                            TfbiFlickDirection.DOWN to "こ",
                            TfbiFlickDirection.DOWN_RIGHT to "ご",
                        )
                    ),
                    // さ行
                    "さ" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.UP_RIGHT to "ざ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.LEFT to "し",
                            TfbiFlickDirection.DOWN_LEFT to "じ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.UP to "す",
                            TfbiFlickDirection.UP_LEFT to "ず"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.RIGHT to "せ",
                            TfbiFlickDirection.DOWN_RIGHT to "ぜ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "さ",
                            TfbiFlickDirection.DOWN to "そ",
                            TfbiFlickDirection.DOWN_RIGHT to "ぞ",
                        )
                    ),
                    // た行
                    "た" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "た",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.UP_RIGHT to "だ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.LEFT to "ち",
                            TfbiFlickDirection.DOWN_LEFT to "ぢ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.UP to "つ",
                            TfbiFlickDirection.UP_LEFT to "づ",
                            TfbiFlickDirection.UP_RIGHT to "っ"
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.RIGHT to "て",
                            TfbiFlickDirection.DOWN_RIGHT to "で"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "た",
                            TfbiFlickDirection.DOWN to "と",
                            TfbiFlickDirection.DOWN_RIGHT to "ど",
                        )
                    ),
                    // な行
                    "な" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "な",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.LEFT to "に",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.UP to "ぬ",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.RIGHT to "ね",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "な",
                            TfbiFlickDirection.DOWN to "の",
                        )
                    ),
                    // は行
                    "は" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "は",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.UP_RIGHT to "ば",
                        ), TfbiFlickDirection.UP_LEFT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.UP_LEFT to "ぱ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.LEFT to "ひ",
                            TfbiFlickDirection.DOWN_LEFT to "び",
                            TfbiFlickDirection.UP_LEFT to "ぴ",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.UP to "ふ",
                            TfbiFlickDirection.UP_RIGHT to "ぷ",
                            TfbiFlickDirection.UP_LEFT to "ぶ",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.RIGHT to "へ",
                            TfbiFlickDirection.DOWN_RIGHT to "べ",
                            TfbiFlickDirection.UP_RIGHT to "ぺ"
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "は",
                            TfbiFlickDirection.DOWN to "ほ",
                            TfbiFlickDirection.DOWN_RIGHT to "ぼ",
                            TfbiFlickDirection.DOWN_LEFT to "ぽ",
                        )
                    ),
                    // ま行
                    "ま" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.LEFT to "み",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.UP to "む",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.RIGHT to "め",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "ま",
                            TfbiFlickDirection.DOWN to "も",
                        )
                    ),
                    // や行
                    "や" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "や",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.UP_RIGHT to "ゃ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.LEFT to "(",
                            TfbiFlickDirection.DOWN_LEFT to "「",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.UP to "ゆ",
                            TfbiFlickDirection.UP_LEFT to "ゅ",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.RIGHT to ")",
                            TfbiFlickDirection.DOWN_RIGHT to "」",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "や",
                            TfbiFlickDirection.DOWN to "よ",
                            TfbiFlickDirection.DOWN_RIGHT to "ょ",
                        )
                    ),
                    // ら行
                    "ら" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.LEFT to "り",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.UP to "る",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.RIGHT to "れ",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "ら",
                            TfbiFlickDirection.DOWN to "ろ",
                        )
                    ),
                    // わ行
                    "わ" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                        ), TfbiFlickDirection.UP_RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.UP_RIGHT to "ゎ",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.LEFT to "を",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.UP to "ん",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.RIGHT to "ー",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "わ",
                            TfbiFlickDirection.DOWN to "〜",
                        )
                    ),
                    // 記号
                    "、。?!" to mapOf(
                        TfbiFlickDirection.TAP to mapOf(
                            TfbiFlickDirection.TAP to "、",
                        ), TfbiFlickDirection.LEFT to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.LEFT to "。",
                        ), TfbiFlickDirection.UP to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.UP to "？",
                            TfbiFlickDirection.UP_LEFT to "：",
                            TfbiFlickDirection.UP_RIGHT to "・",
                        ), TfbiFlickDirection.RIGHT to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.RIGHT to "！",
                        ), TfbiFlickDirection.DOWN to mapOf(
                            TfbiFlickDirection.TAP to "、",
                            TfbiFlickDirection.DOWN to "…",
                        )
                    )
                )

                return KeyboardLayout(keys, flickMaps, 5, 4, twoStepFlickKeyMaps = twoStepFlickMaps)
            }

            "third-flick" -> {
                val pasteActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Paste,
                        drawableResId = com.kazumaproject.core.R.drawable.content_paste_24px
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.SelectAll,
                        drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.Copy,
                        drawableResId = com.kazumaproject.core.R.drawable.content_copy_24dp
                    )
                )

                val cursorLeftActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                    )
                )

                val cursorRightActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                    )
                )

                val spaceActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Space,
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                        KeyAction.InputText("^_^"), label = "^_^"
                    )
                    // この状態ではフリックアクションを定義しない
                )
                // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
                val dakutenStateFlickMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.ToggleDakuten, label = " 小゛゜"
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.InputText("ひらがな小文字"), label = "小"
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.InputText("濁点"), label = "゛"
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.InputText("半濁点"), label = "゜"
                    )
                )

                val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                    if (isFlickDeleteEnabled) {
                        val deleteActionMap = mapOf(
                            FlickDirection.TAP to FlickAction.Action(
                                KeyAction.Delete,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                            ), FlickDirection.UP_LEFT to FlickAction.Action(
                                KeyAction.DeleteUntilSymbol,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                            )
                        )
                        mutableMapOf(
                            "PasteActionKey" to listOf(pasteActionMap),
                            "CursorMoveLeft" to listOf(cursorLeftActionMap),
                            "CursorMoveRight" to listOf(cursorRightActionMap),
                            "Del" to listOf(deleteActionMap)
                        )
                    } else {
                        mutableMapOf(
                            "PasteActionKey" to listOf(pasteActionMap),
                            "CursorMoveLeft" to listOf(cursorLeftActionMap),
                            "CursorMoveRight" to listOf(cursorRightActionMap),
                        )
                    }

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

                // --- "あ"行 SubMenus ---
                val subMenu_A_I = mapOf(
                    TfbiFlickDirection.LEFT to TfbiFlickNode.Input("い"),
                    TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input("ぃ")
                )
                val subMenu_A_U = mapOf(
                    TfbiFlickDirection.UP to TfbiFlickNode.Input("う"),
                    TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input("ぅ")
                )
                val subMenu_A_E = mapOf(
                    TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("え"),
                    TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input("ぇ")
                )
                val subMenu_A_O = mapOf(
                    TfbiFlickDirection.DOWN to TfbiFlickNode.Input("お"),
                    TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input("ぉ")
                )

                // --- "か"行 SubMenus (ご提示のコード) ---
                val subMenuForKyo = mapOf(
                    TfbiFlickDirection.DOWN to TfbiFlickNode.Input("きょ"),
                    TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input("きょう")
                )
                val subMenu_KI = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input(
                            "き",
                            triggersMode = KeyMode.NORMAL
                        ),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "ぎ",
                            triggersMode = KeyMode.DAKUTEN
                        ),
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("きゅ"),
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("きゃ"),
                        TfbiFlickDirection.DOWN to TfbiFlickNode.SubMenu(
                            label = "きょ",
                            nextMap = subMenuForKyo
                        )
                    )
                )
                val subMenu_KU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("く"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "ぐ",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    ), cancelOnTap = true
                )
                val subMenu_KE = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("け"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "げ",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )
                val subMenu_KO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("こ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "ご",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )

                // --- "が"行 SubMenus (ご提示のコード) ---
                val subMenuForGyo = mapOf(
                    TfbiFlickDirection.DOWN to TfbiFlickNode.Input("ぎょ"),
                    TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input("ぎょう")
                )
                val subMenu_GI = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            "ぎ",
                            triggersMode = KeyMode.DAKUTEN
                        ),
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input(
                            char = "き",
                            triggersMode = KeyMode.NORMAL
                        ),
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("ぎゅ"),
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("ぎゃ"),
                        TfbiFlickDirection.DOWN to TfbiFlickNode.SubMenu(
                            label = "ぎょ",
                            nextMap = subMenuForGyo
                        )
                    )
                )
                val subMenu_GU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input("ぐ"),
                        TfbiFlickDirection.UP to TfbiFlickNode.Input(
                            char = "く",
                            triggersMode = KeyMode.NORMAL
                        )
                    ),
                )
                val subMenu_GE = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("げ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "け",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )
                val subMenu_GO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("ご"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "こ",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )

// --- "さ"行 SubMenus ---
                val subMenu_SHI = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input("し"),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "じ",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )
                val subMenu_SU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("す"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "ず",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )
                val subMenu_SE = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("せ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "ぜ",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )
                val subMenu_SO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("そ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "ぞ",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )

// --- "ざ"行 SubMenus ---
                val subMenu_JI = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input("じ"),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "し",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )
                val subMenu_ZU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("ず"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "す",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )
                val subMenu_ZE = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("ぜ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "せ",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )
                val subMenu_ZO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("ぞ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "そ",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )

// --- "た"行 SubMenus ---
                val subMenu_CHI = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input("ち"),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "ぢ",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )
                val subMenu_TSU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("つ"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "づ",
                            triggersMode = KeyMode.DAKUTEN
                        ),
                        TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input("っ")
                    )
                )
                val subMenu_TE = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("て"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "で",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )
                val subMenu_TO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("と"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "ど",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )

// --- "だ"行 SubMenus ---
                val subMenu_DI = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input("ぢ"),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "ち",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )
                val subMenu_DU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("づ"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "つ",
                            triggersMode = KeyMode.NORMAL
                        ),
                        TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input("っ") // 濁点モードでも「っ」は共通
                    )
                )
                val subMenu_DE = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("で"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "て",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )
                val subMenu_DO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("ど"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "と",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )

// --- "な"行 SubMenus ---
                val subMenu_NI =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.LEFT to TfbiFlickNode.Input("に")))
                val subMenu_NU =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.UP to TfbiFlickNode.Input("ぬ")))
                val subMenu_NE =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("ね")))
                val subMenu_NO =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.DOWN to TfbiFlickNode.Input("の")))

// --- "は"行 SubMenus ---
                val subMenu_HI = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input("ひ"),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "び",
                            triggersMode = KeyMode.DAKUTEN
                        ),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "ぴ",
                            triggersMode = KeyMode.HANDAKUTEN
                        )
                    )
                )
                val subMenu_FU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("ふ"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "ぶ",
                            triggersMode = KeyMode.DAKUTEN
                        ),
                        TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                            char = "ぷ",
                            triggersMode = KeyMode.HANDAKUTEN
                        )
                    )
                )
                val subMenu_HE = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("へ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "べ",
                            triggersMode = KeyMode.DAKUTEN
                        ),
                        TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                            char = "ぺ",
                            triggersMode = KeyMode.HANDAKUTEN
                        )
                    )
                )
                val subMenu_HO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("ほ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "ぼ",
                            triggersMode = KeyMode.DAKUTEN
                        ),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "ぽ",
                            triggersMode = KeyMode.HANDAKUTEN
                        )
                    )
                )

// --- "ば"行 SubMenus ---
                val subMenu_BI = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input("び"),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "ぴ",
                            triggersMode = KeyMode.HANDAKUTEN
                        ),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "ひ",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )
                val subMenu_BU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("ぶ"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "ぷ",
                            triggersMode = KeyMode.HANDAKUTEN
                        ),
                        TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                            char = "ふ",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )
                val subMenu_BE = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("べ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "ぺ",
                            triggersMode = KeyMode.HANDAKUTEN
                        ),
                        TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                            char = "へ",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )
                val subMenu_BO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("ぼ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "ぽ",
                            triggersMode = KeyMode.HANDAKUTEN
                        ),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "ほ",
                            triggersMode = KeyMode.NORMAL
                        )
                    )
                )

// --- "ぱ"行 SubMenus ---
                val subMenu_PI = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input("ぴ"),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "ひ",
                            triggersMode = KeyMode.NORMAL
                        ),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "び",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )
                val subMenu_PU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("ぷ"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                            char = "ふ",
                            triggersMode = KeyMode.NORMAL
                        ),
                        TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                            char = "ぶ",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )
                val subMenu_PE = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("ぺ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "へ",
                            triggersMode = KeyMode.NORMAL
                        ),
                        TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                            char = "べ",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )
                val subMenu_PO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("ぽ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input(
                            char = "ほ",
                            triggersMode = KeyMode.NORMAL
                        ),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input(
                            char = "ぼ",
                            triggersMode = KeyMode.DAKUTEN
                        )
                    )
                )

// --- "ま"行 SubMenus ---
                val subMenu_MI =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.LEFT to TfbiFlickNode.Input("み")))
                val subMenu_MU =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.UP to TfbiFlickNode.Input("む")))
                val subMenu_ME =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("め")))
                val subMenu_MO =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.DOWN to TfbiFlickNode.Input("も")))

// --- "や"行 SubMenus ---
                val subMenu_YA_L = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.LEFT to TfbiFlickNode.Input("("),
                        TfbiFlickDirection.DOWN_LEFT to TfbiFlickNode.Input("「")
                    )
                )
                val subMenu_YU = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("ゆ"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input("ゅ")
                    )
                )
                val subMenu_YA_R = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.RIGHT to TfbiFlickNode.Input(")"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input("」")
                    )
                )
                val subMenu_YO = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.DOWN to TfbiFlickNode.Input("よ"),
                        TfbiFlickDirection.DOWN_RIGHT to TfbiFlickNode.Input("ょ")
                    )
                )

// --- "ら"行 SubMenus ---
                val subMenu_RI =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.LEFT to TfbiFlickNode.Input("り")))
                val subMenu_RU =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.UP to TfbiFlickNode.Input("る")))
                val subMenu_RE =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("れ")))
                val subMenu_RO =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.DOWN to TfbiFlickNode.Input("ろ")))

// --- "わ"行 SubMenus ---
                val subMenu_WO =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.LEFT to TfbiFlickNode.Input("を")))
                val subMenu_N =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.UP to TfbiFlickNode.Input("ん")))
                val subMenu_CHOUON =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("ー")))
                val subMenu_NAMI =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.DOWN to TfbiFlickNode.Input("〜")))

// --- "記号" SubMenus ---
                val subMenu_KUTEN =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.LEFT to TfbiFlickNode.Input("。")))
                val subMenu_HATENA = TfbiFlickNode.SubMenu(
                    mapOf(
                        TfbiFlickDirection.UP to TfbiFlickNode.Input("？"),
                        TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input("："),
                        TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input("・")
                    )
                )
                val subMenu_BIKKURI =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.RIGHT to TfbiFlickNode.Input("！")))
                val subMenu_SANTEN =
                    TfbiFlickNode.SubMenu(mapOf(TfbiFlickDirection.DOWN to TfbiFlickNode.Input("…")))

                // --- "あ"行 Map ---
                val a_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("あ"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input("ぁ"),
                    TfbiFlickDirection.LEFT to TfbiFlickNode.SubMenu(nextMap = subMenu_A_I),
                    TfbiFlickDirection.UP to TfbiFlickNode.SubMenu(nextMap = subMenu_A_U),
                    TfbiFlickDirection.RIGHT to TfbiFlickNode.SubMenu(nextMap = subMenu_A_E),
                    TfbiFlickDirection.DOWN to TfbiFlickNode.SubMenu(nextMap = subMenu_A_O)
                )

                // --- "か"行 Map (ご提示のコード) ---
                val k_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("か"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                        char = "が",
                        triggersMode = KeyMode.DAKUTEN
                    ),
                    TfbiFlickDirection.LEFT to subMenu_KI,
                    TfbiFlickDirection.UP to subMenu_KU,
                    TfbiFlickDirection.RIGHT to subMenu_KE,
                    TfbiFlickDirection.DOWN to subMenu_KO
                )
                val g_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("が"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                        char = "か",
                        triggersMode = KeyMode.NORMAL
                    ),
                    TfbiFlickDirection.LEFT to subMenu_GI,
                    TfbiFlickDirection.UP to subMenu_GU,
                    TfbiFlickDirection.RIGHT to subMenu_GE,
                    TfbiFlickDirection.DOWN to subMenu_GO
                )

                // --- "さ"行 Map ---
                val s_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("さ"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                        char = "ざ",
                        triggersMode = KeyMode.DAKUTEN
                    ),
                    TfbiFlickDirection.LEFT to subMenu_SHI,
                    TfbiFlickDirection.UP to subMenu_SU,
                    TfbiFlickDirection.RIGHT to subMenu_SE,
                    TfbiFlickDirection.DOWN to subMenu_SO
                )
                val z_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("ざ"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                        char = "さ",
                        triggersMode = KeyMode.NORMAL
                    ),
                    TfbiFlickDirection.LEFT to subMenu_JI,
                    TfbiFlickDirection.UP to subMenu_ZU,
                    TfbiFlickDirection.RIGHT to subMenu_ZE,
                    TfbiFlickDirection.DOWN to subMenu_ZO
                )

                // --- "た"行 Map ---
                val t_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("た"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                        char = "だ",
                        triggersMode = KeyMode.DAKUTEN
                    ),
                    TfbiFlickDirection.LEFT to subMenu_CHI,
                    TfbiFlickDirection.UP to subMenu_TSU,
                    TfbiFlickDirection.RIGHT to subMenu_TE,
                    TfbiFlickDirection.DOWN to subMenu_TO
                )
                val d_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("だ"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                        char = "た",
                        triggersMode = KeyMode.NORMAL
                    ),
                    TfbiFlickDirection.LEFT to subMenu_DI,
                    TfbiFlickDirection.UP to subMenu_DU,
                    TfbiFlickDirection.RIGHT to subMenu_DE,
                    TfbiFlickDirection.DOWN to subMenu_DO
                )

                // --- "な"行 Map ---
                val n_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("な"),
                    TfbiFlickDirection.LEFT to subMenu_NI,
                    TfbiFlickDirection.UP to subMenu_NU,
                    TfbiFlickDirection.RIGHT to subMenu_NE,
                    TfbiFlickDirection.DOWN to subMenu_NO
                )

// --- "は"行 Map ---
                val h_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("は"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                        char = "ば",
                        triggersMode = KeyMode.DAKUTEN
                    ),
                    TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                        char = "ぱ",
                        triggersMode = KeyMode.HANDAKUTEN
                    ),
                    TfbiFlickDirection.LEFT to subMenu_HI,
                    TfbiFlickDirection.UP to subMenu_FU,
                    TfbiFlickDirection.RIGHT to subMenu_HE,
                    TfbiFlickDirection.DOWN to subMenu_HO
                )
                val b_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("ば"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                        char = "ぱ",
                        triggersMode = KeyMode.HANDAKUTEN
                    ),
                    TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                        char = "は",
                        triggersMode = KeyMode.NORMAL
                    ),
                    TfbiFlickDirection.LEFT to subMenu_BI,
                    TfbiFlickDirection.UP to subMenu_BU,
                    TfbiFlickDirection.RIGHT to subMenu_BE,
                    TfbiFlickDirection.DOWN to subMenu_BO
                )
                val p_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("ぱ"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input(
                        char = "は",
                        triggersMode = KeyMode.NORMAL
                    ),
                    TfbiFlickDirection.UP_LEFT to TfbiFlickNode.Input(
                        char = "ば",
                        triggersMode = KeyMode.DAKUTEN
                    ),
                    TfbiFlickDirection.LEFT to subMenu_PI,
                    TfbiFlickDirection.UP to subMenu_PU,
                    TfbiFlickDirection.RIGHT to subMenu_PE,
                    TfbiFlickDirection.DOWN to subMenu_PO
                )

                // --- "ま"行 Map ---
                val m_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("ま"),
                    TfbiFlickDirection.LEFT to subMenu_MI,
                    TfbiFlickDirection.UP to subMenu_MU,
                    TfbiFlickDirection.RIGHT to subMenu_ME,
                    TfbiFlickDirection.DOWN to subMenu_MO
                )

                // --- "や"行 Map ---
                val y_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("や"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input("ゃ"),
                    TfbiFlickDirection.LEFT to subMenu_YA_L,
                    TfbiFlickDirection.UP to subMenu_YU,
                    TfbiFlickDirection.RIGHT to subMenu_YA_R,
                    TfbiFlickDirection.DOWN to subMenu_YO
                )

                // --- "ら"行 Map ---
                val r_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("ら"),
                    TfbiFlickDirection.LEFT to subMenu_RI,
                    TfbiFlickDirection.UP to subMenu_RU,
                    TfbiFlickDirection.RIGHT to subMenu_RE,
                    TfbiFlickDirection.DOWN to subMenu_RO
                )

                // --- "わ"行 Map ---
                val w_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("わ"),
                    TfbiFlickDirection.UP_RIGHT to TfbiFlickNode.Input("ゎ"),
                    TfbiFlickDirection.LEFT to subMenu_WO,
                    TfbiFlickDirection.UP to subMenu_N,
                    TfbiFlickDirection.RIGHT to subMenu_CHOUON,
                    TfbiFlickDirection.DOWN to subMenu_NAMI
                )

                // --- "記号" Map ---
                val kigou_Map = mapOf(
                    TfbiFlickDirection.TAP to TfbiFlickNode.Input("、"),
                    TfbiFlickDirection.LEFT to subMenu_KUTEN,
                    TfbiFlickDirection.UP to subMenu_HATENA,
                    TfbiFlickDirection.RIGHT to subMenu_BIKKURI,
                    TfbiFlickDirection.DOWN to subMenu_SANTEN
                )

                val hierarchicalFlickMaps = mapOf(
                    // あ行
                    "あ" to TfbiFlickNode.StatefulKey(
                        label = "あ",
                        normalMap = a_Map,
                        dakutenMap = null,
                        handakutenMap = null
                    ),
                    // か行
                    "か" to TfbiFlickNode.StatefulKey(
                        label = "か",
                        normalMap = k_Map,
                        dakutenMap = g_Map,
                        handakutenMap = null
                    ),
                    // さ行
                    "さ" to TfbiFlickNode.StatefulKey(
                        label = "さ",
                        normalMap = s_Map,
                        dakutenMap = z_Map,
                        handakutenMap = null
                    ),
                    // た行
                    "た" to TfbiFlickNode.StatefulKey(
                        label = "た",
                        normalMap = t_Map,
                        dakutenMap = d_Map,
                        handakutenMap = null
                    ),
                    // な行
                    "な" to TfbiFlickNode.StatefulKey(
                        label = "な",
                        normalMap = n_Map,
                        dakutenMap = null,
                        handakutenMap = null
                    ),
                    // は行
                    "は" to TfbiFlickNode.StatefulKey(
                        label = "は",
                        normalMap = h_Map,
                        dakutenMap = b_Map,
                        handakutenMap = p_Map
                    ),
                    // ま行
                    "ま" to TfbiFlickNode.StatefulKey(
                        label = "ま",
                        normalMap = m_Map,
                        dakutenMap = null,
                        handakutenMap = null
                    ),
                    // や行
                    "や" to TfbiFlickNode.StatefulKey(
                        label = "や",
                        normalMap = y_Map,
                        dakutenMap = null,
                        handakutenMap = null
                    ),
                    // ら行
                    "ら" to TfbiFlickNode.StatefulKey(
                        label = "ら",
                        normalMap = r_Map,
                        dakutenMap = null,
                        handakutenMap = null
                    ),
                    // わ行
                    "わ" to TfbiFlickNode.StatefulKey(
                        label = "わ",
                        normalMap = w_Map,
                        dakutenMap = null,
                        handakutenMap = null
                    ),
                    // 記号
                    "、。?!" to TfbiFlickNode.StatefulKey(
                        label = "、",
                        normalMap = kigou_Map,
                        dakutenMap = null,
                        handakutenMap = null
                    )
                )

                return KeyboardLayout(
                    keys,
                    flickMaps,
                    5,
                    4,
                    hierarchicalFlickMaps = hierarchicalFlickMaps
                )
            }

            "sumire" -> {
                return createHiraganaLayout(isFlickDeleteEnabled)
            }

            else -> {
                val cursorLeftActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                    )
                )

                val cursorRightActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.MoveCursorRight,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.MoveCursorLeft,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.MoveCursorUp,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
                    ), FlickDirection.DOWN to FlickAction.Action(
                        KeyAction.MoveCursorDown,
                        drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
                    )
                )

                val spaceActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Space,
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                        KeyAction.InputText("^_^"), label = "^_^"
                    )
                    // この状態ではフリックアクションを定義しない
                )

                // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
                val dakutenStateFlickMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.ToggleDakuten, label = " 小゛゜"
                    ), FlickDirection.UP to FlickAction.Action(
                        KeyAction.InputText("ひらがな小文字"), label = "小"
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.InputText("濁点"), label = "゛"
                    ), FlickDirection.UP_RIGHT to FlickAction.Action(
                        KeyAction.InputText("半濁点"), label = "゜"
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

                val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
                    if (isFlickDeleteEnabled) {
                        val deleteActionMap = mapOf(
                            FlickDirection.TAP to FlickAction.Action(
                                KeyAction.Delete,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                            ), FlickDirection.UP_LEFT to FlickAction.Action(
                                KeyAction.DeleteUntilSymbol,
                                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                            )
                        )
                        mutableMapOf(
                            "CursorMoveLeft" to listOf(cursorLeftActionMap),
                            "CursorMoveRight" to listOf(cursorRightActionMap),
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
                            "Del" to listOf(deleteActionMap)
                        )
                    } else {
                        mutableMapOf(
                            "CursorMoveLeft" to listOf(cursorLeftActionMap),
                            "CursorMoveRight" to listOf(cursorRightActionMap),
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
                    }

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
        }
    }

    private fun createEnglishEffectiveLayout(
        isUpperCase: Boolean, inputStyle: String, isFlickDeleteEnabled: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "",
                0,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
            ), KeyData(
                "CursorMoveLeft",
                1,
                0,
                false,
                KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                keyType = KeyType.CROSS_FLICK,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
            ), KeyData(
                "SwitchToNumber",
                2,
                0,
                false,
                KeyAction.SwitchToNumberLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_number_select_custom
            ), KeyData(
                "SwitchToKana",
                3,
                0,
                false,
                KeyAction.SwitchToKanaLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom,
            ), KeyData(
                "@#/_", 0, 1, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "ABC", 0, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "DEF", 0, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "GHI", 1, 1, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "JKL", 1, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "MNO", 1, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "PQRS", 2, 1, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "TUV", 2, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "WXYZ", 2, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                "a/A", 3, 1, false, action = KeyAction.ToggleCase, isSpecialKey = false
            ), KeyData(
                "' \" ( )", 3, 2, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), KeyData(
                ". , ? !", 3, 3, true, keyType = when (inputStyle) {
                    "default" -> KeyType.PETAL_FLICK
                    "circle" -> KeyType.STANDARD_FLICK
                    else -> KeyType.PETAL_FLICK
                }
            ), if (isFlickDeleteEnabled) {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    deleteFlickStates[0].action,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = deleteFlickStates[0].drawableResId,
                    keyId = "delete_key",
                    keyType = KeyType.CROSS_FLICK
                )
            } else {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    KeyAction.Delete,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                )
            }, KeyData(
                "CursorMoveRight",
                1,
                4,
                false,
                KeyAction.MoveCursorRight,
                isSpecialKey = true,
                keyType = KeyType.CROSS_FLICK,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24,
            ), KeyData(
                spaceConvertStatesCursor[0].label ?: "",
                2,
                4,
                true,
                spaceConvertStatesCursor[0].action,
                dynamicStates = spaceConvertStatesCursor,
                isSpecialKey = true,
                rowSpan = 1,
                keyId = "space_convert_key",
                keyType = KeyType.CROSS_FLICK
            ), KeyData(
                enterKeyStatesCursor[0].label ?: "",
                3,
                4,
                false,
                enterKeyStatesCursor[0].action,
                dynamicStates = enterKeyStatesCursor,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = enterKeyStatesCursor[0].drawableResId,
                keyId = "enter_key"
            )
        )
        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ), FlickDirection.UP_LEFT to FlickAction.Action(
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

        val cursorLeftActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
            ), FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
            ), FlickDirection.UP to FlickAction.Action(
                KeyAction.MoveCursorUp,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
            ), FlickDirection.DOWN to FlickAction.Action(
                KeyAction.MoveCursorDown,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
            )
        )

        val cursorRightActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24
            ), FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24
            ), FlickDirection.UP to FlickAction.Action(
                KeyAction.MoveCursorUp,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_upward_alt_24
            ), FlickDirection.DOWN to FlickAction.Action(
                KeyAction.MoveCursorDown,
                drawableResId = com.kazumaproject.core.R.drawable.outline_arrow_downward_alt_24
            )
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
            if (isFlickDeleteEnabled) {
                val deleteActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Delete,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.DeleteUntilSymbol,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                    )
                )
                mutableMapOf(
                    "CursorMoveLeft" to listOf(cursorLeftActionMap),
                    "CursorMoveRight" to listOf(cursorRightActionMap),
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
                    ". , ? !" to listOf(symbols3),
                    "Del" to listOf(deleteActionMap)
                )
            } else {
                mutableMapOf(
                    "CursorMoveLeft" to listOf(cursorLeftActionMap),
                    "CursorMoveRight" to listOf(cursorRightActionMap),
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
                    ". , ? !" to listOf(symbols3),
                )
            }

        spaceConvertStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(spaceActionMap))
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    private fun createNumberEffectiveLayout(
        isFlickDeleteEnabled: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "",
                0,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
            ),
            KeyData(
                ":", 1, 0, false, isSpecialKey = true, action = KeyAction.InputText(":")
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
                "SwitchToEnglish",
                3,
                0,
                false,
                KeyAction.SwitchToEnglishLayout,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.input_mode_english_custom
            ),
            KeyData(
                label = "1\n☆♪→",
                row = 0,
                column = 1,
                isFlickable = false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                label = "4\n○*・",
                row = 1,
                column = 1,
                isFlickable = false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                label = "7\n「」:",
                row = 2,
                column = 1,
                isFlickable = false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                label = "@\n/~;",
                row = 3,
                column = 1,
                isFlickable = false,
                keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                "2\n￥$€", 0, 2, false, keyType = KeyType.STANDARD_FLICK
            ),
            KeyData(
                "3\n%°&", 0, 3, false, keyType = KeyType.STANDARD_FLICK
            ),
            KeyData("5\n+x÷", 1, 2, false, keyType = KeyType.STANDARD_FLICK),
            KeyData("6\n< = >", 1, 3, false, keyType = KeyType.STANDARD_FLICK),
            KeyData("8\n〒々〆", 2, 2, false, keyType = KeyType.STANDARD_FLICK),
            KeyData("9\n^|\\", 2, 3, false, keyType = KeyType.STANDARD_FLICK),
            KeyData("0\n〜…", 3, 2, false, keyType = KeyType.STANDARD_FLICK),
            KeyData("#\n.^,", 3, 3, false, keyType = KeyType.STANDARD_FLICK),
            if (isFlickDeleteEnabled) {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    deleteFlickStates[0].action,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = deleteFlickStates[0].drawableResId,
                    keyId = "delete_key",
                    keyType = KeyType.CROSS_FLICK
                )
            } else {
                KeyData(
                    "Del",
                    0,
                    4,
                    false,
                    KeyAction.Delete,
                    isSpecialKey = true,
                    rowSpan = 1,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                )
            },
            KeyData(
                "-", 1, 4, false, isSpecialKey = true, action = KeyAction.InputText("-")
            ),
            KeyData(
                spaceConvertStatesCursor[0].label ?: "",
                2,
                4,
                true,
                spaceConvertStatesCursor[0].action,
                dynamicStates = spaceConvertStatesCursor,
                isSpecialKey = true,
                rowSpan = 1,
                keyId = "space_convert_key",
                keyType = KeyType.CROSS_FLICK
            ),
            KeyData(
                enterKeyStatesCursor[0].label ?: "",
                3,
                4,
                false,
                enterKeyStatesCursor[0].action,
                dynamicStates = enterKeyStatesCursor,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = enterKeyStatesCursor[0].drawableResId,
                keyId = "enter_key"
            )
        )

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ), FlickDirection.UP_LEFT to FlickAction.Action(
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
                KeyAction.InputText("^_^"), label = "^_^"
            )
            // この状態ではフリックアクションを定義しない
        )

        // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
        val dakutenStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.ToggleDakuten, label = " 小゛゜"
            ), FlickDirection.UP to FlickAction.Action(
                KeyAction.InputText("ひらがな小文字"), label = "小"
            ), FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.InputText("濁点"), label = "゛"
            ), FlickDirection.UP_RIGHT to FlickAction.Action(
                KeyAction.InputText("半濁点"), label = "゜"
            )
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> =
            if (isFlickDeleteEnabled) {
                val deleteActionMap = mapOf(
                    FlickDirection.TAP to FlickAction.Action(
                        KeyAction.Delete,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                    ), FlickDirection.UP_LEFT to FlickAction.Action(
                        KeyAction.DeleteUntilSymbol,
                        drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol

                    )
                )
                mutableMapOf(
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
                    "3\n%°&" to listOf(
                        mapOf(
                            FlickDirection.TAP to FlickAction.Input("3"),
                            FlickDirection.UP_LEFT_FAR to FlickAction.Input("%"),
                            FlickDirection.UP to FlickAction.Input("°"),
                            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("&"),
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
                    "@\n/~;" to listOf(
                        mapOf(
                            FlickDirection.TAP to FlickAction.Input("@"),
                            FlickDirection.UP_LEFT_FAR to FlickAction.Input("/"),
                            FlickDirection.UP to FlickAction.Input("~"),
                            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(";"),
                        )
                    ),
                    "#\n.^," to listOf(
                        mapOf(
                            FlickDirection.TAP to FlickAction.Input("#"),
                            FlickDirection.UP_LEFT_FAR to FlickAction.Input("."),
                            FlickDirection.UP to FlickAction.Input("^"),
                            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(","),
                        )
                    ),
                    ":" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input(":"))),
                    "-" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("-"))),
                    "Del" to listOf(deleteActionMap)
                )
            } else {
                mutableMapOf(
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
                    "3\n%°&" to listOf(
                        mapOf(
                            FlickDirection.TAP to FlickAction.Input("3"),
                            FlickDirection.UP_LEFT_FAR to FlickAction.Input("%"),
                            FlickDirection.UP to FlickAction.Input("°"),
                            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("&"),
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
                    "@\n/~;" to listOf(
                        mapOf(
                            FlickDirection.TAP to FlickAction.Input("@"),
                            FlickDirection.UP_LEFT_FAR to FlickAction.Input("/"),
                            FlickDirection.UP to FlickAction.Input("~"),
                            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(";"),
                        )
                    ),
                    "#\n.^," to listOf(
                        mapOf(
                            FlickDirection.TAP to FlickAction.Input("#"),
                            FlickDirection.UP_LEFT_FAR to FlickAction.Input("."),
                            FlickDirection.UP to FlickAction.Input("^"),
                            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(","),
                        )
                    ),
                    ":" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input(":"))),
                    "-" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("-"))),
                )
            }

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

}
