package com.kazumaproject.markdownhelperkeyboard.ime_service

internal const val DEFAULT_ZENZ_DEBOUNCE_MILLIS = 300L

internal fun resolveZenzDebounceMillis(preferenceValue: Int?): Long {
    return (preferenceValue?.toLong() ?: DEFAULT_ZENZ_DEBOUNCE_MILLIS).coerceAtLeast(0L)
}
