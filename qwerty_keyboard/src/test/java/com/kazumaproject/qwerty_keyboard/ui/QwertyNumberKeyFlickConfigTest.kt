package com.kazumaproject.qwerty_keyboard.ui

import com.kazumaproject.core.domain.qwerty.QWERTYKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QwertyNumberKeyFlickConfigTest {

    @Test
    fun charForKeyWhenEnabledReturnsConfiguredNumberKeyChar() {
        val result = QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
            key = QWERTYKey.QWERTYKey1,
            chars = mapOf("1" to "!"),
            isNumberKeysShown = true,
            isFlickEnabled = true
        )

        assertEquals('!', result)
    }

    @Test
    fun blankValueIsTreatedAsUnset() {
        val result = QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
            key = QWERTYKey.QWERTYKey1,
            chars = mapOf("1" to " "),
            isNumberKeysShown = true,
            isFlickEnabled = true
        )

        assertNull(result)
    }

    @Test
    fun nonNumberKeyDoesNotUseNumberFlickConfig() {
        val result = QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
            key = QWERTYKey.QWERTYKeyQ,
            chars = mapOf("1" to "!"),
            isNumberKeysShown = true,
            isFlickEnabled = true
        )

        assertNull(result)
    }

    @Test
    fun hiddenNumberRowDisablesNumberFlickConfig() {
        val result = QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
            key = QWERTYKey.QWERTYKey1,
            chars = mapOf("1" to "!"),
            isNumberKeysShown = false,
            isFlickEnabled = true
        )

        assertNull(result)
    }

    @Test
    fun disabledDirectionDisablesNumberFlickConfig() {
        val result = QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
            key = QWERTYKey.QWERTYKey1,
            chars = mapOf("1" to "!"),
            isNumberKeysShown = true,
            isFlickEnabled = false
        )

        assertNull(result)
    }

    @Test
    fun upAndDownMapsStayIndependent() {
        val upResult = QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
            key = QWERTYKey.QWERTYKey2,
            chars = mapOf("2" to "@"),
            isNumberKeysShown = true,
            isFlickEnabled = true
        )
        val downResult = QwertyNumberKeyFlickConfig.charForKeyWhenEnabled(
            key = QWERTYKey.QWERTYKey2,
            chars = mapOf("2" to "②"),
            isNumberKeysShown = true,
            isFlickEnabled = true
        )

        assertEquals('@', upResult)
        assertEquals('②', downResult)
    }
}
