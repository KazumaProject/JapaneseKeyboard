package com.kazumaproject.markdownhelperkeyboard.converter.number

import org.junit.Assert.*
import org.junit.Test

class NumberReadingDecoderTest {
    @Test fun standardCounterReadingsValidateInternalAndTerminalSoundsSeparately() {
        val valid = mapOf("しがつ" to 4L, "しちがつ" to 7L, "くがつ" to 9L, "はたち" to 20L,
            "よえん" to 4L, "よにん" to 4L, "きゅうにん" to 9L, "にじっぷん" to 20L,
            "にじゅっぷん" to 20L, "はちふん" to 8L, "はっぷん" to 8L,
            "いっけいえん" to 10000000000000000L,
            "さんびゃくろくじゅうごにち" to 365L, "さんぜんえん" to 3000L)
        valid.forEach { (reading, value) -> assertEquals(reading, value, NumberCandidateProvider.parse(reading).single().value) }
        val invalid = listOf("いっえん", "いっにん", "いちにん", "ににん", "ににち", "ににちかん", "にじゅうにち", "ろっにん", "はっがつ", "じゅっにん",
            "いちぼん", "さんぽん", "ろくぼん", "にじゅっふん", "さんふん", "くにん", "くえん",
            "にびゃくえん", "よんぜんえん", "はちひゃくえん", "さんひゃく", "はちせん",
            "にじゅうがつ", "いちじゅうえん", "いちひゃくえん", "じゅう20えん", "1まん20000えん", "1おく20000まんえん", "にがい", "ひゃっさつ", "いちいち", "いちまんにまんえん")
        invalid.forEach { assertTrue(it, NumberCandidateProvider.parse(it).isEmpty()) }
    }
    @Test fun literalMagnitudeMixesAndLongRangeUseCheckedArithmetic() {
        val valid = mapOf("1まんえん" to 10000L, "１まんえん" to 10000L,
            "12おく3せんまん4えん" to 1230000004L,
            "9223372036854775807えん" to Long.MAX_VALUE, "０００２えん" to 2L)
        valid.forEach { (reading, value) -> assertEquals(reading, value, NumberCandidateProvider.parse(reading).single().value) }
        assertEquals("0002", NumberCandidateProvider.parse("０００２えん").single().digits)
        listOf("9223372036854775808えん", "1000けいえん", "1おく2ちょうえん", "-2えん", "1.5えん")
            .forEach { assertTrue(it, NumberCandidateProvider.parse(it).isEmpty()) }
    }
    @Test fun semanticSurfaceParsingConservesValueAndCounterAliases() {
        val minutes = NumberCandidateProvider.parse("にじゅっぷん").single()
        assertEquals(20L, NumberCandidateProvider.matchSurface("2十分", minutes)?.value)
        assertNull(NumberCandidateProvider.matchSurface("210分", minutes))
        val financial = NumberCandidateProvider.parse("さんじゅっぷん").single()
        assertEquals(30L, NumberCandidateProvider.matchSurface("参拾分", financial)?.value)
        val months = NumberCandidateProvider.parse("にかげつ").single()
        for (suffix in listOf("か月", "ヶ月", "箇月", "カ月", "ケ月")) {
            assertEquals(suffix, NumberCandidateProvider.matchSurface("二$suffix", months)?.counter)
        }
        for (value in listOf(0L, 1L, 20L, 10000L, 123456789L, Long.MAX_VALUE)) {
            val number = ParsedNumber(value)
            for (style in NumberStyle.entries.filterNot { it == NumberStyle.COMMA }) {
                assertEquals("$value/$style", value, NumberReadingDecoder.surfaceValue(number.render(style)))
            }
        }
    }
    @Test fun maximalInvalidRunsNeverProduceNumericSuffixes() {
        for (input in listOf("にじゅっふんまつ", "いちまんにまんえんはらう", "9".repeat(1000) + "えん", "に".repeat(1000), "0".repeat(1000) + "1えん", "-2えん", "1.5えん")) {
            assertTrue(input.take(40), NumberCandidateProvider.spans(input).isEmpty())
        }
        assertTrue(NumberCandidateProvider.spans("きょうにいく").none { it.canSupplement })
        assertTrue(NumberCandidateProvider.spans("まごにあう").none { it.canSupplement })
        val spans = NumberCandidateProvider.spans("はつかにいちまんえんはらう")
        assertEquals(listOf(0 to 3, 4 to 10), spans.map { it.start to it.end })
    }
}
