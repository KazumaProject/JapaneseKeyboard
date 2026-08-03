package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyCharacterCase
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewCharacterCaseTest {
    @Test
    fun qwertyLabels_followCharacterCase_withoutMutatingCanonicalLayout() {
        val keyboard = createView()
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        keyboard.setKeyboard(layout)

        assertNotNull(keyboard.findButtonWithText("q"))
        keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)
        assertNotNull(keyboard.findButtonWithText("Q"))
        assertNull(keyboard.findButtonWithText("q"))
        assertEquals("q", layout.keys.first { it.keyId == "qwerty_key_q" }.label)

        keyboard.setKeyCharacterCase(KeyCharacterCase.AS_DEFINED)
        assertNotNull(keyboard.findButtonWithText("q"))
        assertNull(keyboard.findButtonWithText("Q"))
    }

    @Test
    fun characterCaseSetBeforeLayout_isAppliedWhenKeysAreCreated() {
        val keyboard = createView()

        keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)
        keyboard.setKeyboard(KeyboardDefaultLayouts.createQwertyTemplateLayout())

        assertNotNull(keyboard.findButtonWithText("Q"))
    }

    @Test
    fun nonInputShortcutLabel_isNotChanged() {
        val keyboard = createView()
        keyboard.setKeyboard(
            KeyboardLayout(
                keys = listOf(
                    KeyData(
                        label = "paste",
                        row = 0,
                        column = 0,
                        isFlickable = false,
                        action = KeyAction.Paste,
                        isSpecialKey = false,
                        keyId = "paste",
                        keyType = KeyType.NORMAL
                    )
                ),
                flickKeyMaps = emptyMap(),
                columnCount = 1,
                rowCount = 1
            )
        )

        keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)

        assertNotNull(keyboard.findButtonWithText("paste"))
        assertNull(keyboard.findButtonWithText("PASTE"))
    }

    @Test
    fun textActionLabel_isChangedEvenWhenItUsesSpecialKeyStyling() {
        val keyboard = createView()
        keyboard.setKeyboard(
            KeyboardLayout(
                keys = listOf(
                    KeyData(
                        label = "a",
                        row = 0,
                        column = 0,
                        isFlickable = false,
                        action = KeyAction.Text("a"),
                        isSpecialKey = true,
                        keyId = "styled_text",
                        keyType = KeyType.NORMAL
                    )
                ),
                flickKeyMaps = emptyMap(),
                columnCount = 1,
                rowCount = 1
            )
        )

        keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)

        assertNotNull(keyboard.findButtonWithText("A"))
    }

    @Test
    fun englishFlickGuideTapLabel_followsCharacterCase() {
        val keyboard = createView()
        keyboard.setFlickGuideEnabled(enabled = true, allowMultiCharacterLabels = true)
        keyboard.setKeyboard(
            KeyboardDefaultLayouts.createFinalLayout(
                mode = KeyboardInputMode.ENGLISH,
                dynamicKeyStates = emptyMap(),
                inputLayoutType = "flick",
                inputStyle = "default"
            )
        )
        assertNotNull(keyboard.findButtonWithText("a"))

        keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)
        assertNotNull(keyboard.findButtonWithText("A"))
        assertNull(keyboard.findButtonWithText("a"))

        keyboard.setKeyCharacterCase(KeyCharacterCase.AS_DEFINED)
        assertNotNull(keyboard.findButtonWithText("a"))
    }

    private fun createView(): FlickKeyboardView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return FlickKeyboardView(
            ContextThemeWrapper(
                context,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
        )
    }

    private fun FlickKeyboardView.findButtonWithText(text: String): AutoSizeButton? {
        for (index in 0 until childCount) {
            val button = getChildAt(index) as? AutoSizeButton ?: continue
            if (button.text.toString() == text) return button
        }
        return null
    }
}
