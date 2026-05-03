package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.copyWithItems
import com.kazumaproject.custom_keyboard.data.halfColumnSpacer
import com.kazumaproject.custom_keyboard.data.halfRowSpacer
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import com.kazumaproject.custom_keyboard.data.isPlacementOverlapping
import com.kazumaproject.custom_keyboard.data.oneColumnSpacer
import com.kazumaproject.custom_keyboard.data.oneRowSpacer
import com.kazumaproject.custom_keyboard.data.swapKeyPlacements
import com.kazumaproject.custom_keyboard.data.toKeyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QWERTY / AZERTY / Dvorak / Colemak テンプレートの基本仕様テスト。
 */
class AlphabetTemplateLayoutsTest {

    private fun assertCommonStructure(
        layout: KeyboardLayout,
        expectedColumnUnitCount: Int = 20,
        expectedColumnCount: Int = 10
    ) {
        assertEquals("columnCount", expectedColumnCount, layout.columnCount)
        assertEquals("rowCount must be 4", 4, layout.rowCount)
        assertEquals("columnUnitCount", expectedColumnUnitCount, layout.columnUnitCount)
        assertEquals("rowUnitCount must be 8", 8, layout.rowUnitCount)
        assertFalse("isRomaji should default to false", layout.isRomaji)
        assertFalse("isDirectMode should default to false", layout.isDirectMode)
        assertTrue("flickKeyMaps should be empty", layout.flickKeyMaps.isEmpty())
    }

    private fun row0Labels(layout: KeyboardLayout): List<String> =
        // Source of truth is items + GridPlacement, so order by columnUnits.
        layout.items
            .filterIsInstance<KeyItem>()
            .filter { it.placement.rowUnits == 0 && !it.keyData.isSpecialKey }
            .sortedBy { it.placement.columnUnits }
            .map { it.keyData.label }

    /**
     * Verify Shift / Delete are now on Row 2 (rowUnits = 4) with at least
     * one Spacer separating them from the character keys, and that Row 3
     * holds SwitchToNextIme + Space + Enter.
     */
    private fun assertHasSpecialKeys(layout: KeyboardLayout, prefix: String) {
        fun specialItem(action: KeyAction): KeyItem? =
            layout.items.filterIsInstance<KeyItem>()
                .firstOrNull { it.keyData.action == action && it.keyData.isSpecialKey }

        val space = specialItem(KeyAction.Space)
        val enter = specialItem(KeyAction.Enter)
        val delete = specialItem(KeyAction.Delete)
        val shift = specialItem(KeyAction.ShiftKey)
        val switchIme = specialItem(KeyAction.SwitchToNextIme)

        assertNotNull("$prefix should have Space", space)
        assertNotNull("$prefix should have Enter", enter)
        assertNotNull("$prefix should have Delete", delete)
        assertNotNull("$prefix should have ShiftKey", shift)
        assertNotNull("$prefix should have SwitchToNextIme", switchIme)

        // Shift / Delete moved to Row 2 (rowUnits = 4)
        assertEquals("$prefix Shift rowUnits", 4, shift!!.placement.rowUnits)
        assertEquals("$prefix Delete rowUnits", 4, delete!!.placement.rowUnits)

        // Row 3 (rowUnits = 6): SwitchIme | Space | Enter
        assertEquals("$prefix SwitchToNextIme rowUnits", 6, switchIme!!.placement.rowUnits)
        assertEquals("$prefix Space rowUnits", 6, space!!.placement.rowUnits)
        assertEquals("$prefix Enter rowUnits", 6, enter!!.placement.rowUnits)
    }

    private fun assertAllCharacterKeysAreNormalText(layout: KeyboardLayout) {
        layout.keys.filter { !it.isSpecialKey }.forEach { key ->
            val action = key.action
            assertTrue(
                "Character key '${key.label}' must use KeyAction.Text",
                action is KeyAction.Text && action.text == key.label
            )
            assertEquals(
                "Character key '${key.label}' must be NORMAL",
                KeyType.NORMAL, key.keyType
            )
            assertFalse("Character key '${key.label}' must not be flickable", key.isFlickable)
            assertNotNull("Character key '${key.label}' must have a keyId", key.keyId)
        }
    }

    private fun keyItem(layout: KeyboardLayout, keyId: String): KeyItem =
        layout.items.filterIsInstance<KeyItem>().first { it.keyData.keyId == keyId }

