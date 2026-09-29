package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun classicSpaceConvertToggleKeepsModifierRoleInBothStates() {
        val key = KeyData(
            label = "スペース",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Space,
            isSpecialKey = true,
            dynamicStates = listOf(
                FlickAction.Action(KeyAction.Space, "空白"),
                FlickAction.Action(KeyAction.Convert, "変換")
            )
        )

        assertEquals(
            com.kazumaproject.core.ui.skin.SkinKeyRole.MODIFIER,
            KeyVisualStyleResolver.resolveSkinKeyRole(key, keepSpaceConvertToggleSurface = true)
        )
        assertEquals(
            com.kazumaproject.core.ui.skin.SkinKeyRole.SPACE,
            KeyVisualStyleResolver.resolveSkinKeyRole(key)
        )
    }

    @Test
    fun separateSpaceKeyKeepsSpaceRoleInClassic() {
        val key = KeyData(
            label = "空白",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Space,
            isSpecialKey = true
        )

        assertEquals(
            com.kazumaproject.core.ui.skin.SkinKeyRole.SPACE,
            KeyVisualStyleResolver.resolveSkinKeyRole(key, keepSpaceConvertToggleSurface = true)
        )
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
