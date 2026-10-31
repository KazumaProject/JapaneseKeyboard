package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import com.kazumaproject.custom_keyboard.data.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SpecialActionPersistenceTest {
    @Test fun specialActionsRoundTripThroughTapAndFlickStorage() {
        val actions = listOf(KeyAction.Tab, KeyAction.Escape, KeyAction.Home, KeyAction.End,
            KeyAction.PageUp, KeyAction.PageDown, KeyAction.NextField, KeyAction.PreviousField,
            KeyAction.SelectLeft, KeyAction.SelectRight, KeyAction.UndoLastDelete,
            KeyAction.CtrlLock, KeyAction.AltLock)
        actions.forEach { action ->
            assertEquals(action, KeyActionMapper.toKeyAction(KeyActionMapper.fromKeyAction(action)))
            val (type, value) = FlickAction.Action(action).toDbStrings()
            val restored = FlickMapping(1, 0, FlickDirection.DOWN, type, value).toFlickAction()
            assertEquals(action, (restored as FlickAction.Action).action)
        }
    }
}