    @Test
    fun qwertyTemplate_hasExpectedRow0AndStructure() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        assertCommonStructure(layout)
        assertEquals(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            row0Labels(layout)
        )
        assertHasSpecialKeys(layout, "QWERTY")
        assertAllCharacterKeysAreNormalText(layout)
        assertNotNull(layout.keys.firstOrNull { it.keyId == "qwerty_key_q" })
        assertNotNull(layout.keys.firstOrNull { it.keyId == "qwerty_key_p" })
    }

    @Test
    fun qwertyTemplate_usesHalfUnitPlacements() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()

        assertEquals(20, layout.columnUnitCount)
        assertEquals(8, layout.rowUnitCount)

        val q = keyItem(layout, "qwerty_key_q")
        assertEquals(0, q.placement.rowUnits)
        assertEquals(0, q.placement.columnUnits)
        assertEquals(2, q.placement.rowSpanUnits)
        assertEquals(2, q.placement.columnSpanUnits)

        // Row1 has half-cell offset (startColumnUnits = 1)
        val a = keyItem(layout, "qwerty_key_a")
        assertEquals(2, a.placement.rowUnits)
        assertEquals(1, a.placement.columnUnits)

        // Row2 design: Shift(2) | spacer(1) | z..m | spacer(1) | Delete(2)
        val shift = keyItem(layout, "qwerty_shift")
        assertEquals(4, shift.placement.rowUnits)
        assertEquals(0, shift.placement.columnUnits)
        assertEquals(2, shift.placement.columnSpanUnits)

        val z = keyItem(layout, "qwerty_key_z")
        assertEquals(4, z.placement.rowUnits)
        // After Shift(2) + spacer(1) = 3 columnUnits
        assertEquals(3, z.placement.columnUnits)

        val delete = keyItem(layout, "qwerty_delete")
        assertEquals(4, delete.placement.rowUnits)
        // 2 (shift) + 1 (spacer) + 7*2 (letters z..m) + 1 (spacer) = 18
        assertEquals(18, delete.placement.columnUnits)
        assertEquals(2, delete.placement.columnSpanUnits)

        val space = keyItem(layout, "qwerty_space")
        assertEquals(6, space.placement.rowUnits)
        assertEquals(2, space.placement.columnUnits)
        assertEquals(14, space.placement.columnSpanUnits)
    }

    @Test
    fun spacerHelpers_createExpectedUnitSpacers() {
        val halfColumn = halfColumnSpacer("half_column", rowUnits = 0, columnUnits = 0)
        assertEquals(2, halfColumn.placement.rowSpanUnits)
        assertEquals(1, halfColumn.placement.columnSpanUnits)

        val oneColumn = oneColumnSpacer("one_column", rowUnits = 0, columnUnits = 0)
        assertEquals(2, oneColumn.placement.rowSpanUnits)
        assertEquals(2, oneColumn.placement.columnSpanUnits)

        val halfRow = halfRowSpacer(
            id = "half_row",
            rowUnits = 0,
            columnUnits = 0,
            columnSpanUnits = 20
        )
        assertEquals(1, halfRow.placement.rowSpanUnits)
        assertEquals(20, halfRow.placement.columnSpanUnits)

        val oneRow = oneRowSpacer(
            id = "one_row",
            rowUnits = 0,
            columnUnits = 0,
            columnSpanUnits = 20
        )
        assertEquals(2, oneRow.placement.rowSpanUnits)
        assertEquals(20, oneRow.placement.columnSpanUnits)
    }

    @Test
    fun spacerItems_doNotBecomeKeys() {
        val key = KeyData(
            label = "a",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Text("a"),
            keyId = "key_a"
        )
        val spacer = halfColumnSpacer("spacer", rowUnits = 0, columnUnits = 2)
        val layout = KeyboardLayout(
            keys = listOf(key),
            flickKeyMaps = emptyMap(),
            columnCount = 2,
            rowCount = 1,
            items = listOf(KeyItem("key_a", key, key.toKeyItem().placement), spacer),
            columnUnitCount = 4,
            rowUnitCount = 2
        )

        assertEquals(1, layout.keys.size)
        assertTrue(layout.items.any { it is SpacerItem })
        assertFalse(layout.keys.any { it.keyId == spacer.id })
    }

    @Test
    fun azertyTemplate_hasExpectedRow0AndStructure() {
        val layout = KeyboardDefaultLayouts.createAzertyTemplateLayout()
        assertCommonStructure(layout)
        assertEquals(
            listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
            row0Labels(layout)
        )
        assertHasSpecialKeys(layout, "AZERTY")
        assertAllCharacterKeysAreNormalText(layout)
        assertNotNull(layout.keys.firstOrNull { it.keyId == "azerty_key_a" })
        assertNotNull(layout.keys.firstOrNull { it.keyId == "azerty_key_m" })
    }

    @Test
    fun dvorakTemplate_hasSafeSymbolKeyIds() {
        val layout = KeyboardDefaultLayouts.createDvorakTemplateLayout()
        // Dvorak Row 2 has 10 letters; columnUnitCount widens to 24 to fit
        // Shift + 10 letters + Delete on the same row.
        assertCommonStructure(layout, expectedColumnUnitCount = 24, expectedColumnCount = 12)
        assertHasSpecialKeys(layout, "Dvorak")
        assertAllCharacterKeysAreNormalText(layout)

        // 安全名で keyId が振られていること
        assertNotNull(
            "dvorak_key_quote should exist",
            layout.keys.firstOrNull { it.keyId == "dvorak_key_quote" && it.label == "'" }
        )
        assertNotNull(
            "dvorak_key_comma should exist",
            layout.keys.firstOrNull { it.keyId == "dvorak_key_comma" && it.label == "," }
        )
        assertNotNull(
            "dvorak_key_period should exist",
            layout.keys.firstOrNull { it.keyId == "dvorak_key_period" && it.label == "." }
        )
        assertNotNull(
            "dvorak_key_semicolon should exist",
            layout.keys.firstOrNull { it.keyId == "dvorak_key_semicolon" && it.label == ";" }
        )

        // 記号そのものが keyId になっていないこと
        assertNull(
            "Dvorak should not use raw '\\'' as keyId suffix",
            layout.keys.firstOrNull { it.keyId == "dvorak_key_'" }
        )
    }

    @Test
    fun colemakTemplate_hasExpectedRow0AndStructure() {
        val layout = KeyboardDefaultLayouts.createColemakTemplateLayout()
        assertCommonStructure(layout)
        assertEquals(
            listOf("q", "w", "f", "p", "g", "j", "l", "u", "y"),
            row0Labels(layout)
        )
        assertHasSpecialKeys(layout, "Colemak")
        assertAllCharacterKeysAreNormalText(layout)
        assertNotNull(layout.keys.firstOrNull { it.keyId == "colemak_key_q" })
        assertNotNull(layout.keys.firstOrNull { it.keyId == "colemak_key_o" })
    }

    @Test
    fun allTemplates_areNotDirectModeAndNotRomaji() {
        val templates = listOf(
            KeyboardDefaultLayouts.createQwertyTemplateLayout(),
            KeyboardDefaultLayouts.createAzertyTemplateLayout(),
            KeyboardDefaultLayouts.createDvorakTemplateLayout(),
            KeyboardDefaultLayouts.createColemakTemplateLayout()
        )
        templates.forEach {
            assertFalse(it.isRomaji)
            assertFalse(it.isDirectMode)
        }
    }

    // ====================================================================
    // New tests for Spec-based templates: Row2 layout, placement validity,
    // swap, and items-based source-of-truth helpers.
    // ====================================================================

    @Test
    fun qwertyTemplate_row2_hasShiftSpacerLettersSpacerDelete() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val row2Items = layout.items
            .filter { it.placement.rowUnits == 4 }
            .sortedBy { it.placement.columnUnits }

        // Order: Shift, Spacer, z, x, c, v, b, n, m, Spacer, Delete (11 entries)
        assertEquals(11, row2Items.size)
        assertTrue(
            "First Row2 item must be Shift",
            (row2Items[0] as? KeyItem)?.keyData?.action == KeyAction.ShiftKey
        )
        assertTrue("Second Row2 item must be a SpacerItem", row2Items[1] is SpacerItem)

        val letters = row2Items.subList(2, 9).mapNotNull { (it as? KeyItem)?.keyData?.label }
        assertEquals(listOf("z", "x", "c", "v", "b", "n", "m"), letters)

        assertTrue("Tenth Row2 item must be a SpacerItem", row2Items[9] is SpacerItem)
        assertTrue(
            "Last Row2 item must be Delete",
            (row2Items[10] as? KeyItem)?.keyData?.action == KeyAction.Delete
        )
    }

    @Test
    fun qwertyTemplate_row2_placementsDoNotOverlap() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val row2 = layout.items.filter { it.placement.rowUnits == 4 }

        // Within Row2: no two non-spacer-spacer items overlap.
        for (i in row2.indices) {
            for (j in i + 1 until row2.size) {
                val a = row2[i]
                val b = row2[j]
                if (a is SpacerItem && b is SpacerItem) continue
                assertFalse(
                    "$i and $j overlap (${a.placement} vs ${b.placement})",
                    isPlacementOverlapping(a.placement, b.placement)
                )
            }
        }
    }

    @Test
    fun qwertyTemplate_layoutHasNoPlacementIssues() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        assertFalse(
            hasPlacementIssues(
                items = layout.items,
                rowUnitCount = layout.rowUnitCount,
                columnUnitCount = layout.columnUnitCount
            )
        )
    }

    @Test
    fun swapKeyPlacements_swapsPlacementsOnly_andKeepsSpacers() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()

        val qBefore = keyItem(layout, "qwerty_key_q")
        val pBefore = keyItem(layout, "qwerty_key_p")
        val spacerCountBefore = layout.items.count { it is SpacerItem }

        val swapped = layout.swapKeyPlacements("qwerty_key_q", "qwerty_key_p")

        val qAfter = keyItem(swapped, "qwerty_key_q")
        val pAfter = keyItem(swapped, "qwerty_key_p")

        // Placements are swapped
        assertEquals(pBefore.placement, qAfter.placement)
        assertEquals(qBefore.placement, pAfter.placement)

        // KeyData identity (label / action / keyId) is preserved
        assertEquals("q", qAfter.keyData.label)
        assertEquals("p", pAfter.keyData.label)
        assertEquals(KeyAction.Text("q"), qAfter.keyData.action)
        assertEquals(KeyAction.Text("p"), pAfter.keyData.action)

        // SpacerItems survived the swap
        assertEquals(spacerCountBefore, swapped.items.count { it is SpacerItem })
        assertTrue(spacerCountBefore > 0)
    }

    @Test
    fun swapKeyPlacements_swapPreservesHalfCellOffset() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()

        // Row 1 keys carry a half-cell offset (columnUnits = 1, 3, 5, ...)
        val a = keyItem(layout, "qwerty_key_a")
        assertEquals(1, a.placement.columnUnits)

        // Swap 'a' (Row1, half-cell) with 's' (Row1, also half-cell)
        val swapped = layout.swapKeyPlacements("qwerty_key_a", "qwerty_key_s")

        val aAfter = keyItem(swapped, "qwerty_key_a")
        // 's' was at columnUnits = 3 (half-cell offset), so 'a' must inherit it.
        assertEquals(3, aAfter.placement.columnUnits)

        // The bug being protected against: the legacy swap rewrote
        // KeyData.row/column → integer cells, which collapsed columnUnits 3
        // back to 2 (= column 1 * 2). Make sure that does NOT happen.
        assertTrue(
            "Half-cell offset must survive the swap",
            aAfter.placement.columnUnits % 2 == 1
        )
    }

    @Test
    fun hasPlacementIssues_detectsKeyKeyOverlap() {
        val keyA = KeyData(
            label = "a", row = 0, column = 0, isFlickable = false,
            action = KeyAction.Text("a"), keyId = "a"
        )
        val keyB = KeyData(
            label = "b", row = 0, column = 0, isFlickable = false,
            action = KeyAction.Text("b"), keyId = "b"
        )
        val items = listOf(
            KeyItem("a", keyA, GridPlacement(rowUnits = 0, columnUnits = 0)),
            KeyItem("b", keyB, GridPlacement(rowUnits = 0, columnUnits = 1))
        )
        assertTrue(
            hasPlacementIssues(items, rowUnitCount = 4, columnUnitCount = 8)
        )
    }

    @Test
    fun hasPlacementIssues_acceptsAdjacentNonOverlappingKeys() {
        val keyA = KeyData(
            label = "a", row = 0, column = 0, isFlickable = false,
            action = KeyAction.Text("a"), keyId = "a"
        )
        val keyB = KeyData(
            label = "b", row = 0, column = 0, isFlickable = false,
            action = KeyAction.Text("b"), keyId = "b"
        )
        // Adjacent at columnUnits 0..2 and 2..4 — touching, not overlapping.
        val items = listOf(
            KeyItem("a", keyA, GridPlacement(rowUnits = 0, columnUnits = 0)),
            KeyItem("b", keyB, GridPlacement(rowUnits = 0, columnUnits = 2))
        )
        assertFalse(
            hasPlacementIssues(items, rowUnitCount = 4, columnUnitCount = 8)
        )
    }

    @Test
    fun copyWithItems_keepsKeysAndItemsConsistent() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val keyItems = layout.items.filterIsInstance<KeyItem>()

        // Shuffle items: reverse order to confirm both lists are derived from items.
        val newItems = layout.items.reversed()
        val updated = layout.copyWithItems(newItems)

        // Items reflect the new order
        assertEquals(newItems, updated.items)

        // Keys are derived from items (filtered to KeyItems) — count equals
        // KeyItem count and labels match.
        val updatedKeyItems = updated.items.filterIsInstance<KeyItem>()
        assertEquals(keyItems.size, updated.keys.size)
        assertEquals(updatedKeyItems.map { it.keyData.label }, updated.keys.map { it.label })
    }
}
