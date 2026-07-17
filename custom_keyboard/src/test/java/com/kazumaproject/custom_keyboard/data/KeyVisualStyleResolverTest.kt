package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyVisualStyleResolverTest {

    @Test
    fun specialKeyWithSpecialColorStyleUsesSpecialSurface() {
        val key = keyData(
            isSpecialKey = true,
            specialKeyColorStyle = SpecialKeyColorStyle.SPECIAL
        )

        assertTrue(KeyVisualStyleResolver.usesSpecialSurface(key))
    }

    @Test
    fun specialKeyWithNormalColorStyleUsesNormalSurface() {
        val key = keyData(
            isSpecialKey = true,
            specialKeyColorStyle = SpecialKeyColorStyle.NORMAL
        )

        assertFalse(KeyVisualStyleResolver.usesSpecialSurface(key))
    }

    @Test
    fun normalKeyDoesNotUseSpecialSurface() {
        val key = keyData(
            isSpecialKey = false,
            specialKeyColorStyle = SpecialKeyColorStyle.SPECIAL
        )

        assertFalse(KeyVisualStyleResolver.usesSpecialSurface(key))
    }

    private fun keyData(
        isSpecialKey: Boolean,
        specialKeyColorStyle: SpecialKeyColorStyle
    ): KeyData = KeyData(
        label = "A",
        row = 0,
        column = 0,
        isFlickable = false,
        isSpecialKey = isSpecialKey,
        specialKeyColorStyle = specialKeyColorStyle
    )
}
