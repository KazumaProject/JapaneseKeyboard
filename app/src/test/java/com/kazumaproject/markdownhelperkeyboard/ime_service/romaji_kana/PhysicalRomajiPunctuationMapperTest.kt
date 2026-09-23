package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhysicalRomajiPunctuationMapperTest {
    @Test fun unshiftedLatinPunctuationBecomesJapaneseInBothRomajiMaps() {
        for (fullWidth in listOf(false, true)) {
            val converter = RomajiKanaConverter(emptyMap())
            val input = listOf(
                KeyEvent.KEYCODE_COMMA to ',',
                KeyEvent.KEYCODE_PERIOD to '.',
                KeyEvent.KEYCODE_COMMA to '，',
                KeyEvent.KEYCODE_PERIOD to '．',
            )
            val actual = input.joinToString("") { (keyCode, char) ->
                val unicode = PhysicalRomajiPunctuationMapper.map(keyCode, char.code, false)
                if (fullWidth) converter.handleUnicodeCharZenkaku(unicode).first
                else converter.handleUnicodeChar(unicode).first
            }
            assertEquals("fullWidth=$fullWidth", "、。、。", actual)
        }
    }

    @Test fun existingJapanesePunctuationAndShiftedSymbolsArePreserved() {
        assertEquals('、'.code, PhysicalRomajiPunctuationMapper.map(KeyEvent.KEYCODE_COMMA, '、'.code, false))
        assertEquals('。'.code, PhysicalRomajiPunctuationMapper.map(KeyEvent.KEYCODE_PERIOD, '。'.code, false))
        assertEquals('<'.code, PhysicalRomajiPunctuationMapper.map(KeyEvent.KEYCODE_COMMA, '<'.code, true))
        assertEquals('>'.code, PhysicalRomajiPunctuationMapper.map(KeyEvent.KEYCODE_PERIOD, '>'.code, true))
        assertEquals(','.code, PhysicalRomajiPunctuationMapper.map(KeyEvent.KEYCODE_COMMA, ','.code, true))
        assertEquals('.'.code, PhysicalRomajiPunctuationMapper.map(KeyEvent.KEYCODE_PERIOD, '.'.code, true))
        assertEquals('.'.code, PhysicalRomajiPunctuationMapper.map(KeyEvent.KEYCODE_NUMPAD_DOT, '.'.code, false))
    }

    @Test fun pendingNStillBecomesKanaBeforePunctuation() {
        val converter = RomajiKanaConverter(mapOf("na" to ("な" to 2)))
        assertEquals("n", converter.handleUnicodeChar('n'.code).first)
        val punctuation = PhysicalRomajiPunctuationMapper.map(KeyEvent.KEYCODE_COMMA, ','.code, false)
        assertEquals("ん、", converter.handleUnicodeChar(punctuation).first)
    }
}
