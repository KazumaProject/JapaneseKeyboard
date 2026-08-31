package com.kazumaproject.markdownhelperkeyboard.setting_activity

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Test

class GojuonKeyboardTypeMigrationTest {
    @Test
    fun enabledReplacesTenkeyInPlaceAndKeepsSelectedMeaning() {
        val result = GojuonKeyboardTypeMigration.resolve(
            legacyGojuonEnabled = true,
            keyboardOrder = listOf(
                KeyboardType.QWERTY,
                KeyboardType.TENKEY,
                KeyboardType.SUMIRE,
            ),
            selectedPosition = 1,
        )

        assertEquals(
            listOf(KeyboardType.QWERTY, KeyboardType.GOJUON, KeyboardType.SUMIRE),
            result.keyboardOrder,
        )
        assertEquals(1, result.selectedPosition)
    }

    @Test
    fun disabledKeepsTenkeyAndPosition() {
        val result = GojuonKeyboardTypeMigration.resolve(
            legacyGojuonEnabled = false,
            keyboardOrder = listOf(KeyboardType.QWERTY, KeyboardType.TENKEY),
            selectedPosition = 1,
        )

        assertEquals(listOf(KeyboardType.QWERTY, KeyboardType.TENKEY), result.keyboardOrder)
        assertEquals(1, result.selectedPosition)
    }

    @Test
    fun duplicateTypesAreNormalizedAndSelectedTypeIsPreserved() {
        val result = GojuonKeyboardTypeMigration.resolve(
            legacyGojuonEnabled = true,
            keyboardOrder = listOf(
                KeyboardType.TENKEY,
                KeyboardType.GOJUON,
                KeyboardType.QWERTY,
                KeyboardType.TENKEY,
            ),
            selectedPosition = 3,
        )

        assertEquals(listOf(KeyboardType.GOJUON, KeyboardType.QWERTY), result.keyboardOrder)
        assertEquals(0, result.selectedPosition)
    }

    @Test
    fun orderWithoutTenkeyDoesNotGainGojuon() {
        val result = GojuonKeyboardTypeMigration.resolve(
            legacyGojuonEnabled = true,
            keyboardOrder = listOf(KeyboardType.QWERTY, KeyboardType.SUMIRE),
            selectedPosition = 0,
        )

        assertEquals(listOf(KeyboardType.QWERTY, KeyboardType.SUMIRE), result.keyboardOrder)
        assertEquals(0, result.selectedPosition)
    }

    @Test
    fun emptyOrderRemainsEmpty() {
        val result = GojuonKeyboardTypeMigration.resolve(
            legacyGojuonEnabled = true,
            keyboardOrder = emptyList(),
            selectedPosition = 9,
        )

        assertEquals(emptyList<KeyboardType>(), result.keyboardOrder)
        assertEquals(0, result.selectedPosition)
    }

    @Test
    fun migrationIsIdempotent() {
        val first = GojuonKeyboardTypeMigration.resolve(
            legacyGojuonEnabled = true,
            keyboardOrder = listOf(KeyboardType.TENKEY, KeyboardType.QWERTY),
            selectedPosition = 0,
        )
        val second = GojuonKeyboardTypeMigration.resolve(
            legacyGojuonEnabled = true,
            keyboardOrder = first.keyboardOrder,
            selectedPosition = first.selectedPosition,
        )

        assertEquals(first, second)
    }
}
