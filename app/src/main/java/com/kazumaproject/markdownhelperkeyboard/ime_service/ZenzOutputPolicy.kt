package com.kazumaproject.markdownhelperkeyboard.ime_service

/**
 * Validates text crossing from the Zenz runtime into the user-visible
 * candidate pipeline.
 *
 * The U+EE00..U+EE0F range belongs to the Zenz prompt protocol. It is never
 * valid candidate text, even if a native/runtime regression accidentally
 * returns it. Completion length is deliberately not inferred from the input
 * reading: a valid conversion may be longer or shorter than its reading.
 */
internal object ZenzOutputPolicy {
    private const val PROTOCOL_MARKER_FIRST = 0xEE00
    private const val PROTOCOL_MARKER_LAST = 0xEE0F

    fun acceptedTextOrNull(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        if (!isSafeUserVisibleText(value)) return null
        return value
    }

    fun isSafeUserVisibleText(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (
                codePoint in PROTOCOL_MARKER_FIRST..PROTOCOL_MARKER_LAST ||
                codePoint < 0x20 ||
                codePoint == 0x7F ||
                codePoint == 0xFFFD ||
                codePoint in 0xD800..0xDFFF
            ) {
                return false
            }
            index += Character.charCount(codePoint)
        }
        return true
    }
}
