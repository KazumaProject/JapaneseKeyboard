package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SumireSpecialKeyPlacementOverrideApplierTest {
    @Test
    fun changesOnlySpecialKeyPlacementAndKeepsKeyData() {
        val layout = testLayout()
        val originalKeyData = layout.special("special_a").keyData
        val applied = SumireSpecialKeyPlacementOverrideApplier.apply(
            layout = layout,
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            overrides = listOf(
                placement("special_a", row = 0, column = 2)
            )
        )

        assertEquals(GridPlacement(0, 2, 1, 1), applied.special("special_a").placement)
        assertEquals(GridPlacement(0, 0, 1, 1), applied.normal("normal_a").placement)
        assertSame(originalKeyData, applied.special("special_a").keyData)
    }

    @Test
    fun normalKeyOverrideIsIgnored() {
        val layout = testLayout()
        val applied = SumireSpecialKeyPlacementOverrideApplier.apply(
            layout = layout,
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            overrides = listOf(placement("normal_a", row = 0, column = 2))
        )
        assertEquals(layout.normal("normal_a").placement, applied.normal("normal_a").placement)
    }

    @Test
    fun invalidPlacementReturnsOriginalLayout() {
        val layout = testLayout()
        val applied = SumireSpecialKeyPlacementOverrideApplier.apply(
            layout = layout,
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            overrides = listOf(placement("special_a", row = 0, column = 0))
        )
        assertSame(layout, applied)
    }

    private fun testLayout(): KeyboardLayout {
        val normal = KeyItem(
            id = "normal_a",
            keyData = KeyData("a", 0, 0, false, keyId = "normal_a"),
            placement = GridPlacement(0, 0, 1, 1)
        )
        val special = KeyItem(
            id = "special_a",
            keyData = KeyData(
                "S",
                0,
                1,
                false,
                isSpecialKey = true,
                keyId = "special_a"
            ),
            placement = GridPlacement(0, 1, 1, 1)
        )
        return KeyboardLayout(
            keys = listOf(normal.keyData, special.keyData),
            flickKeyMaps = emptyMap(),
            columnCount = 2,
            rowCount = 1,
            items = listOf(normal, special),
            columnUnitCount = 3,
            rowUnitCount = 1
        )
    }

    private fun KeyboardLayout.special(keyId: String): KeyItem {
        return items.filterIsInstance<KeyItem>().first {
            it.keyData.isSpecialKey && it.keyData.keyId == keyId
        }
    }

    private fun KeyboardLayout.normal(keyId: String): KeyItem {
        return items.filterIsInstance<KeyItem>().first {
            !it.keyData.isSpecialKey && it.keyData.keyId == keyId
        }
    }

    private fun placement(keyId: String, row: Int, column: Int) =
        SumireSpecialKeyPlacementOverrideEntity(
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            keyId = keyId,
            rowUnits = row,
            columnUnits = column,
            rowSpanUnits = 1,
            columnSpanUnits = 1,
            updatedAt = 1L
        )
}

