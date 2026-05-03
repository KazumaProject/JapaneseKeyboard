package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.halfColumnSpacer
import com.kazumaproject.custom_keyboard.data.halfRowSpacer
import com.kazumaproject.custom_keyboard.data.oneColumnSpacer
import com.kazumaproject.custom_keyboard.data.oneRowSpacer
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

    private fun assertCommonStructure(layout: KeyboardLayout) {
        assertEquals("columnCount must be 10", 10, layout.columnCount)
        assertEquals("rowCount must be 4", 4, layout.rowCount)
        assertEquals("columnUnitCount must be 20", 20, layout.columnUnitCount)
        assertEquals("rowUnitCount must be 8", 8, layout.rowUnitCount)
        assertFalse("isRomaji should default to false", layout.isRomaji)
        assertFalse("isDirectMode should default to false", layout.isDirectMode)
        assertTrue("flickKeyMaps should be empty", layout.flickKeyMaps.isEmpty())
    }

    private fun row0Labels(layout: KeyboardLayout): List<String> =
        layout.keys
            .filter { it.row == 0 && !it.isSpecialKey }
            .sortedBy { it.column }
            .map { it.label }

    private fun assertHasSpecialKeys(layout: KeyboardLayout, prefix: String) {
        fun specialKey(action: KeyAction): KeyData? =
            layout.keys.firstOrNull { it.action == action && it.isSpecialKey }

        val space = specialKey(KeyAction.Space)
        val enter = specialKey(KeyAction.Enter)
        val delete = specialKey(KeyAction.Delete)
        val shift = specialKey(KeyAction.ShiftKey)
        val switchIme = specialKey(KeyAction.SwitchToNextIme)

        assertNotNull("$prefix should have Space", space)
        assertNotNull("$prefix should have Enter", enter)
        assertNotNull("$prefix should have Delete", delete)
        assertNotNull("$prefix should have ShiftKey", shift)
        assertNotNull("$prefix should have SwitchToNextIme", switchIme)

        assertEquals("$prefix Space colSpan", 5, space!!.colSpan)
        assertEquals("$prefix Enter colSpan", 2, enter!!.colSpan)
        assertEquals("$prefix Space row", 3, space.row)
        assertEquals("$prefix Enter row", 3, enter.row)
        assertEquals("$prefix Delete row", 3, delete!!.row)
        assertEquals("$prefix Shift row", 3, shift!!.row)
        assertEquals("$prefix SwitchToNextIme row", 3, switchIme!!.row)
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

        val a = keyItem(layout, "qwerty_key_a")
        assertEquals(2, a.placement.rowUnits)
        assertEquals(1, a.placement.columnUnits)

        val z = keyItem(layout, "qwerty_key_z")
        assertEquals(4, z.placement.rowUnits)
        assertEquals(2, z.placement.columnUnits)

        val space = keyItem(layout, "qwerty_space")
        assertEquals(6, space.placement.rowUnits)
        assertEquals(4, space.placement.columnUnits)
        assertEquals(10, space.placement.columnSpanUnits)
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
        assertCommonStructure(layout)
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
}
