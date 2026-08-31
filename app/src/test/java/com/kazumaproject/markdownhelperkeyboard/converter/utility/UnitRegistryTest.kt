package com.kazumaproject.markdownhelperkeyboard.converter.utility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

class UnitRegistryTest {
    @Test
    fun everyRegisteredUnitRoundTripsThroughItsBaseUnit() {
        val context = MathContext(40)
        val input = BigDecimal("12.345")
        UnitRegistry.Default.units.forEach { unit ->
            val roundTrip = unit.fromBase(unit.toBase(input, context), context)
            assertTrue(unit.id.value, roundTrip.subtract(input).abs() < BigDecimal("1e-30"))
        }
    }

    @Test
    fun registryIdsAreUniqueAndStable() {
        val units = UnitRegistry.Default.units
        assertEquals(units.size, units.map { it.id }.distinct().size)
        assertTrue(units.all { it.id.value.matches(Regex("[a-z0-9._-]+")) })
        assertTrue(UnitCategory.entries.all { category -> units.any { it.category == category } })
    }

    @Test
    fun everyCanonicalSymbolCanBeParsed() {
        val parser = UnitExpressionParser()
        UnitRegistry.Default.units.forEach { unit ->
            val normalized = UtilityInputNormalizer.normalize("1${unit.symbol}")
            val parsed = parser.parse(normalized)
            assertEquals(unit.id.value, unit.category, parsed?.category)
        }
    }
}
