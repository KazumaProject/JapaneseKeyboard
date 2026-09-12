package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import com.kazumaproject.markdownhelperkeyboard.ime_service.withComposingGuideShortcut
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import org.junit.Assert.*
import org.junit.Test

class ComposingGuideShortcutTest {
    @Test fun addsExactlyOneRuntimeIconWithoutChangingExistingOrder() {
        val configured = listOf(ShortcutType.PASTE, ShortcutType.SETTINGS)
        val visible = withComposingGuideShortcut(configured, true)
        assertEquals(configured + ShortcutType.COMPOSING_GUIDE_TOGGLE, visible)
        assertEquals(visible, withComposingGuideShortcut(visible, true))
        assertEquals(configured, withComposingGuideShortcut(visible, false))
        assertEquals(listOf(ShortcutType.PASTE, ShortcutType.SETTINGS), configured)
        assertTrue(ShortcutType.COMPOSING_GUIDE_TOGGLE.runtimeOnly)
    }
}
