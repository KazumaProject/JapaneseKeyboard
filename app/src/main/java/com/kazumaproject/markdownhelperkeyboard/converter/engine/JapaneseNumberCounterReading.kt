package com.kazumaproject.markdownhelperkeyboard.converter.engine

/** Compatibility adapter for callers supplying the counter separately. */
internal object JapaneseNumberCounterReading {
    fun parse(numberReading: String, counterReading: String): Pair<String, String>? =
        ValidatedNumber.parse(numberReading + counterReading)
            ?.takeIf { it.counter.isNotEmpty() }?.let { it.fullWidth to it.digits }
}
