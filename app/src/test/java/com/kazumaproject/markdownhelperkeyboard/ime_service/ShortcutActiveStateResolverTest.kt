package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.ResolvedInputBehavior
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.short_cut.database.ShortcutDao
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class ShortcutActiveStateResolverTest {

    @Test
    fun directCommitMarksInputBehaviorToggleActiveAndPreservesExistingActiveTypes() {
        val activeTypes = resolveShortcutActiveTypes(
            keyboardLayoutEditActive = true,
            keyboardFloatingActive = true,
            inputBehavior = ResolvedInputBehavior.DIRECT_COMMIT,
            liveConversionEnabled = false,
        )

        assertTrue(ShortcutType.KEYBOARD_LAYOUT_EDIT in activeTypes)
        assertTrue(ShortcutType.KEYBOARD_FLOATING_TOGGLE in activeTypes)
        assertTrue(ShortcutType.INPUT_BEHAVIOR_TOGGLE in activeTypes)
    }

    @Test
    fun composingTextDoesNotMarkInputBehaviorToggleActive() {
        val activeTypes = resolveShortcutActiveTypes(
            keyboardLayoutEditActive = false,
            keyboardFloatingActive = false,
            inputBehavior = ResolvedInputBehavior.COMPOSING_TEXT,
            liveConversionEnabled = false,
        )

        assertFalse(ShortcutType.INPUT_BEHAVIOR_TOGGLE in activeTypes)
    }

    @Test
    fun inputBehaviorToggleHasActiveIconAndIsNotInDefaultShortcuts() {
        val defaultShortcuts = ShortcutRepository(mock<ShortcutDao>()).defaultShortcuts

        assertNotNull(ShortcutType.INPUT_BEHAVIOR_TOGGLE.activeIconResId)
        assertFalse(ShortcutType.INPUT_BEHAVIOR_TOGGLE in defaultShortcuts)
    }

    @Test
    fun liveConversionDisabledDoesNotMarkLiveConversionToggleActive() {
        val activeTypes = resolveShortcutActiveTypes(
            keyboardLayoutEditActive = false,
            keyboardFloatingActive = false,
            inputBehavior = ResolvedInputBehavior.COMPOSING_TEXT,
            liveConversionEnabled = false,
        )

        assertFalse(ShortcutType.LIVE_CONVERSION_TOGGLE in activeTypes)
    }

    @Test
    fun liveConversionEnabledMarksLiveConversionToggleActive() {
        val activeTypes = resolveShortcutActiveTypes(
            keyboardLayoutEditActive = false,
            keyboardFloatingActive = false,
            inputBehavior = ResolvedInputBehavior.COMPOSING_TEXT,
            liveConversionEnabled = true,
        )

        assertTrue(ShortcutType.LIVE_CONVERSION_TOGGLE in activeTypes)
    }

    @Test
    fun liveConversionActivePreservesOtherActiveTypes() {
        val activeTypes = resolveShortcutActiveTypes(
            keyboardLayoutEditActive = true,
            keyboardFloatingActive = true,
            inputBehavior = ResolvedInputBehavior.DIRECT_COMMIT,
            liveConversionEnabled = true,
        )

        assertTrue(ShortcutType.KEYBOARD_LAYOUT_EDIT in activeTypes)
        assertTrue(ShortcutType.KEYBOARD_FLOATING_TOGGLE in activeTypes)
        assertTrue(ShortcutType.INPUT_BEHAVIOR_TOGGLE in activeTypes)
        assertTrue(ShortcutType.LIVE_CONVERSION_TOGGLE in activeTypes)
    }

    @Test
    fun liveConversionToggleHasActiveIconAndIsNotInDefaultShortcuts() {
        val defaultShortcuts = ShortcutRepository(mock<ShortcutDao>()).defaultShortcuts

        assertNotNull(ShortcutType.LIVE_CONVERSION_TOGGLE.activeIconResId)
        assertFalse(ShortcutType.LIVE_CONVERSION_TOGGLE in defaultShortcuts)
    }

    @Test
    fun liveConversionToggleCanBeResolvedFromId() {
        assertTrue(
            ShortcutType.fromId("live_conversion_toggle") == ShortcutType.LIVE_CONVERSION_TOGGLE
        )
    }
}
