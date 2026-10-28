package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatImageButton
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardModifierTest {
    @Test fun specialActionPickerIncludesTheAddedKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val available = KeyActionMapper.getDisplayActions(context).map { it.action }.toSet()
        assertTrue(available.containsAll(listOf(
            KeyAction.Tab, KeyAction.Escape, KeyAction.Home, KeyAction.End,
            KeyAction.PageUp, KeyAction.PageDown, KeyAction.SelectLeft, KeyAction.SelectRight,
            KeyAction.NextField, KeyAction.PreviousField, KeyAction.UndoLastDelete,
            KeyAction.CtrlLock, KeyAction.AltLock
        )))
    }

    @Test fun lockedStateIsShownWithoutReplacingTheUsersIcon() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val keyboard = FlickKeyboardView(context)
        val key = KeyData(label = "Ctrl", row = 0, column = 0, isFlickable = false,
            action = KeyAction.CtrlLock, isSpecialKey = true, keyId = "ctrl", keyType = KeyType.NORMAL,
            drawableResId = com.kazumaproject.core.R.drawable.custom_key_ctrl,
            icon = KeyIconRef(KeyIconType.DRAWABLE_RESOURCE_NAME, "caps_lock"))
        keyboard.setKeyboard(KeyboardLayout(listOf(key), emptyMap(), 1, 1))
        val button = keyboard.getChildAt(0) as AppCompatImageButton
        assertFalse(button.drawable is LayerDrawable)
        keyboard.setSoftwareModifierState(ctrl = true, alt = false)
        assertTrue(button.drawable is LayerDrawable)
        assertTrue(button.contentDescription.toString().contains("Ctrl"))
        keyboard.setSoftwareModifierState(ctrl = false, alt = false)
        assertFalse(button.drawable is LayerDrawable)
    }

    @Test fun configuredActionIncludesFlickAndSumireOverride() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val keyboard = FlickKeyboardView(context)
        val key = KeyData(label = "key", row = 0, column = 0, isFlickable = true,
            action = KeyAction.Delete, isSpecialKey = true, keyId = "key", keyType = KeyType.CROSS_FLICK)
        keyboard.setSumireSpecialKeyActionResolver({ _, _, _, direction ->
            if (direction == SumireSpecialKeyDirection.DOWN) ResolvedSumireSpecialKeyAction.Action(KeyAction.UndoLastDelete)
            else ResolvedSumireSpecialKeyAction.Default
        }, "flick", "HIRAGANA")
        keyboard.setKeyboard(KeyboardLayout(listOf(key), mapOf("key" to listOf(mapOf(
            FlickDirection.TAP to FlickAction.Action(KeyAction.Delete)))), 1, 1))
        assertTrue(keyboard.hasConfiguredAction(KeyAction.UndoLastDelete))
        assertFalse(keyboard.hasConfiguredAction(KeyAction.AltLock))
    }
}
