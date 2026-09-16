package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.quantity.CardinalGrammar

/** Parser-owned alignment. A registered fused ending need not have a numeric/unit boundary. */
data class CardinalSource(val end: Int, val unchangedPrefix: Int, val includesUnit: Boolean = false)

sealed interface NumberReadingStructure {
    val inputStart: Int
    val inputEnd: Int
    data class Alignment(val inputStart: Int, val inputEnd: Int, val cardinalStart: Int,
                         val cardinalEnd: Int, val includesUnit: Boolean)
    data class Cardinal(override val inputStart: Int, override val inputEnd: Int, val value: Long,
                        val expression: CardinalGrammar.Expression, val alignment: List<Alignment>,
                        val unitStart: Int?) : NumberReadingStructure
    data class Atomic(override val inputStart: Int, override val inputEnd: Int, val value: Long) : NumberReadingStructure
    data class Digits(override val inputStart: Int, override val inputEnd: Int, val value: Long) : NumberReadingStructure
    data class Clock(override val inputStart: Int, override val inputEnd: Int,
                     val hour: NumberReadingStructure, val minute: NumberReadingStructure) : NumberReadingStructure
    data class Suffix(override val inputStart: Int, override val inputEnd: Int, val output: String) : NumberReadingStructure
    data class Compound(override val inputStart: Int, override val inputEnd: Int,
                        val core: NumberReadingStructure, val suffixes: List<Suffix>) : NumberReadingStructure
}
