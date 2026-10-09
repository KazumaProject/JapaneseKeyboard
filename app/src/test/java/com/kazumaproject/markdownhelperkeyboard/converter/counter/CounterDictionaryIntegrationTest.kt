package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.counter.CounterDictionary
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class CounterDictionaryIntegrationTest {
    private fun bytes() = listOf(File("src/main/assets/counter/counter_rules.dat"), File("app/src/main/assets/counter/counter_rules.dat")).first { it.exists() }.readBytes()
    private fun converter() = CounterDictionary.read(bytes()).converter()
    @Test fun publishedAssetAndAllIndependentGoldenCasesMatch() {
        assertEquals("7decaa2574df37bd341898f08a655afed0a7987f956ce7fef3bfa5a42f732e08", MessageDigest.getInstance("SHA-256").digest(bytes()).joinToString("") { "%02x".format(it) })
        val converter = converter()
        val rows = javaClass.getResourceAsStream("/counter/cases.tsv")!!.bufferedReader().readLines().drop(1).filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(106, rows.size)
        for (row in rows) {
            val columns = row.split('\t')
            val input = columns[0]
            val result = converter.convert(input)
            if (columns[1] == "@reject") assertTrue(input, result.candidates.isEmpty()) else {
                val expected = columns.drop(2).filter { it.isNotEmpty() }
                val outputs = result.candidates.filter { it.counterId == columns[1] }.map { it.value }
                assertTrue("$input: $outputs", outputs.containsAll(expected))
                assertEquals(input, expected.first(), outputs.first())
                assertEquals(input, outputs.size, outputs.distinct().size)
                assertTrue(input, expected.map(outputs::indexOf).zipWithNext().all { it.first < it.second })
                val wrapped = "ねこが" + input + "いる"
                var found = false
                converter.forEachPrefix(converter.normalizedReading(wrapped), 3) { end, prefix ->
                    if (end == 3 + input.length) {
                        found = true
                        assertEquals(input, result.candidates, prefix.candidates)
                    }
                }
                assertTrue("Missing complete prefix: $input", found)
            }
        }
    }
    @Test fun prefixOffsetsAndAppendFrontierDoNotEmitStaleNodes() {
        val converter = converter()
        val input = "ごごさんじはんにあう"
        val full = mutableListOf<Pair<Int, String>>()
        converter.forEachPrefix(input, 0) { end, result -> full += end to result.candidates.first().value }
        assertTrue(full.toString(), full.contains(7 to "午後3時半"))
        val appended = mutableListOf<Pair<Int, String>>()
        converter.forEachPrefix(input, 0, 5) { end, result -> appended += end to result.candidates.first().value }
        assertEquals(full.filter { it.first > 5 }, appended)
        assertTrue(appended.all { it.first > 5 })
    }
    @Test fun corruptionAndOverflowAreRejected() {
        val broken = bytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { CounterDictionary.read(broken) }
        val converter = converter()
        assertTrue(converter.convert("9223372036854775808ほん").candidates.isEmpty())
        assertEquals("9223372036854775807本", converter.convert("9223372036854775807ほん").candidates.first().value)
        assertTrue(converter.convert("1".repeat(129)).candidates.isEmpty())
    }
}
