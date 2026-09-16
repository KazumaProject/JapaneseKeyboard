package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.quantity.QuantityDictionary
import com.kazumaproject.quantity.QuantityDictionary.Feature
import com.kazumaproject.quantity.QuantityDictionary.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuantityRuleMatchingTest {
    private fun reference(rules: List<List<Feature>>, tokens: List<Token>, ends: List<List<Int>>,
                          filter: (List<Feature>) -> Boolean, verify: (String, String, String) -> Boolean): Int {
        fun match(rule: List<Feature>, feature: Int, at: Int): Boolean {
            if (feature == rule.size) return true
            if (at == tokens.size) return false
            return when (val f = rule[feature]) {
                is Feature.Word -> tokens[at].text == f.text && match(rule, feature + 1, at + 1)
                is Feature.Quantity -> {
                    var reading = ""
                    var text = ""
                    var found = false
                    for (end in at until tokens.size) {
                        if (tokens[end].protected) break
                        reading += tokens[end].reading
                        text += tokens[end].text
                        if (reading.length > 255) break
                        if (end in ends[at] && verify(reading, text, f.unit) && match(rule, feature + 1, end + 1)) {
                            found = true
                            break
                        }
                    }
                    found
                }
            }
        }
        return rules.filter(filter).filter { rule -> tokens.indices.any { match(rule, 0, it) } }.maxOfOrNull { it.size } ?: 0
    }

    @Test fun sharedPrefixesMatchExhaustiveRuleTraversal() {
        val random = Random(1020)
        val features = listOf(Feature.Word("2"), Feature.Word("個"), Feature.Word("買う"), Feature.Quantity("個"), Feature.Quantity("@registered"))
        repeat(1000) {
            val rules = List(30) { List(random.nextInt(2, 6)) { features.random(random) } }.distinct()
            val tokens = List(random.nextInt(1, 15)) {
                val text = listOf("2", "個", "買う").random(random)
                Token(text, text, random.nextInt(10) == 0)
            }
            val ends = tokens.indices.map { at -> (at until tokens.size).filter { random.nextBoolean() } }
            val verify = { reading: String, text: String, unit: String ->
                reading == text && (text == "2個" || unit == "@registered" && text == "2")
            }
            val dictionary = QuantityDictionary(rules, emptyList())
            for (contextual in listOf(false, true)) {
                val filter: (List<Feature>) -> Boolean = { !contextual || it.first() is Feature.Word }
                assertEquals(reference(rules, tokens, ends, filter, verify),
                    dictionary.matchStrength(tokens, filter, { ends[it] }) { _, _, reading, text, unit -> verify(reading, text, unit) })
            }
        }
    }

    @Test fun spanConstructionAndVerificationAreSharedAcrossRules() {
        val dictionary = QuantityDictionary((1..100).map { listOf(Feature.Quantity("個"), Feature.Word("word$it")) }, emptyList())
        var endsCalls = 0
        var verifyCalls = 0
        assertEquals(2, dictionary.matchStrength(listOf(Token("2個", "にこ"), Token("word100", "ことば")),
            quantityEnds = { at -> endsCalls++; if (at == 0) listOf(0) else emptyList() }) { _, _, _, _, _ -> verifyCalls++; true })
        assertTrue(endsCalls <= 2)
        assertEquals(1, verifyCalls)
    }
}
