package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyIconResolverTintTest {

    @Test
    fun userImageFileIconsAreNotTinted() {
        assertFalse(
            KeyIconResolver.shouldTintIcon(
                KeyIconRef(KeyIconType.USER_IMAGE_FILE, "custom_key_icons/icon.png"),
                com.kazumaproject.core.R.drawable.backspace_24px
            )
        )
    }

    @Test
    fun builtInDrawableIconsAreTinted() {
        assertTrue(
            KeyIconResolver.shouldTintIcon(
                KeyIconRef(KeyIconType.DRAWABLE_RESOURCE_NAME, "backspace_24px"),
                null
            )
        )
    }

    @Test
    fun actionFallbackIconsAreTinted() {
        assertTrue(
            KeyIconResolver.shouldTintIcon(
                null,
                com.kazumaproject.core.R.drawable.backspace_24px
            )
        )
    }

    @Test
    fun missingIconAndFallbackAreNotTinted() {
        assertFalse(KeyIconResolver.shouldTintIcon(null, null))
    }

    @Test
    fun doNothingSpecialKeyWithoutOverrideSuppressesDefaultIconAndLabel() {
        val keyData = specialKey(
            action = KeyAction.DoNothing,
            label = "何もしない",
            drawableResId = com.kazumaproject.core.R.drawable.remove
        )

        assertFalse(KeyIconResolver.hasIcon(keyData))
        assertFalse(KeyIconResolver.shouldTintIcon(keyData))
        assertEquals("", KeyIconResolver.resolvedLabelForRendering(keyData))
    }

    @Test
    fun doNothingSpecialKeyKeepsUserImageOverride() {
        val keyData = specialKey(
            action = KeyAction.DoNothing,
            label = "何もしない",
            drawableResId = com.kazumaproject.core.R.drawable.remove,
            icon = KeyIconRef(KeyIconType.USER_IMAGE_FILE, "custom_key_icons/icon.png")
        )

        assertTrue(KeyIconResolver.hasIcon(keyData))
        assertFalse(KeyIconResolver.shouldTintIcon(keyData))
    }

    @Test
    fun doNothingSpecialKeyKeepsBuiltInIconOverride() {
        val keyData = specialKey(
            action = KeyAction.DoNothing,
            label = "何もしない",
            drawableResId = com.kazumaproject.core.R.drawable.remove,
            icon = KeyIconRef(KeyIconType.DRAWABLE_RESOURCE_NAME, "outline_arrow_left_alt_24")
        )

        assertTrue(KeyIconResolver.hasIcon(keyData))
        assertTrue(KeyIconResolver.shouldTintIcon(keyData))
    }

    @Test
    fun nonDoNothingSpecialKeyStillUsesActionFallbackIconAndLabel() {
        val keyData = specialKey(
            action = KeyAction.Delete,
            label = "Delete",
            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
        )

        assertTrue(KeyIconResolver.hasIcon(keyData))
        assertTrue(KeyIconResolver.shouldTintIcon(keyData))
        assertEquals("Delete", KeyIconResolver.resolvedLabelForRendering(keyData))
    }

    @Test
    fun unlabeledMixedCursorTapsRenderTheirDefaultIcons() {
        val icons = mapOf(
            KeyAction.MoveCursorUp to com.kazumaproject.core.R.drawable.outline_arrow_drop_up_24,
            KeyAction.MoveCursorDown to com.kazumaproject.core.R.drawable.outline_arrow_drop_down_24,
            KeyAction.MoveCursorLeft to com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
            KeyAction.MoveCursorRight to com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
            KeyAction.Home to com.kazumaproject.core.R.drawable.keyboard_home_24px
        )
        for ((action, icon) in icons) {
            val key = mixedKey(action)
            val resolved = resolveMixedTap(key, FlickAction.Action(action))
            assertEquals(icon, resolved.drawableResId)
            assertTrue(KeyIconResolver.hasIcon(resolved))
            assertTrue(KeyIconResolver.shouldTintIcon(resolved))
        }
    }

    @Test
    fun mixedTapPreservesCustomLabelAndExplicitIconPrecedence() {
        val key = mixedKey(KeyAction.MoveCursorUp, label = "Up")
        val icon = KeyIconRef(KeyIconType.DRAWABLE_RESOURCE_NAME, "keyboard_home_24px")
        val labeled = resolveMixedTap(key, FlickAction.Action(KeyAction.MoveCursorUp))
        assertFalse(KeyIconResolver.hasIcon(labeled))
        assertEquals("Up", KeyIconResolver.resolvedLabelForRendering(labeled))

        val overridden = resolveMixedTap(key, FlickAction.Action(KeyAction.MoveCursorUp, icon = icon))
        assertTrue(KeyIconResolver.hasIcon(overridden))
        assertEquals(icon, overridden.icon)
    }

    @Test
    fun mixedTextTapDoesNotUseTheCursorIconFromAnotherSlot() {
        val key = mixedKey(KeyAction.Text("a"))
        val resolved = resolveMixedTap(key, FlickAction.Input("a"))
        assertFalse(KeyIconResolver.hasIcon(resolved))
        assertEquals("a", KeyIconResolver.resolvedLabelForRendering(resolved))
    }

    @Test
    fun mixedTapWithoutAnIconUsesItsActionLabel() {
        val key = mixedKey(KeyAction.End)
        val resolved = resolveMixedTap(key, FlickAction.Action(KeyAction.End))
        assertNull(resolved.drawableResId)
        assertFalse(KeyIconResolver.hasIcon(resolved))
        assertEquals("End", KeyIconResolver.resolvedLabelForRendering(resolved))
    }

    private fun mixedKey(action: KeyAction, label: String = "") = KeyData(
        label = label, row = 0, column = 0, isFlickable = true,
        action = action, keyId = "mixed", keyType = KeyType.MIXED_FLICK
    )

    private fun resolveMixedTap(key: KeyData, tap: FlickAction): KeyData {
        val slots = mapOf(FlickDirection.TAP to tap, FlickDirection.UP to FlickAction.Action(KeyAction.MoveCursorUp))
        val layout = KeyboardLayout(listOf(key), mapOf("mixed" to listOf(slots)), 1, 1)
        return KeyIconResolver.withMixedFlickTapIcon(key, layout)
    }

    private fun specialKey(
        action: KeyAction,
        label: String,
        drawableResId: Int?,
        icon: KeyIconRef? = null
    ): KeyData =
        KeyData(
            label = label,
            row = 0,
            column = 0,
            isFlickable = false,
            action = action,
            drawableResId = drawableResId,
            icon = icon,
            isSpecialKey = true
        )
}
