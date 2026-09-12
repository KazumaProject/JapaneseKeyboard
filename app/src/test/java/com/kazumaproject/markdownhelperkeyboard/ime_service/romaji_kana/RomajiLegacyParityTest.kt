package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RomajiLegacyParityTest {
    private val rules = JsonParser.parseString(javaClass.getResource("/romaji/default-312.json")!!.readText())
        .asJsonObject.entrySet().associate { (key, value) -> key to (value.asJsonArray[0].asString to value.asJsonArray[1].asInt) }
    private fun wide(s: String) = s.map { if (it in '!'..'~') (it.code + 0xfee0).toChar() else it }.joinToString("")
    private fun narrow(s: String) = s.map { if (it in '！'..'～') (it.code - 0xfee0).toChar() else it }.joinToString("")

    @Test fun originalHalfWidthCustomQwertyReproducesTheReportedFailure() {
        val old = RomajiKanaConverter(rules)
        var state = ""
        "yappari".forEach { state = old.convertQWERTYZenkaku(state + it) }
        assertEquals("やっあり", state)
        val fixed = CustomRomajiScreenConverter(rules)
        state = ""
        "yappari".forEach { state = fixed.convert(state + it) }
        assertEquals("やっぱり", state)
    }

    @Test fun allAlphabeticRulesAndConsonantPrefixesMatchDefaultSoftwareAtEveryKeystroke() {
        val oldDefault = RomajiKanaConverter(rules.mapKeys { wide(it.key) })
        val custom = CustomRomajiScreenConverter(rules)
        val inputs = rules.keys.filter { key -> key.all { it in 'a'..'z' } }.flatMap { listOf(it, it.first() + it) }
        assertTrue(inputs.size > 500)
        for (input in inputs) {
            var expected = ""; var actual = ""
            for ((index, c) in input.withIndex()) {
                expected = oldDefault.convertQWERTYZenkaku(expected + wide(c.toString()))
                actual = custom.convert(actual + c)
                assertEquals("$input at $index", narrow(expected), actual)
            }
        }
    }

    @Test fun physicalInputKeepsItsExistingBehaviorRegardlessOfSoftwareSettings() {
        for (fullWidth in listOf(false, true)) for (sokuon in listOf(false, true)) for (n in listOf(false, true)) {
            val map = if (fullWidth) rules.mapKeys { wide(it.key) } else rules
            val physical = RomajiKanaConverter(map)
            val screen = CustomRomajiScreenConverter(map, sokuon, n)
            for ((input, expected) in listOf("yappari" to "やっぱり", "gakkou" to "がっこう", "kanta" to "かんた")) {
                physical.clear()
                var state = ""
                input.forEach { c ->
                    screen.convert("ppnk") // Software settings cannot affect the physical session.
                    val (append, delete) = if (fullWidth) physical.handleUnicodeCharZenkaku(c.code)
                        else physical.handleUnicodeChar(c.code)
                    state = state.dropLast(delete) + append
                }
                assertEquals("$input full=$fullWidth s=$sokuon n=$n", expected, state)
            }
        }
    }
}
