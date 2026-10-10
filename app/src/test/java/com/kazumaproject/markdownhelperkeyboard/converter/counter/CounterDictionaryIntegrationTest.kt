package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.counter.CounterDictionary
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class CounterDictionaryIntegrationTest {
    private fun bytes() = listOf(File("src/main/assets/counter/counter_rules.dat"), File("app/src/main/assets/counter/counter_rules.dat")).first { it.exists() }.readBytes()
    private fun converter() = CounterDictionary.read(bytes()).converter()
    @Test fun publishedAssetAndRepresentativeRuleCasesMatch() {
        // Pinned upstream source: KazumaProject/kotlin-kana-kanji-converter@3bf65dfbd25504cbacb33ede451ad86920f3a53d.
        assertEquals("c1a470c655692ed3eb95ece7a16c6babf7916fef69192b2601498b931417a4c0", MessageDigest.getInstance("SHA-256").digest(bytes()).joinToString("") { "%02x".format(it) })
        val converter = converter()
        val cases = listOf(
            Triple("いっぽん", "hon", listOf("1本", "一本", "１本")),
            Triple("ひゃくにじゅうさんぼん", "hon", listOf("123本", "百二十三本", "１２３本")),
            Triple("さんびき", "hiki", listOf("3匹", "三匹", "３匹")),
            Triple("さんばい", "hai", listOf("3杯", "三杯", "３杯")),
            Triple("さんがい", "kai_floor", listOf("3階", "三階", "３階")),
            Triple("ひとり", "nin", listOf("1人", "一人", "１人")),
            Triple("ふたり", "nin", listOf("2人", "二人", "２人")),
            Triple("はたち", "sai", listOf("20歳", "二十歳", "２０歳")),
            Triple("ついたち", "day_calendar", listOf("1日", "一日", "１日")),
            Triple("くがつ", "month", listOf("9月", "九月", "９月")),
            Triple("ぜろまい", "mai", listOf("0枚", "零枚", "０枚")),
            Triple("イッポン", "hon", listOf("1本", "一本", "１本")),
            Triple("123ほん", "hon", listOf("123本", "百二十三本", "１２３本")),
            Triple("いっちょうえん", "en", listOf("1000000000000円", "一兆円", "１００００００００００００円")),
            Triple("いちへいほうめーとる", "square_meter", listOf("1平方メートル", "一平方メートル", "１平方メートル")),
            Triple("よじ", "time", listOf("4時", "四時", "４時", "04:00")),
            Triple("くじ", "time", listOf("9時", "九時", "９時", "09:00")),
            Triple("ごごさんじはん", "time", listOf("午後3時半", "午後三時半", "午後３時半", "15:30")),
            Triple("ごぜんじゅうにじ", "time", listOf("午前12時", "午前十二時", "午前１２時", "00:00")),
            Triple("にじゅうさんじごじゅうきゅうふんごじゅうきゅうびょう", "time", listOf("23時59分59秒", "二十三時五十九分五十九秒", "２３時５９分５９秒", "23:59:59")),
        )
        for ((input, counterId, expected) in cases) {
            val result = converter.convert(input)
            val outputs = result.candidates.filter { it.counterId == counterId }.map { it.value }
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
        for (input in listOf("いちほん", "さんひき", "よんがつ", "じゅうさんがつ", "いちにん", "ほん", "3", "いっぽんめ", "まいなすいっぽん", "いってんごほん", "にじゅうよじ", "ごごじゅうさんじ", "さんじろくじゅっぷん", "さんじはんじゅうごふん", "ひゃくひゃくまい", "いちちょうえん", "いっぺいほうめーとる")) {
            assertTrue(input, converter.convert(input).candidates.isEmpty())
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
