package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.DisplayActionUi
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.SpecialFlickMappingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpecialFlickActionTargetStateTest {

    @Test
    fun displayActionsContainCursorUpDownAndMoveToCustomKeyboard() {
        val actions = KeyActionMapper.getDisplayActions(ApplicationProvider.getApplicationContext())
            .map { it.action }

        assertTrue(actions.contains(KeyAction.MoveCursorUp))
        assertTrue(actions.contains(KeyAction.MoveCursorDown))
        assertTrue(actions.any { it is KeyAction.MoveToCustomKeyboard })
    }

    @Test
    fun moveToCustomKeyboardDisplayRestoreUsesActionTypeNotPlaceholderEquality() {
        val displayActions = listOf(
            DisplayActionUi("Delete", KeyAction.Delete, null),
            DisplayActionUi("Move", KeyAction.MoveToCustomKeyboard(""), null)
        )

        val restored = displayActions.displayActionFor(KeyAction.MoveToCustomKeyboard("target-a"))

        assertEquals("Move", restored?.displayName)
    }

    @Test
    fun selectingMoveToCustomKeyboardUsesCurrentDirectionTargetOrValidDefault() {
        val validTargets = linkedSetOf("target-a", "target-b")

        assertEquals(
            KeyAction.MoveToCustomKeyboard("target-b"),
            resolveSpecialFlickSelectedAction(
                selectedAction = KeyAction.MoveToCustomKeyboard(""),
                currentAction = KeyAction.MoveToCustomKeyboard("target-b"),
                selectedTargetStableId = "target-a",
                validTargetStableIds = validTargets
            )
        )
        assertEquals(
            KeyAction.MoveToCustomKeyboard("target-a"),
            resolveSpecialFlickSelectedAction(
                selectedAction = KeyAction.MoveToCustomKeyboard(""),
                currentAction = null,
                selectedTargetStableId = null,
                validTargetStableIds = validTargets
            )
        )
    }

    @Test
    fun selectingMoveToCustomKeyboardWithoutValidTargetKeepsSaveValidationFalse() {
        val action = resolveSpecialFlickSelectedAction(
            selectedAction = KeyAction.MoveToCustomKeyboard(""),
            currentAction = null,
            selectedTargetStableId = null,
            validTargetStableIds = emptySet()
        )
        val items = listOf(SpecialFlickMappingItem(direction = FlickDirection.TAP, action = action))

        assertEquals(KeyAction.MoveToCustomKeyboard(""), action)
        assertFalse(items.hasOnlyValidMoveToCustomKeyboardTargets(emptySet()))
    }

    @Test
    fun targetSelectionUpdatesOnlyCurrentSpecialFlickDirection() {
        val items = listOf(
            SpecialFlickMappingItem(direction = FlickDirection.TAP, action = KeyAction.MoveToCustomKeyboard("target-a")),
            SpecialFlickMappingItem(direction = FlickDirection.UP, action = KeyAction.MoveToCustomKeyboard("target-b")),
            SpecialFlickMappingItem(direction = FlickDirection.DOWN, action = KeyAction.MoveToCustomKeyboard("target-c"))
        )

        val updated = items.withMoveToCustomKeyboardTargetForDirection(
            direction = FlickDirection.UP,
            stableId = "target-d",
            validTargetStableIds = setOf("target-a", "target-c", "target-d")
        )

        assertEquals("target-a", updated.moveToCustomKeyboardStableIdForDirection(FlickDirection.TAP))
        assertEquals("target-d", updated.moveToCustomKeyboardStableIdForDirection(FlickDirection.UP))
        assertEquals("target-c", updated.moveToCustomKeyboardStableIdForDirection(FlickDirection.DOWN))
    }

    @Test
    fun specialFlickValidationRejectsBlankOrDeletedTargets() {
        val validTargets = setOf("target-a", "target-b")

        assertTrue(
            listOf(
                SpecialFlickMappingItem(FlickDirection.TAP.name, FlickDirection.TAP, KeyAction.MoveToCustomKeyboard("target-a")),
                SpecialFlickMappingItem(FlickDirection.UP.name, FlickDirection.UP, KeyAction.Delete),
                SpecialFlickMappingItem(FlickDirection.DOWN.name, FlickDirection.DOWN, KeyAction.MoveToCustomKeyboard("target-b"))
            ).hasOnlyValidMoveToCustomKeyboardTargets(validTargets)
        )
        assertFalse(
            listOf(
                SpecialFlickMappingItem(direction = FlickDirection.TAP, action = KeyAction.MoveToCustomKeyboard(""))
            ).hasOnlyValidMoveToCustomKeyboardTargets(validTargets)
        )
        assertFalse(
            listOf(
                SpecialFlickMappingItem(direction = FlickDirection.TAP, action = KeyAction.MoveToCustomKeyboard("deleted-target"))
            ).hasOnlyValidMoveToCustomKeyboardTargets(validTargets)
        )
    }
}
