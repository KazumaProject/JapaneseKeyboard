package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDisplayResolverTest {

    @Test
    fun customOnlyOrderRequestedTenkeyResolvesCustom() {
        val resolution = resolveKeyboardDisplay(
            requested = KeyboardType.TENKEY,
            keyboardOrder = listOf(KeyboardType.CUSTOM)
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertEquals(0, resolution.resolvedIndex)
        assertTrue(resolution.requestedMissingFromOrder)
    }

    @Test
    fun customOnlyOrderNullRequestResolvesCustom() {
        val resolution = resolveKeyboardDisplay(
            requested = null,
            keyboardOrder = listOf(KeyboardType.CUSTOM)
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertEquals(0, resolution.resolvedIndex)
    }

    @Test
    fun customOnlyOrderSavedPositionZeroResolvesCustom() {
        val resolution = resolveKeyboardDisplay(
            requested = null,
            keyboardOrder = listOf(KeyboardType.CUSTOM),
            savedPosition = 0
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertEquals(0, resolution.resolvedIndex)
    }

    @Test
    fun customOnlyOrderSavedPositionOutOfRangeResolvesCustom() {
        val resolution = resolveKeyboardDisplay(
            requested = null,
            keyboardOrder = listOf(KeyboardType.CUSTOM),
            savedPosition = 4
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertEquals(0, resolution.resolvedIndex)
        assertTrue(resolution.savedPositionOutOfRange)
    }

    @Test
    fun multiKeyboardOrderRequestedCustomStaysCustom() {
        val resolution = resolveKeyboardDisplay(
            requested = KeyboardType.CUSTOM,
            keyboardOrder = listOf(
                KeyboardType.TENKEY,
                KeyboardType.CUSTOM,
                KeyboardType.QWERTY
            )
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertEquals(1, resolution.resolvedIndex)
    }

    @Test
    fun multiKeyboardOrderRequestedQwertyStaysQwerty() {
        val resolution = resolveKeyboardDisplay(
            requested = KeyboardType.QWERTY,
            keyboardOrder = listOf(
                KeyboardType.TENKEY,
                KeyboardType.CUSTOM,
                KeyboardType.QWERTY
            )
        )

        assertEquals(KeyboardType.QWERTY, resolution.resolvedKeyboard)
        assertEquals(2, resolution.resolvedIndex)
    }

    @Test
    fun customOnlyOrderInvalidRestoredKeyboardNormalizesToCustomIndex() {
        val resolution = resolveKeyboardDisplay(
            requested = KeyboardType.TENKEY,
            keyboardOrder = listOf(KeyboardType.CUSTOM),
            savedPosition = 0
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertEquals(0, resolution.resolvedIndex)
        assertEquals(KeyboardType.CUSTOM, resolution.keyboardOrder[resolution.resolvedIndex!!])
    }

    @Test
    fun emptyOrderFallsBackToTenkey() {
        val resolution = resolveKeyboardDisplay(
            requested = KeyboardType.CUSTOM,
            keyboardOrder = emptyList()
        )

        assertEquals(KeyboardType.TENKEY, resolution.resolvedKeyboard)
        assertNull(resolution.resolvedIndex)
        assertTrue(resolution.usedEmptyOrderFallback)
    }
}
