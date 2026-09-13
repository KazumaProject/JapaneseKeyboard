package com.kazumaproject.markdownhelperkeyboard.converter.engine

/** Month/day candidates have no year, so February 29 remains a valid possibility. */
internal object NumberCandidateDate {
    fun isValid(month: Int, day: Int): Boolean {
        val maximum = when (month) {
            2 -> 29
            4, 6, 9, 11 -> 30
            1, 3, 5, 7, 8, 10, 12 -> 31
            else -> return false
        }
        return day in 1..maximum
    }
}
