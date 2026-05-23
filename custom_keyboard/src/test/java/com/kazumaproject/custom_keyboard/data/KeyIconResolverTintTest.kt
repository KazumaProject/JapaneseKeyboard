package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
