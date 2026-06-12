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
        )

        assertFalse(ShortcutType.INPUT_BEHAVIOR_TOGGLE in activeTypes)
    }

    @Test
    fun inputBehaviorToggleHasActiveIconAndIsNotInDefaultShortcuts() {
        val defaultShortcuts = ShortcutRepository(mock<ShortcutDao>()).defaultShortcuts

        assertNotNull(ShortcutType.INPUT_BEHAVIOR_TOGGLE.activeIconResId)
        assertFalse(ShortcutType.INPUT_BEHAVIOR_TOGGLE in defaultShortcuts)
    }
}
