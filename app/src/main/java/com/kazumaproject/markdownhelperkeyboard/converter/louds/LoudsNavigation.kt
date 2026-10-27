package com.kazumaproject.markdownhelperkeyboard.converter.louds

import java.util.BitSet

/** A LOUDS child must be a stored one-bit strictly after its parent. */
internal fun loudsChildPosition(bits: BitSet, parent: Int, delimiter: Int): Int {
    // Compact dictionaries can omit trailing zero words, including leaf delimiters.
    // Check select0's failure before +1, which would otherwise revisit sentinel 0.
    if (parent < 0 || delimiter < 0) return -1
    val child = delimiter + 1
    return if (child > parent && bits[child]) child else -1
}
