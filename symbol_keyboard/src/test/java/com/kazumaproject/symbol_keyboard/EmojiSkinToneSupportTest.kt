package com.kazumaproject.symbol_keyboard

import com.kazumaproject.domain.EmojiSkinToneSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSkinToneSupportTest {

    @Test
    fun skinToneVariants_returnsDefaultAndFiveToneVariants() {
        val variants = EmojiSkinToneSupport.skinToneVariants("👍")

        assertEquals(listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), variants)
    }

    @Test
    fun skinToneVariants_appliesToneBeforeZwjSuffix() {
        val variants = EmojiSkinToneSupport.skinToneVariants("👨‍💻")

        assertEquals("👨🏽‍💻", variants[3])
    }

    @Test
    fun withSkinTone_appliesSelectedToneToToneableEmoji() {
        assertEquals(
            "👋🏿",
            EmojiSkinToneSupport.withSkinTone("👋", EmojiSkinToneSupport.DARK_SKIN_TONE)
        )
    }

    @Test
    fun withSkinTone_returnsBaseEmojiForDefaultTone() {
        assertEquals(
            "👋",
            EmojiSkinToneSupport.withSkinTone("👋🏿", EmojiSkinToneSupport.DEFAULT_SKIN_TONE)
        )
    }

    @Test
    fun skinToneValueFromEmoji_detectsSelectedTone() {
        assertEquals(
            EmojiSkinToneSupport.MEDIUM_DARK_SKIN_TONE,
            EmojiSkinToneSupport.skinToneValueFromEmoji("👍🏾")
        )
    }

    @Test
    fun hasSkinToneVariants_ignoresPlainFaceEmoji() {
        assertFalse(EmojiSkinToneSupport.hasSkinToneVariants("😀"))
    }

    @Test
    fun hasSkinToneVariants_detectsToneableGestureWithVariationSelector() {
        assertTrue(EmojiSkinToneSupport.hasSkinToneVariants("✌️"))
        assertEquals("✌🏽", EmojiSkinToneSupport.skinToneVariants("✌️")[3])
    }
}
