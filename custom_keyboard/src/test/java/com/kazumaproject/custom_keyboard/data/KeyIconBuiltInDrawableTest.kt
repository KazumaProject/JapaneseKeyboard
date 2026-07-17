package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyIconBuiltInDrawableTest {

    @Test
    fun arrowAltDrawablesAreAllowedAndResolvable() {
        assertTrue(KeyIconBuiltInDrawable.isAllowed("outline_arrow_left_alt_24"))
        assertTrue(KeyIconBuiltInDrawable.isAllowed("outline_arrow_right_alt_24"))
        assertEquals(
            com.kazumaproject.core.R.drawable.outline_arrow_left_alt_24,
            KeyIconBuiltInDrawable.resolve("outline_arrow_left_alt_24")
        )
        assertEquals(
            com.kazumaproject.core.R.drawable.outline_arrow_right_alt_24,
            KeyIconBuiltInDrawable.resolve("outline_arrow_right_alt_24")
        )
    }

    @Test
    fun normalizedAndLegacyKanaCaseDrawablesAreAllowed() {
        assertTrue(KeyIconBuiltInDrawable.isAllowed("custom_key_kana_case_24"))
        assertTrue(KeyIconBuiltInDrawable.isAllowed("custom_key_english_case_24"))
        assertTrue(KeyIconBuiltInDrawable.isAllowed("kana_small"))
        assertTrue(KeyIconBuiltInDrawable.isAllowed("kana_small_custom"))
        assertTrue(KeyIconBuiltInDrawable.isAllowed("english_small"))
        assertEquals(
            com.kazumaproject.core.R.drawable.custom_key_kana_case_24,
            KeyIconBuiltInDrawable.resolve("custom_key_kana_case_24")
        )
        assertEquals(
            com.kazumaproject.core.R.drawable.custom_key_english_case_24,
            KeyIconBuiltInDrawable.resolve("custom_key_english_case_24")
        )
    }

    @Test
    fun unknownDrawableResourceNameIsNotAllowed() {
        assertFalse(KeyIconBuiltInDrawable.isAllowed("missing_drawable_name"))
    }
}
