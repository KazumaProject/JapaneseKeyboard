package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyWithFlicks
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class KeyboardRepositoryGridPlacementTest {

    private val repository = KeyboardRepository(mock(KeyboardLayoutDao::class.java))

    @Test
    fun convertRoundTrip_preservesNormalTextActionAndGridPlacement() {
        val q = KeyData(
            label = "q",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Text("q"),
            keyType = KeyType.NORMAL,
            isSpecialKey = false,
            keyId = "q_key"
        )
        val a = KeyData(
            label = "a",
            row = 1,
            column = 0,
            isFlickable = false,
            action = KeyAction.Text("a"),
            keyType = KeyType.NORMAL,
            isSpecialKey = false,
            keyId = "a_key"
        )
        val layout = KeyboardLayout(
            keys = listOf(q, a),
            flickKeyMaps = emptyMap(),
            columnCount = 10,
            rowCount = 4,
            items = listOf(
                KeyItem("q_key", q, GridPlacement(rowUnits = 0, columnUnits = 0)),
                KeyItem("a_key", a, GridPlacement(rowUnits = 2, columnUnits = 1))
            ),
            columnUnitCount = 20,
            rowUnitCount = 8
        )

        val dbKeys = convertToDbKeys(layout)
        val restored = convertToUiLayout(dbKeys)

        val restoredA = restored.keys.first { it.keyId == "a_key" }
        val restoredAItem = restored.items.filterIsInstance<KeyItem>().first { it.keyData.keyId == "a_key" }

        assertEquals(KeyAction.Text("q"), restored.keys.first { it.keyId == "q_key" }.action)
        assertEquals(KeyAction.Text("a"), restoredA.action)
        assertFalse(restoredA.isSpecialKey)
        assertEquals(1, restoredAItem.placement.columnUnits)
    }

    @Test
    fun convertRoundTrip_preservesFlexibleSpanUnits() {
        val half = KeyData(
            label = "h",
            row = 0,
            column = 0,
            isFlickable = false,
            action = KeyAction.Text("h"),
            keyType = KeyType.NORMAL,
            isSpecialKey = false,
            keyId = "half_key",
            rowSpan = 1,
            colSpan = 2
        )
        val layout = KeyboardLayout(
            keys = listOf(half),
            flickKeyMaps = emptyMap(),
            columnCount = 2,
            rowCount = 1,
            items = listOf(
                KeyItem(
                    id = "half_key",
                    keyData = half,
                    placement = GridPlacement(
                        rowUnits = 0,
                        columnUnits = 0,
                        rowSpanUnits = 1,
                        columnSpanUnits = 3
                    )
                )
            ),
            columnUnitCount = 4,
            rowUnitCount = 2
        )

        val dbKeys = convertToDbKeys(layout)
        val restored = convertToUiLayout(dbKeys)
        val restoredItem = restored.items.filterIsInstance<KeyItem>().single()

        assertEquals(1, dbKeys.single().rowSpanUnits)
        assertEquals(3, dbKeys.single().columnSpanUnits)
        assertEquals(1, restoredItem.placement.rowSpanUnits)
        assertEquals(3, restoredItem.placement.columnSpanUnits)
        assertEquals(1, restored.keys.single().rowSpan)
        assertEquals(2, restored.keys.single().colSpan)
    }

    @Test
    fun convertToUiModel_fallsBackTextActionOnlyForNormalNonSpecialKeys() {
        val normal = keyDefinition(
            identifier = "normal_key",
            label = "x",
            keyType = KeyType.NORMAL,
            isSpecialKey = false,
            action = null
        )
        val flick = keyDefinition(
            identifier = "flick_key",
            label = "y",
            keyType = KeyType.PETAL_FLICK,
            isSpecialKey = false,
            action = null
        )
        val special = keyDefinition(
            identifier = "special_key",
            label = "Del",
            keyType = KeyType.NORMAL,
            isSpecialKey = true,
            action = null
        )

        val restored = convertToUiLayout(listOf(normal, flick, special))

        assertEquals(KeyAction.Text("x"), restored.keys.first { it.keyId == "normal_key" }.action)
        assertNull(restored.keys.first { it.keyId == "flick_key" }.action)
        assertNull(restored.keys.first { it.keyId == "special_key" }.action)
    }

    private fun convertToDbKeys(layout: KeyboardLayout): List<KeyDefinition> {
        val method = KeyboardRepository::class.java.getDeclaredMethod(
            "convertToDbModel",
            KeyboardLayout::class.java
        )
        method.isAccessible = true
        val parts = method.invoke(repository, layout)
        val keysField = parts.javaClass.getDeclaredField("keys")
        keysField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return keysField.get(parts) as List<KeyDefinition>
    }

    private fun convertToUiLayout(keys: List<KeyDefinition>): KeyboardLayout {
        val method = KeyboardRepository::class.java.getDeclaredMethod(
            "convertToUiModel",
            FullKeyboardLayout::class.java
        )
        method.isAccessible = true
        return method.invoke(
            repository,
            FullKeyboardLayout(
                layout = CustomKeyboardLayout(
                    layoutId = 1,
                    name = "test",
                    columnCount = 10,
                    rowCount = 4
                ),
                keysWithFlicks = keys.map {
                    KeyWithFlicks(
                        key = it,
                        flicks = emptyList(),
                        circularFlicks = emptyList(),
                        twoStepFlicks = emptyList(),
                        longPressFlicks = emptyList(),
                        twoStepLongPressFlicks = emptyList()
                    )
                }
            )
        ) as KeyboardLayout
    }

    private fun keyDefinition(
        identifier: String,
        label: String,
        keyType: KeyType,
        isSpecialKey: Boolean,
        action: String?
    ): KeyDefinition {
        return KeyDefinition(
            keyId = 0,
            ownerLayoutId = 1,
            label = label,
            row = 0,
            column = 0,
            keyType = keyType,
            isSpecialKey = isSpecialKey,
            keyIdentifier = identifier,
            action = action
        )
    }
}
