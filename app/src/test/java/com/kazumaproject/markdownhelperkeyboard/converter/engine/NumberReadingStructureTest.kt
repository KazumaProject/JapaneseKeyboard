package com.kazumaproject.markdownhelperkeyboard.converter.engine

import org.junit.Assert.*
import org.junit.Test

class NumberReadingStructureTest {
    @Test fun ordinaryCardinalKeepsTheOriginalNumericAndUnitRanges() {
        val input = "さんびゃくごじゅうにえん"
        val proof = ValidatedNumber.parse(input)!!
        val structure = proof.inputStructure as NumberReadingStructure.Cardinal
        assertEquals(352L, structure.expression.value)
        assertEquals(input.length, structure.inputEnd)
        assertEquals("えん", input.substring(structure.unitStart!!))
        assertEquals("さんびゃくごじゅうに", input.substring(structure.alignment.first().inputStart, structure.alignment.first().inputEnd))
        assertFalse(structure.alignment.first().includesUnit)
    }

    @Test fun counterSoundChangesRetainAnExplicitFusedSourceRange() {
        val proof = ValidatedNumber.parseAll("さんぞく", NumberCandidateConfig()).single()
        val structure = proof.inputStructure as NumberReadingStructure.Cardinal
        assertEquals(3L, structure.value)
        assertNull(structure.unitStart)
        assertEquals(NumberReadingStructure.Alignment(0, "さんぞく".length, 0, "さん".length, true), structure.alignment.single())
    }

    @Test fun nativeAndRegisteredExactReadingsAreAtomicAndAuthoritative() {
        assertEquals(NumberReadingStructure.Atomic(0, 3, 2), ValidatedNumber.parse("ふたり")!!.inputStructure)
        val unit = CustomNumberUnit("special", "組", "くみ", specialReadings = listOf(SpecialNumberReading(17, "さんぐみ")))
        assertTrue(unit.isValid())
        val proof = ValidatedNumber.parseAll("さんぐみ", NumberCandidateConfig(units = listOf(unit))).single { it.customUnit != null }
        assertEquals(17L, proof.value)
        assertNull(proof.cardinalExpression)
        assertEquals(NumberReadingStructure.Atomic(0, 4, 17), proof.inputStructure)
    }

    @Test fun clockComponentsUseInputCoordinatesWithinTheWholeReading() {
        val proof = ValidatedNumber.parse("にじゅうさんじごふん")!!
        val structure = proof.inputStructure as NumberReadingStructure.Clock
        assertEquals(0, structure.hour.inputStart)
        assertEquals(proof.clockParts!!.first.reading.length, structure.minute.inputStart)
        assertEquals(proof.reading.length, structure.minute.inputEnd)
        assertEquals(23L, (structure.hour as NumberReadingStructure.Cardinal).value)
        assertEquals(5L, (structure.minute as NumberReadingStructure.Cardinal).value)
    }
}
