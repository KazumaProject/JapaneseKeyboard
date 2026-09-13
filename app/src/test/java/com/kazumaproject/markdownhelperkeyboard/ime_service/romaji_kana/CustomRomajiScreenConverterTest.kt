package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class CustomRomajiScreenConverterTest {
    private val rules = JsonParser.parseString(javaClass.getResource("/romaji/default-312.json")!!.readText())
        .asJsonObject.entrySet().associate { (key, value) ->
            key to (value.asJsonArray[0].asString to value.asJsonArray[1].asInt)
        }
    private fun wide(s: String) = s.map { if (it in '!'..'~') (it.code + 0xfee0).toChar() else it }.joinToString("")
    private fun type(converter: CustomRomajiScreenConverter, text: String): String =
        text.fold("") { state, c -> converter.convert(state + c) }

    @Test fun all312RulesRespectTheirLiteralMappingWhenAutomaticRulesAreOff() {
        assertEquals(312, rules.size)
        for (fullWidth in listOf(false, true)) {
            val map = if (fullWidth) rules.mapKeys { wide(it.key) } else rules
            val converter = CustomRomajiScreenConverter(map, false, false)
            for ((key, value) in map) assertEquals(key, value.first, converter.convert(key))
        }
    }

    @Test fun everyGeminatingConsonantAndEveryFollowingSyllable() {
        val consonants = "kstcpbdfghljmqrvwxyz"
        val syllables = rules.filter { (key, value) ->
            key.first() in consonants && key.last() in "aiueo" && value.first != "っ" &&
                key.zipWithNext().none { it.first == it.second }
        }
        assertTrue(syllables.size > 200)
        for (fullWidth in listOf(false, true)) for (autoN in listOf(false, true)) {
            val converter = CustomRomajiScreenConverter(if (fullWidth) rules.mapKeys { wide(it.key) } else rules, true, autoN)
            for ((key, value) in syllables) for (repeats in 1..4) {
                val input = key.first().toString().repeat(repeats) + key
                val expected = "っ".repeat(repeats) + value.first
                val actualInput = if (fullWidth) wide(input) else input
                assertEquals("$input full=$fullWidth n=$autoN", expected, type(converter, actualInput))
                assertEquals(input, expected, converter.convert(actualInput))
            }
        }
    }

    @Test fun fourSettingCombinationsHaveIndependentEffects() {
        for (sokuon in listOf(false, true)) for (n in listOf(false, true)) {
            val converter = CustomRomajiScreenConverter(rules, sokuon, n)
            assertEquals(if (sokuon) "やっぱり" else "やっあり", type(converter, "yappari"))
            assertEquals(if (sokuon) "がっこう" else "がっおう", type(converter, "gakkou"))
            assertEquals(if (sokuon) "きって" else "きっえ", type(converter, "kitte"))
            assertEquals(if (n) "かんた" else "かnた", type(converter, "kanta"))
            assertEquals(if (n) "かん" else "かn", converter.flush(type(converter, "kan")))
            assertEquals("ん", type(converter, "nn"))
            assertEquals("にゃ", type(converter, "nya"))
            assertEquals("っ", type(converter, "xtsu"))
        }
    }

    @Test fun nBoundariesAndWidthAreExplicit() {
        for (fullWidth in listOf(false, true)) {
            val converter = CustomRomajiScreenConverter(if (fullWidth) rules.mapKeys { wide(it.key) } else rules)
            fun input(s: String) = if (fullWidth) wide(s) else s
            for (c in "bcdfghjklmpqrstvwxz") assertEquals("n$c", "ん" + input(c.toString()), converter.convert(input("n$c")))
            for (suffix in listOf("", " ", "!", "1", "あ")) {
                assertEquals(input("n" + suffix), converter.convert(input("n" + suffix)))
            }
            assertEquals("ん", converter.flush(input("n")))
            assertEquals("N", converter.flush("N"))
        }
        val converter = CustomRomajiScreenConverter(emptyMap())
        assertEquals("っｐ", converter.convert("pｐ"))
        assertEquals("っp", converter.convert("ｐp"))
        assertEquals("んｋ", converter.convert("nｋ"))
        assertEquals("んk", converter.convert("ｎk"))
        assertEquals("PP", converter.convert("PP"))
    }

    @Test fun customRulesAndMissingRulesAreNotSilentlyReplaced() {
        val map = mapOf("pp" to ("★" to 2), "pa" to ("ぱ" to 2), "nn" to ("☆" to 2), "abcdef" to ("独自" to 6))
        assertEquals("★あ", CustomRomajiScreenConverter(map + ("a" to ("あ" to 1)), false).convert("ppa"))
        assertEquals("っぱ", CustomRomajiScreenConverter(map).convert("ppa"))
        assertEquals("☆", CustomRomajiScreenConverter(map).convert("nn"))
        assertEquals("独自", CustomRomajiScreenConverter(map).convert("abcdef"))
        assertEquals("pp", CustomRomajiScreenConverter(emptyMap(), false).convert("pp"))
        assertEquals("っp", CustomRomajiScreenConverter(emptyMap()).convert("pp"))
        assertEquals("っあ", CustomRomajiScreenConverter(rules).convert("tcha"))
        assertEquals("www", CustomRomajiScreenConverter(rules, false).convert("www"))
        assertEquals("っっw", CustomRomajiScreenConverter(rules).convert("www"))
    }

    @Test fun eachKeystrokeDeletionAndFlushHasExpectedState() {
        val converter = CustomRomajiScreenConverter(rules)
        var state = ""
        for ((c, expected) in "yappari".toList().zip(listOf("y", "や", "やp", "やっp", "やっぱ", "やっぱr", "やっぱり"))) {
            state = converter.convert(state + c)
            assertEquals(expected, state)
        }
        state = type(converter, "pp").dropLast(1)
        assertEquals("っ", state)
        assertEquals("っぱ", type(converter, state + "pa"))
        assertEquals("ん", converter.flush("n"))
        assertEquals("p", converter.convert("p")) // No previous pending n is retained.
    }

    @Test fun malformedLegacyConsumptionCannotLoopOrOverflow() {
        for (consume in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            val converter = CustomRomajiScreenConverter(mapOf("a" to ("あ" to consume)))
            assertEquals("!あ", converter.convert("!a"))
        }
    }

    @Test fun deterministicLongInputAndChunking() {
        val random = Random(78123)
        val words = listOf("yappari" to "やっぱり", "gakkou" to "がっこう", "kitte" to "きって", "kanta" to "かんた", "nya" to "にゃ", "xtsu" to "っ")
        val converter = CustomRomajiScreenConverter(rules)
        repeat(250) {
            val selected = List(20) { words[random.nextInt(words.size)] }
            val input = selected.joinToString(" ") { it.first }
            val expected = selected.joinToString(" ") { it.second }
            assertEquals(expected, converter.convert(input))
            assertEquals(expected, type(converter, input))
            var state = ""; var index = 0
            while (index < input.length) {
                val end = minOf(input.length, index + random.nextInt(5) + 1)
                state = converter.convert(state + input.substring(index, end)); index = end
            }
            assertEquals(expected, state)
        }
    }
}
