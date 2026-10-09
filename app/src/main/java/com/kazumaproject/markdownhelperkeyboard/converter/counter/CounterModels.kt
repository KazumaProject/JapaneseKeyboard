package com.kazumaproject.counter


internal data class NumberPart(val reading: String, val value: Long, val kind: Int, val place: Long)
internal data class CounterUnit(val id: String, val surface: String, val category: String, val priority: Int, val min: Long, val max: Long, val blocked: Int)
internal data class TailRule(val profile: String, val terminal: Int, val spoken: String, val restored: String, val form: String, val replace: Boolean)
internal data class CounterException(val unit: Int, val number: Long, val reading: String, val replace: Boolean, val suffix: String)
internal data class CounterSurface(val unit: Int, val surface: String, val priority: Int)
internal data class CounterEnding(val unit: Int, val reading: String, val restored: String, val terminal: Int)
internal data class CounterSource(val numbers: List<NumberPart>, val units: List<CounterUnit>, val endings: List<CounterEnding>, val exceptions: List<CounterException>, val surfaces: List<CounterSurface>)

internal fun terminalBit(value: Int): Int = 1 shl when (value) { in 0..9 -> value; 10 -> 10; 100 -> 11; 1000 -> 12; 10000 -> 13; else -> 14 }
internal fun validNumber(part: NumberPart): Boolean = part.reading.isNotEmpty() && when (part.kind) {
    0 -> part.value == 0L && part.place == 0L
    1, 3, 4 -> part.place in listOf(1L, 10L, 100L, 1000L) && part.value / part.place in 1..9 && part.value % part.place == 0L &&
        (part.kind == 1 || (part.kind == 3 && ((part.place == 1L && part.value in listOf(1L, 8L)) || part.place == 10L)) ||
            (part.kind == 4 && ((part.place == 1L && part.value == 6L) || part.place == 100L)))
    2 -> part.value == part.place && part.place in listOf(10000L, 100000000L, 1000000000000L, 10000000000000000L)
    else -> false
}
