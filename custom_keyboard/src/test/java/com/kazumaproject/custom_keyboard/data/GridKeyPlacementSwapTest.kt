package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GridKeyPlacementSwapTest {

    @Test
    fun swapGridKeyPlacementsKeepingKeyDataInSync_keepsOrdinaryGridLayoutNonFlexible() {
        val layout = gridLayout()

        val swapped = layout.swapGridKeyPlacementsKeepingKeyDataInSync("key_a", "key_b")

        assertFalse(swapped.usesFlexiblePlacement())
    }

    @Test
    fun swapGridKeyPlacementsKeepingKeyDataInSync_syncsPlacementAndKeyDataCellFields() {
        val layout = gridLayout()
        val aBefore = keyItem(layout, "key_a")
        val bBefore = keyItem(layout, "key_b")

        val swapped = layout.swapGridKeyPlacementsKeepingKeyDataInSync("key_a", "key_b")

        val aAfter = keyItem(swapped, "key_a")
        val bAfter = keyItem(swapped, "key_b")

        assertEquals(bBefore.placement, aAfter.placement)
        assertEquals(bBefore.placement.rowUnits / 2, aAfter.keyData.row)
        assertEquals(bBefore.placement.columnUnits / 2, aAfter.keyData.column)
        assertEquals(bBefore.placement.rowSpanUnits / 2, aAfter.keyData.rowSpan)
        assertEquals(bBefore.placement.columnSpanUnits / 2, aAfter.keyData.colSpan)

        assertEquals(aBefore.placement, bAfter.placement)
        assertEquals(aBefore.placement.rowUnits / 2, bAfter.keyData.row)
        assertEquals(aBefore.placement.columnUnits / 2, bAfter.keyData.column)
        assertEquals(aBefore.placement.rowSpanUnits / 2, bAfter.keyData.rowSpan)
        assertEquals(aBefore.placement.columnSpanUnits / 2, bAfter.keyData.colSpan)

        assertEquals(swapped.items.filterIsInstance<KeyItem>().map { it.keyData }, swapped.keys)
    }

    private fun gridLayout(): KeyboardLayout =
        KeyboardLayout(
            keys = listOf(
                KeyData(
                    label = "a",
                    row = 0,
                    column = 0,
                    isFlickable = false,
                    action = KeyAction.Text("a"),
                    rowSpan = 1,
                    colSpan = 1,
                    keyId = "key_a",
                    keyType = KeyType.NORMAL
                ),
                KeyData(
                    label = "b",
                    row = 0,
                    column = 1,
                    isFlickable = false,
                    action = KeyAction.Text("b"),
                    rowSpan = 1,
                    colSpan = 2,
                    keyId = "key_b",
                    keyType = KeyType.NORMAL
                )
            ),
            flickKeyMaps = emptyMap(),
            columnCount = 3,
            rowCount = 1
        )

    private fun keyItem(layout: KeyboardLayout, keyId: String): KeyItem =
        layout.items.filterIsInstance<KeyItem>().first { it.keyData.keyId == keyId }
}
