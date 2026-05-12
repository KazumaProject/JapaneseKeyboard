package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SumireSpecialKeyActionDisplayOverrideApplierTest {
    @Test
    fun tapOverrideWithIconUpdatesKeyDataAndItemWithoutMovingPlacement() {
        val layout = testLayout()
        val applied = SumireSpecialKeyActionDisplayOverrideApplier.apply(
            layout = layout,
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            overrides = listOf(action("special_key", "TAP", "Delete")),
            displayMetadata = metadata()
        )

        val key = applied.special("special_key").keyData
        assertEquals(KeyAction.Delete, key.action)
        assertEquals("Delete", key.label)
        assertEquals(101, key.drawableResId)
        assertEquals(GridPlacement(0, 1, 1, 1), applied.special("special_key").placement)
        assertEquals(key, applied.keys.first { it.keyId == "special_key" })
    }

    @Test
    fun tapOverrideWithoutIconUsesDisplayNameAsTextKey() {
        val layout = testLayout()
        val applied = SumireSpecialKeyActionDisplayOverrideApplier.apply(
            layout = layout,
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            overrides = listOf(action("special_key", "TAP", "ForceNewLine")),
            displayMetadata = metadata()
        )

        val key = applied.special("special_key").keyData
        assertEquals(KeyAction.ForceNewLine, key.action)
        assertEquals("New line", key.label)
        assertEquals(null, key.drawableResId)
    }

    @Test
    fun flickOverrideDoesNotChangeKeyBodyAndUnrelatedKeysStayUntouched() {
        val layout = testLayout()
        val applied = SumireSpecialKeyActionDisplayOverrideApplier.apply(
            layout = layout,
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            overrides = listOf(action("special_key", "UP", "Delete")),
            displayMetadata = metadata()
        )

        assertEquals(layout.special("special_key").keyData, applied.special("special_key").keyData)
        assertEquals(layout.normal("normal_key").keyData, applied.normal("normal_key").keyData)
    }

    private fun testLayout(): KeyboardLayout {
        val normal = KeyItem(
            id = "normal_key",
            keyData = KeyData("A", 0, 0, false, keyId = "normal_key"),
            placement = GridPlacement(0, 0, 1, 1)
        )
        val special = KeyItem(
            id = "special_key",
            keyData = KeyData(
                label = "Space",
                row = 0,
                column = 1,
                isFlickable = false,
                action = KeyAction.Space,
                isSpecialKey = true,
                keyId = "special_key",
                keyType = KeyType.NORMAL
            ),
            placement = GridPlacement(0, 1, 1, 1)
        )
        return KeyboardLayout(
            keys = listOf(normal.keyData, special.keyData),
            flickKeyMaps = emptyMap(),
            columnCount = 2,
            rowCount = 1,
            items = listOf(normal, special),
            columnUnitCount = 2,
            rowUnitCount = 1
        )
    }

    private fun KeyboardLayout.special(keyId: String): KeyItem =
        items.filterIsInstance<KeyItem>().first { it.keyData.isSpecialKey && it.keyData.keyId == keyId }

    private fun KeyboardLayout.normal(keyId: String): KeyItem =
        items.filterIsInstance<KeyItem>().first { !it.keyData.isSpecialKey && it.keyData.keyId == keyId }

    private fun metadata() = listOf(
        SumireSpecialKeyActionDisplayMetadata(KeyAction.Delete, "Delete", 101),
        SumireSpecialKeyActionDisplayMetadata(KeyAction.ForceNewLine, "New line", null)
    )

    private fun action(keyId: String, direction: String, actionString: String) =
        SumireSpecialKeyActionOverrideEntity(
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            keyId = keyId,
            direction = direction,
            overrideType = SumireSpecialKeyOverrideType.KEY_ACTION.name,
            actionString = actionString,
            inputText = null,
            updatedAt = 1L
        )
}

