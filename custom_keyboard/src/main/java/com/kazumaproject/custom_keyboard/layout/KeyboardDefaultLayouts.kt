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
        mode: KeyboardInputMode, dynamicKeyStates: Map<String, Int>
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
            KeyAction.Enter, "実行", com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        ),
    )

    private val dakutenToggleStates = listOf(
        FlickAction.Action(KeyAction.InputText("^_^"), label = "^_^"), FlickAction.Action(
            KeyAction.ToggleDakuten,
            label = "゛゜",
            drawableResId = com.kazumaproject.core.R.drawable.kana_small
        )
    )

    // ▼▼▼ NEW: Define states for the Space/Convert key ▼▼▼
    private val spaceConvertStates = listOf(
        FlickAction.Action(KeyAction.Space, "空白"), FlickAction.Action(KeyAction.Convert, "変換")
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
            KeyData("、。?!", 3, 3, true),
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
            FlickDirection.UP_LEFT to FlickAction.Input("を"),
            FlickDirection.UP to FlickAction.Input("ん"),
            FlickDirection.UP_RIGHT to FlickAction.Input("ー"),
            FlickDirection.DOWN to FlickAction.Input("小")
        )

        val wa_small = mapOf(
            FlickDirection.TAP to FlickAction.Input("ゎ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ゎ"),
            FlickDirection.UP_RIGHT to FlickAction.Input("~"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("〜"),
            FlickDirection.DOWN to FlickAction.Input("大")

        )
        val kuten = mapOf(
            FlickDirection.TAP to FlickAction.Input("、"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("、"),
            FlickDirection.UP_LEFT to FlickAction.Input("。"),
            FlickDirection.UP to FlickAction.Input("？"),
            FlickDirection.UP_RIGHT to FlickAction.Input("！"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("…"),
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
            KeyData("1", 0, 1, true),
            KeyData("2", 0, 2, true),
            KeyData("3", 0, 3, true),
            KeyData("4", 1, 1, true),
            KeyData("5", 1, 2, true),
            KeyData("6", 1, 3, true),
            KeyData("7", 2, 1, true),
            KeyData("8", 2, 2, true),
            KeyData("9", 2, 3, true),
            KeyData("( ) [ ]", 3, 1, true),
            KeyData("0", 3, 2, true),
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

        // --- FLICK MAP DEFINITIONS (Adjusted) ---

        // Flick maps for special keys (for consistency)
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

            "1" to listOf(
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
            "2" to listOf(
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
            "3" to listOf(
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
            "4" to listOf(
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
            "5" to listOf(
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
            "6" to listOf(
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
            "7" to listOf(
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
            // ▼▼▼ ENHANCED 8, 9, 0 keys ▼▼▼
            "8" to listOf(
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
            "9" to listOf(
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
            "0" to listOf(
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
}
