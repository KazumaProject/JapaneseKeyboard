package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDefaultLayoutsCenterGuideTest {

    @Test
    fun centerGuideStyleUsesTheNewKeyTypeAndExistingFiveWayMaps() {
        listOf(
            KeyboardInputMode.HIRAGANA,
            KeyboardInputMode.ENGLISH,
            KeyboardInputMode.SYMBOLS
        ).forEach { mode ->
            val layout = KeyboardDefaultLayouts.createFinalLayout(
                mode = mode,
                dynamicKeyStates = emptyMap(),
                inputLayoutType = "flick",
                inputStyle = "center-guide-flick"
            )

            val guideKeys = layout.keys.filter { it.keyType == KeyType.CENTER_GUIDE_FLICK }
            assertFalse("$mode should contain guide keys", guideKeys.isEmpty())
            assertTrue(
                "$mode should keep normal flick maps",
                layout.flickKeyMaps.isNotEmpty()
            )
        }
    }

    @Test
    fun centerGuideStyleDoesNotUseDonutLayout() {
        val guideLayout = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = emptyMap(),
            inputLayoutType = "toggle",
            inputStyle = "center-guide-flick"
        )
        val donutLayout = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = emptyMap(),
            inputLayoutType = "toggle",
            inputStyle = "sumire"
        )

        assertEquals(
            0,
            guideLayout.keys.count { it.keyType == KeyType.CIRCULAR_FLICK }
        )
        assertTrue(
            donutLayout.keys.any { it.keyType == KeyType.CIRCULAR_FLICK }
        )
    }
}
