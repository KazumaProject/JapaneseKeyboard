package com.kazumaproject.markdownhelperkeyboard.sumire_special_key

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.ResolvedSumireSpecialKeyAction
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class SumireSpecialKeyPopupDisplayMapBuilderTest {
    @Test
    fun crossFlickDirectionOverridesAreAddedToDisplayMapWithoutChangingBaseMap() {
        val base = mapOf(
            FlickDirection.TAP to FlickAction.Action(KeyAction.Space),
            FlickDirection.UP to FlickAction.Action(KeyAction.Convert)
        )

        val built = SumireSpecialKeyPopupDisplayMapBuilder.build(
            baseMap = base,
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            keyData = specialKey("delete_key"),
            overrides = listOf(
                action("delete_key", "UP", "Delete"),
                action("delete_key", "RIGHT", "Enter"),
                action("delete_key", "DOWN", "Space"),
                action("delete_key", "LEFT", "小゛゜")
            ),
            displayMetadata = metadata()
        )

        assertEquals(FlickAction.Action(KeyAction.Convert), base[FlickDirection.UP])
        assertNotSame(base, built)
        assertEquals(
            FlickAction.Action(KeyAction.Delete, "Delete", 101),
            built[FlickDirection.UP]
        )
        assertEquals(
            FlickAction.Action(KeyAction.Enter, "Enter", 102),
            built[FlickDirection.UP_RIGHT_FAR]
        )
        assertEquals(
            FlickAction.Action(KeyAction.Space, "Space", 103),
            built[FlickDirection.DOWN]
        )
        assertEquals(
            FlickAction.Action(KeyAction.ToggleDakuten, "Dakuten", null),
            built[FlickDirection.UP_LEFT_FAR]
        )
    }

    @Test
    fun displayMapAndExecutionResolverUseSameKeyIdDirectionAction() {
        val overrides = listOf(action("delete_key", "LEFT", "小゛゜"))
        val keyData = specialKey("delete_key")
        val built = SumireSpecialKeyPopupDisplayMapBuilder.build(
            baseMap = mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Delete)),
            layoutType = "toggle",
            inputMode = "HIRAGANA",
            keyData = keyData,
            overrides = overrides,
            displayMetadata = metadata()
        )
        val resolved = SumireSpecialKeyActionResolver(overrides)
            .resolve("toggle", "HIRAGANA", keyData, SumireSpecialKeyDirection.LEFT)

        assertEquals(
            FlickAction.Action(KeyAction.ToggleDakuten, "Dakuten", null),
            built[FlickDirection.UP_LEFT_FAR]
        )
        assertEquals(
            ResolvedSumireSpecialKeyAction.Action(KeyAction.ToggleDakuten),
            resolved
        )
    }

    private fun specialKey(keyId: String) = KeyData(
        label = "",
        row = 0,
        column = 0,
        isFlickable = false,
        action = KeyAction.Delete,
        isSpecialKey = true,
        keyId = keyId,
        keyType = KeyType.CROSS_FLICK
    )

    private fun metadata() = listOf(
        SumireSpecialKeyActionDisplayMetadata(KeyAction.Delete, "Delete", 101),
        SumireSpecialKeyActionDisplayMetadata(KeyAction.Enter, "Enter", 102),
        SumireSpecialKeyActionDisplayMetadata(KeyAction.Space, "Space", 103),
        SumireSpecialKeyActionDisplayMetadata(KeyAction.ToggleDakuten, "Dakuten", null)
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
