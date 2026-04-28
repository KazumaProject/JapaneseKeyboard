package com.kazumaproject.qwerty_keyboard.ui

import com.kazumaproject.core.domain.qwerty.QWERTYKey

internal object QwertyNumberKeyFlickConfig {
    private val numberKeyPreferenceKeys = mapOf(
        QWERTYKey.QWERTYKey1 to "1",
        QWERTYKey.QWERTYKey2 to "2",
        QWERTYKey.QWERTYKey3 to "3",
        QWERTYKey.QWERTYKey4 to "4",
        QWERTYKey.QWERTYKey5 to "5",
        QWERTYKey.QWERTYKey6 to "6",
        QWERTYKey.QWERTYKey7 to "7",
        QWERTYKey.QWERTYKey8 to "8",
        QWERTYKey.QWERTYKey9 to "9",
        QWERTYKey.QWERTYKey0 to "0"
    )

    fun isQwertyNumberKey(key: QWERTYKey): Boolean = key in numberKeyPreferenceKeys

    fun qwertyNumberKeyToPreferenceKey(key: QWERTYKey): String? = numberKeyPreferenceKeys[key]

    fun charForKey(key: QWERTYKey, chars: Map<String, String>): Char? {
        val preferenceKey = qwertyNumberKeyToPreferenceKey(key) ?: return null
        return chars[preferenceKey]?.trim()?.firstOrNull()
    }

    fun charForKeyWhenEnabled(
        key: QWERTYKey,
        chars: Map<String, String>,
        isNumberKeysShown: Boolean,
        isFlickEnabled: Boolean
    ): Char? {
        if (!isNumberKeysShown || !isFlickEnabled) return null
        return charForKey(key, chars)
    }
}
