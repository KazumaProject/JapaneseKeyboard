package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertFalse
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
}
