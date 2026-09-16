package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.LexicalPart
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.*
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QuantityScoreMachineTest {
    private fun dictionary(): SystemNgramDictionary {
        fun read(name: String) = PackedSystemNgramDictionary.read(
            checkNotNull(javaClass.classLoader!!.getResourceAsStream("ngram/$name")).use { it.readBytes() })
        return CompositeSystemNgramDictionary(listOf(read("system_ngram_v3_test.dat"), read("system_ngram_unigram_v4_test.dat")))
    }
    private fun node(text: String, left: Short = 1) = Node(left, left, 0, 0, tango = text, len = 1, yomiUsed = text, sPos = 0)

    @Test fun compactHistoryMatchesConservativeHistoryAcrossOverlapsAndLexicalParts() {
        val dictionary = dictionary()
        val conservative = object : SystemNgramDictionary by dictionary {
            override fun continuationLength(nodes: List<Node>): Int = minOf(4, nodes.size)
            override fun historyClass(node: Node): Any = dictionary.lexicalClass(node)
        }
        val compact = QuantityScoreMachine(NgramRuleScorer(emptyList()), dictionary)
        val reference = QuantityScoreMachine(NgramRuleScorer(emptyList()), conservative)
        val random = Random(482)
        val words = listOf("服", "を", "着る", "布", "で", "拭く", "洗う", "一", "二", "三", "四", "五", "語", "カワボ", "無関係").map { node(it) } +
            listOf(node("机", 1851), node("動く", 434), node("複合語").copy(lexicalParts = listOf(LexicalPart("服", 1, 1), LexicalPart("を", 1, 1))))
        val paths = listOf(
            listOf("服", "を", "着る", "カワボ", "無関係"),
            listOf("一", "二", "三", "四", "五"),
            listOf("布", "で", "机", "を", "洗う"),
            listOf("無関係", "服", "服", "を", "着る"),
        ).map { path -> path.map { node(it) } } + List(2000) { List(8) { words[random.nextInt(words.size)] } }
        for (path in paths) {
            var actual = 0; var expected = 0; var actualMask = 0; var expectedMask = 0
            for (word in path) {
                val a = compact.step(actual, compact.symbol(word))
                val b = reference.step(expected, reference.symbol(word))
                actualMask = actualMask or compact.matchedMask(a)
                expectedMask = expectedMask or reference.matchedMask(b)
                assertEquals(path.map { it.tango }.toString(), expectedMask, actualMask)
                assertEquals(reference.cost(b), compact.cost(a))
                actual = compact.next(a); expected = reference.next(b)
            }
        }
    }

    @Test fun completedUnigramAndDeadPrefixesReturnToEmptyHistory() {
        val machine = QuantityScoreMachine(NgramRuleScorer(emptyList()), dictionary())
        val unigram = machine.step(0, machine.symbol(node("カワボ")))
        assertEquals(8, machine.matchedMask(unigram))
        assertEquals(0, machine.next(unigram))
        val prefix = machine.step(0, machine.symbol(node("服")))
        assertNotEquals(0, machine.next(prefix))
        val dead = machine.step(machine.next(prefix), machine.symbol(node("無関係")))
        assertEquals(0, machine.next(dead))
    }
}
