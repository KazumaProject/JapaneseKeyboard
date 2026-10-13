package com.kazumaproject.core.domain.physical_keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalKanaMapperTest {
    private val punctuationKeysByLayout = listOf(
        "Sumire" to listOf(
            KeyEvent.KEYCODE_AT to '@',
            KeyEvent.KEYCODE_LEFT_BRACKET to '[',
            KeyEvent.KEYCODE_RIGHT_BRACKET to ']'
        ),
        "Mozc" to listOf(
            KeyEvent.KEYCODE_LEFT_BRACKET to '@',
            KeyEvent.KEYCODE_RIGHT_BRACKET to '[',
            KeyEvent.KEYCODE_BACKSLASH to ']'
        )
    )

    @Test
    fun resolve_kanaKeys() {
        assertEquals("ぬ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_1, false))
        assertEquals("ぁ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_3, true))
        assertEquals("た", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_Q, false))
        assertEquals("て", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_W, false))
        assertEquals("い", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_E, false))
        assertEquals("っ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_Z, true))
        assertEquals("め", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_SLASH, false))
        assertEquals("・", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_SLASH, true))
    }

    @Test
    fun resolve_atKeyProducesDakuten() {
        assertEquals("゛", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_AT, false))
        assertEquals("゛", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_AT, true))
    }

    @Test
    fun resolve_leftBracketProducesHandakutenOrOpeningBracket() {
        assertEquals("゜", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_LEFT_BRACKET, false))
        assertEquals("「", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_LEFT_BRACKET, true))
    }

    @Test
    fun resolve_rightBracketProducesMuOrClosingBracket() {
        assertEquals("む", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_RIGHT_BRACKET, false))
        assertEquals("」", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_RIGHT_BRACKET, true))
    }

    @Test
    fun resolve_jisKanaMarksComposeVoicedKana() {
        val ha = PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_F, false)!!
        val dakuten = PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_AT, false)!!
        val handakuten = PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_LEFT_BRACKET, false)!!

        assertEquals("ば", KanaDakutenComposer.append(ha, dakuten))
        assertEquals("ぱ", KanaDakutenComposer.append(ha, handakuten))
    }

    @Test
    fun resolve_jisPunctuationUsesBaseCharacterAcrossOsLayouts() {
        for ((layout, keys) in punctuationKeysByLayout) {
            assertEquals(
                layout,
                listOf("゛", "゜", "む"),
                keys.map { (keyCode, char) -> PhysicalKanaMapper.resolve(keyCode, false, char.code) }
            )
            assertEquals(
                "$layout shifted",
                listOf("゛", "「", "」"),
                keys.map { (keyCode, char) -> PhysicalKanaMapper.resolve(keyCode, true, char.code) }
            )
        }
    }

    @Test
    fun resolve_jisKanaMarksComposeVoicedKanaAcrossOsLayouts() {
        val ha = PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_F, false, 'f'.code)!!
        for ((layout, keys) in punctuationKeysByLayout) {
            for (isShift in listOf(false, true)) {
                val dakuten = PhysicalKanaMapper.resolve(keys[0].first, isShift, '@'.code)!!
                assertEquals("$layout shift=$isShift", "ば", KanaDakutenComposer.append(ha, dakuten))
            }
            val handakuten = PhysicalKanaMapper.resolve(keys[1].first, false, '['.code)!!
            assertEquals(layout, "ぱ", KanaDakutenComposer.append(ha, handakuten))
        }
    }

    @Test
    fun resolve_fallsBackToKeyCodeWhenBaseCharacterIsUnavailableOrUnrelated() {
        assertEquals("゛", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_AT, false, 0))
        assertEquals("た", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_Q, false, 'q'.code))
        assertEquals("「", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_LEFT_BRACKET, true, 0))
    }

    @Test
    fun resolve_japaneseDedicatedKanaKeys() {
        assertEquals("ー", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_YEN, false))
        assertEquals("ー", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_YEN, true))
        assertEquals("ろ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_RO, false))
        assertEquals("ろ", PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_RO, true))
    }

    @Test
    fun resolve_returnsNullForUnsupportedKey() {
        assertNull(PhysicalKanaMapper.resolve(KeyEvent.KEYCODE_DPAD_LEFT, false))
    }
}
