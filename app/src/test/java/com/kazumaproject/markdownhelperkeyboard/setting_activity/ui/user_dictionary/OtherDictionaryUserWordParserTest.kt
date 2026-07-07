package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class OtherDictionaryUserWordParserTest {

    @Test
    fun parse_google_japanese_input_pos_hints() {
        val text = """
            とうきょう	東京	地名
            はしる	走る	動詞
            はやい	速い	形容詞
            とても	迚も	副詞
            です	です	助動詞
        """.trimIndent()

        val result = OtherDictionaryUserWordParser.parse(text, OtherDictFormat.GOOGLE_JP_INPUT)

        assertEquals(5, result.size)
        assertEquals(0, result[0].posIndex)
        assertEquals(1, result[1].posIndex)
        assertEquals(2, result[2].posIndex)
        assertEquals(3, result[3].posIndex)
        assertEquals(4, result[4].posIndex)
    }

    @Test
    fun parse_microsoft_ime_swapped_columns() {
        val text = """
            東京	とうきょう	地名
            走る	はしる	動詞
            ただし	但し	接続詞
        """.trimIndent()

        val result = OtherDictionaryUserWordParser.parse(text, OtherDictFormat.MICROSOFT_IME)

        assertEquals(3, result.size)
        assertEquals("とうきょう", result[0].reading)
        assertEquals("東京", result[0].word)
        assertEquals(0, result[0].posIndex)
        assertEquals(1, result[1].posIndex)
        assertEquals(7, result[2].posIndex)
    }

    @Test
    fun parse_uses_selected_format_when_both_first_columns_are_hiragana() {
        val google = OtherDictionaryUserWordParser.parse(
            "よみ\tことば\t助詞",
            OtherDictFormat.GOOGLE_JP_INPUT,
        )
        val microsoft = OtherDictionaryUserWordParser.parse(
            "ことば\tよみ\t助詞",
            OtherDictFormat.MICROSOFT_IME,
        )

        assertEquals("よみ", google.single().reading)
        assertEquals("ことば", google.single().word)
        assertEquals("よみ", microsoft.single().reading)
        assertEquals("ことば", microsoft.single().word)
        assertEquals(5, google.single().posIndex)
        assertEquals(5, microsoft.single().posIndex)
    }

    @Test
    fun parse_normalizes_katakana_reading() {
        val result = OtherDictionaryUserWordParser.parse(
            "トウキョウ\t東京\t名詞",
            OtherDictFormat.GOOGLE_JP_INPUT,
        )

        assertEquals("とうきょう", result.single().reading)
        assertEquals(0, result.single().posIndex)
    }

    @Test
    fun parse_skips_header_line() {
        val text = """
            よみ	単語	品詞
            はしる	走る	動詞
        """.trimIndent()

        val result = OtherDictionaryUserWordParser.parse(text, OtherDictFormat.GOOGLE_JP_INPUT)

        assertEquals(1, result.size)
        assertEquals("はしる", result.single().reading)
    }

    @Test
    fun resolve_pos_index_falls_back_to_noun_for_unknown_hint() {
        assertEquals(0, OtherDictionaryUserWordParser.resolvePosIndex("未対応品詞"))
        assertEquals(0, OtherDictionaryUserWordParser.resolvePosIndex(null))
    }

    @Test
    fun resolve_pos_index_matches_specific_words_before_generic_verb() {
        assertEquals(3, OtherDictionaryUserWordParser.resolvePosIndex("adverb"))
        assertEquals(4, OtherDictionaryUserWordParser.resolvePosIndex("auxiliary verb"))
        assertEquals(2, OtherDictionaryUserWordParser.resolvePosIndex("形容動詞"))
    }
}
