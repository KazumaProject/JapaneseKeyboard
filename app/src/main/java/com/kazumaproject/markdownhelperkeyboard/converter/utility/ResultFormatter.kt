package com.kazumaproject.markdownhelperkeyboard.converter.utility

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object ResultFormatter {
    private const val AUTO_DIGITS = 15
    private const val SCIENTIFIC_UPPER_EXPONENT = 12
    private const val SCIENTIFIC_LOWER_EXPONENT = -7

    fun format(value: BigDecimal, precision: Precision): String {
        if (value.signum() == 0) return "0"
        val rounded = when (precision) {
            Precision.Auto -> {
                if (value.precision() > AUTO_DIGITS) {
                    value.round(MathContext(AUTO_DIGITS, RoundingMode.HALF_UP))
                } else {
                    value
                }
            }
            Precision.Integer -> value.setScale(0, RoundingMode.HALF_UP)
            is Precision.SignificantDigits -> value.round(
                MathContext(precision.digits, RoundingMode.HALF_UP)
            )
        }.stripTrailingZeros()
        if (rounded.signum() == 0) return "0"

        val exponent = rounded.precision() - rounded.scale() - 1
        return if (exponent >= SCIENTIFIC_UPPER_EXPONENT || exponent <= SCIENTIFIC_LOWER_EXPONENT) {
            val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
            "$mantissa" + "e" + exponent
        } else {
            rounded.toPlainString()
        }
    }
}
