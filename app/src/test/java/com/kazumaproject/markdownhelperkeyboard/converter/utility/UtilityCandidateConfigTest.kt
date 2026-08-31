package com.kazumaproject.markdownhelperkeyboard.converter.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityCandidateConfigTest {
    @Test
    fun significantDigitsAcceptOneThroughFifteen() {
        assertEquals(1, Precision.SignificantDigits(1).digits)
        assertEquals(2, Precision.SignificantDigits(2).digits)
        assertEquals(15, Precision.SignificantDigits(15).digits)
        assertThrows(IllegalArgumentException::class.java) {
            Precision.SignificantDigits(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Precision.SignificantDigits(16)
        }
    }

    @Test
    fun decimalPlacesAcceptZeroThroughFifteen() {
        assertEquals(0, Precision.DecimalPlaces(0).places)
        assertEquals(15, Precision.DecimalPlaces(15).places)
        assertThrows(IllegalArgumentException::class.java) {
            Precision.DecimalPlaces(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Precision.DecimalPlaces(16)
        }
    }

    @Test
    fun defaultsStayAtFourTargetsWhileLimitIsEight() {
        assertEquals(8, UtilityCandidateConfig.MAX_TARGETS_PER_CATEGORY)
        assertTrue(UtilityCandidateConfig.defaultUnitTargets().values.all { it.size <= 4 })
    }

    @Test
    fun acceptsEightTargetsAndRejectsNine() {
        val targets = UnitRegistry.Default.units
            .filter { it.category == UnitCategory.LENGTH }
            .take(9)
            .map { UnitTargetSetting(it.id) }
        val defaults = UtilityCandidateConfig.defaultUnitTargets()

        val config = UtilityCandidateConfig(
            unitTargets = defaults + (UnitCategory.LENGTH to targets.take(8)),
        )
        assertEquals(8, config.unitTargets.getValue(UnitCategory.LENGTH).size)

        assertThrows(IllegalArgumentException::class.java) {
            UtilityCandidateConfig(
                unitTargets = defaults + (UnitCategory.LENGTH to targets),
            )
        }
    }
}
