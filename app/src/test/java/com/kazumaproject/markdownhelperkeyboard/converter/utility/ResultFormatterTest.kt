package com.kazumaproject.markdownhelperkeyboard.converter.utility

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ResultFormatterTest {
    @Test
    fun automaticPrecisionKeepsFiniteDecimalsAndUsesScientificNotationAtExtremes() {
        assertEquals("1412.5", ResultFormatter.format(BigDecimal("1412.5000"), Precision.Auto))
        assertEquals("1.23456789012346", ResultFormatter.format(BigDecimal("1.2345678901234567"), Precision.Auto))
        assertEquals("1.23e12", ResultFormatter.format(BigDecimal("1230000000000"), Precision.Auto))
        assertEquals("1.23e-7", ResultFormatter.format(BigDecimal("0.000000123"), Precision.Auto))
    }

    @Test
    fun manualPrecisionUsesHalfUpAndStripsZerosAndNegativeZero() {
        assertEquals("1000", ResultFormatter.format(BigDecimal("1234.5"), Precision.SignificantDigits(1)))
        assertEquals("1200", ResultFormatter.format(BigDecimal("1234.5"), Precision.SignificantDigits(2)))
        assertEquals("0.01", ResultFormatter.format(BigDecimal("0.01234"), Precision.SignificantDigits(1)))
        assertEquals("0.012", ResultFormatter.format(BigDecimal("0.01234"), Precision.SignificantDigits(2)))
        assertEquals("1.24", ResultFormatter.format(BigDecimal("1.235"), Precision.SignificantDigits(3)))
        assertEquals("12", ResultFormatter.format(BigDecimal("12.000"), Precision.SignificantDigits(6)))
        assertEquals("0", ResultFormatter.format(BigDecimal("-0.000"), Precision.SignificantDigits(3)))
    }

    @Suppress("DEPRECATION")
    @Test
    fun decimalPlacesUseHalfUpStripZerosAndNeverReturnNegativeZero() {
        assertEquals("1234.57", ResultFormatter.format(BigDecimal("1234.567"), Precision.DecimalPlaces(2)))
        assertEquals("1.5", ResultFormatter.format(BigDecimal("1.500"), Precision.DecimalPlaces(2)))
        assertEquals("2", ResultFormatter.format(BigDecimal("1.5"), Precision.DecimalPlaces(0)))
        assertEquals("-2", ResultFormatter.format(BigDecimal("-1.5"), Precision.DecimalPlaces(0)))
        assertEquals("0", ResultFormatter.format(BigDecimal("0.49"), Precision.DecimalPlaces(0)))
        assertEquals("0", ResultFormatter.format(BigDecimal("-0.49"), Precision.DecimalPlaces(0)))
        assertEquals("0", ResultFormatter.format(BigDecimal("-0.000"), Precision.DecimalPlaces(2)))
        assertEquals("2", ResultFormatter.format(BigDecimal("1.5"), Precision.Integer))
    }

    @Test
    fun decimalPlacesKeepScientificNotationForExtremeValues() {
        assertEquals(
            "1.234567890123e12",
            ResultFormatter.format(BigDecimal("1234567890123.4"), Precision.DecimalPlaces(0)),
        )
    }
}
