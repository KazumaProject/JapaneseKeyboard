package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import com.kazumaproject.core.domain.extensions.toZenkaku
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapDao
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class RomajiKanaConverterUnicodeInputTest {

    @Test
    fun handleUnicodeChar_keepsKaConversion() {
        val result = type("ka")

        assertEquals("か", result.text)
        assertEquals(Pair("か", 1), result.lastResult)
    }

    @Test
    fun handleUnicodeChar_keepsShiConversion() {
        val result = type("shi")

        assertEquals("し", result.text)
        assertEquals(Pair("し", 2), result.lastResult)
    }

    @Test
    fun handleUnicodeChar_keepsDoubleNConversion() {
        val result = type("nn")

        assertEquals("ん", result.text)
        assertEquals(Pair("ん", 1), result.lastResult)
    }

    @Test
    fun handleUnicodeChar_keepsSmallTsuBeforeKaConversion() {
        val result = type("kka")

        assertEquals("っか", result.text)
        assertEquals(Pair("か", 1), result.lastResult)
    }

    @Test
    fun handleUnicodeChar_keepsSmallTsuBeforeTeConversion() {
        val result = type("tte")

        assertEquals("って", result.text)
        assertEquals(Pair("て", 1), result.lastResult)
    }

    @Test
    fun handleUnicodeChar_commitsPendingNBeforeSymbol() {
        val result = type("n@")

        assertEquals("ん@", result.text)
        assertEquals(Pair("ん@", 1), result.lastResult)
    }

    @Test
    fun handleUnicodeChar_keepsSymbolInput() {
        val converter = newConverter()
        val result = converter.handleUnicodeChar('@'.code)

        assertEquals(Pair("@", 0), result)
    }

    @Test
    fun handleUnicodeCharZenkaku_keepsKaConversion() {
        val converter = newZenkakuConverter()
        var composing = ""
        var lastResult = Pair("", 0)

        listOf('k', 'a').forEach { char ->
            lastResult = converter.handleUnicodeCharZenkaku(char.code)
            composing = applyResult(composing, lastResult)
        }

        assertEquals("か", composing)
        assertEquals(Pair("か", 1), lastResult)
    }

    private fun type(text: String): TypedResult {
        val converter = newConverter()
        var composing = ""
        var lastResult = Pair("", 0)
        text.forEach { char ->
            lastResult = converter.handleUnicodeChar(char.code)
            composing = applyResult(composing, lastResult)
        }
        return TypedResult(composing, lastResult)
    }

    private fun applyResult(current: String, result: Pair<String, Int>): String {
        return current.dropLast(result.second).plus(result.first)
    }

    private fun newConverter(): RomajiKanaConverter {
        return RomajiKanaConverter(defaultRomajiMap())
    }

    private fun newZenkakuConverter(): RomajiKanaConverter {
        return RomajiKanaConverter(defaultRomajiMap().mapKeys { (key, _) -> key.toZenkaku() })
    }

    private fun defaultRomajiMap(): Map<String, Pair<String, Int>> {
        return RomajiMapRepository(mock<RomajiMapDao>()).getDefaultMapData()
    }

    private data class TypedResult(
        val text: String,
        val lastResult: Pair<String, Int>
    )
}
