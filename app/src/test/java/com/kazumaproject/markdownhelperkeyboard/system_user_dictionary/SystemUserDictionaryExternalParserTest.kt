package com.kazumaproject.markdownhelperkeyboard.system_user_dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemUserDictionaryExternalParserTest {

    @Test
    fun parse_google_japanese_input_sample() {
        val bytes = readSample("system_user_dictionary/google_jp_input_sample.tsv")
        val result = SystemUserDictionaryExternalParser.parse(bytes)

        assertEquals(3, result.entries.size)
        assertEquals("とうきょう", result.entries[0].yomi)
        assertEquals("東京", result.entries[0].tango)
        assertEquals("地名", result.entries[0].posHint)
    }

    @Test
    fun parse_ms_ime_sample_with_swapped_columns() {
        val bytes = readSample("system_user_dictionary/ms_ime_sample.tsv")
        val result = SystemUserDictionaryExternalParser.parse(bytes)

        assertEquals(3, result.entries.size)
        assertEquals("とうきょう", result.entries[0].yomi)
        assertEquals("東京", result.entries[0].tango)
        assertEquals("地名", result.entries[0].posHint)
    }

    @Test
    fun resolve_context_id_from_pos_hint() {
        val resolver = SystemUserDictionaryContextIdResolver(
            idEntries = listOf(
                IdDefEntry(1851, "名詞,一般,*,*,*,*,*"),
                IdDefEntry(1300, "名詞,固有名詞,人名,一般,*,*,*"),
                IdDefEntry(1400, "名詞,固有名詞,地域,一般,*,*,*"),
            ),
            defaultContextId = 1851,
        )

        val person = resolver.resolve("人名")
        val place = resolver.resolve("地名")
        val noun = resolver.resolve("名詞")

        assertEquals(1300 to 1300, person)
        assertEquals(1400 to 1400, place)
        assertEquals(1851 to 1851, noun)
        assertTrue(resolver.resolve(null) == (1851 to 1851))
    }

    private fun readSample(path: String): ByteArray {
        return javaClass.classLoader!!.getResourceAsStream(path)!!.use { it.readBytes() }
    }
}

