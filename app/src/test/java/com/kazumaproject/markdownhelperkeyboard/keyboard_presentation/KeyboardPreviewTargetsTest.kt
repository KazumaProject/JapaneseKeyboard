package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.*
import org.junit.Test

class KeyboardPreviewTargetsTest {
    private fun custom(id: Long, stable: String, name: String = "same name") =
        CustomKeyboardLayout(id, name, 5, 4, stableId = stable)

    @Test fun preservesSelectedFamilyAndCustomTabOrder() {
        val layouts = listOf(custom(8, "second"), custom(2, "first"))
        val pages = keyboardPreviewTargets(listOf(KeyboardType.QWERTY, KeyboardType.CUSTOM, KeyboardType.SPLIT), layouts)
        assertEquals(listOf("QWERTY", "CUSTOM:second", "CUSTOM:first", "SPLIT"), pages.map { it.id })
    }
    @Test fun doesNotIncludeUnselectedCustomLayouts() {
        assertEquals(listOf("TENKEY"), keyboardPreviewTargets(listOf(KeyboardType.TENKEY), listOf(custom(1, "a"))).map { it.id })
    }
    @Test fun pageIdentitySurvivesRenameAndReorder() {
        val layout = custom(1, "fixed")
        val before = keyboardPreviewTargets(listOf(KeyboardType.CUSTOM), listOf(layout)).single()
        val after = keyboardPreviewTargets(listOf(KeyboardType.QWERTY, KeyboardType.CUSTOM), listOf(layout.copy(name = "renamed"))).last()
        assertEquals(before.id, after.id)
        assertTrue(after.matches(layout.copy(name = "renamed")))
    }
    @Test fun sameNamesDoNotCollapseDistinctLayouts() {
        assertEquals(2, keyboardPreviewTargets(listOf(KeyboardType.CUSTOM), listOf(custom(1, "a"), custom(2, "b"))).size)
    }
    @Test fun missingCustomLayoutRetainsAnUnavailablePageInsteadOfSubstitutingAnotherKeyboard() {
        assertEquals(listOf("CUSTOM:null"), keyboardPreviewTargets(listOf(KeyboardType.CUSTOM), emptyList()).map { it.id })
    }
    @Test fun emptyOrderUsesTheImeTenkeyFallback() {
        assertEquals(listOf(KeyboardPreviewTarget(KeyboardType.TENKEY)), keyboardPreviewTargets(emptyList(), emptyList()))
    }
    @Test fun legacyMissingStableIdUsesDatabaseIdentityWithoutWritingAnId() {
        val layout = custom(15, "")
        val target = keyboardPreviewTargets(listOf(KeyboardType.CUSTOM), listOf(layout)).single()
        assertEquals("CUSTOM:15", target.id)
        assertTrue(target.matches(layout))
        assertEquals("", layout.stableId)
    }
    @Test fun homeResolvesOneSavedFamilyRatherThanAllEnabledKeyboards() {
        val order = listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE, KeyboardType.QWERTY, KeyboardType.CUSTOM)
        assertEquals(KeyboardPreviewTarget(KeyboardType.QWERTY), resolveHomeKeyboardPreviewTarget(
            order, listOf(custom(1, "a"), custom(2, "b")), true, 2, false, "", null))
        assertEquals(KeyboardPreviewTarget(KeyboardType.TENKEY), resolveHomeKeyboardPreviewTarget(
            order, emptyList(), false, 2, false, "", null))
    }
    @Test fun homeUsesTheLiveSelectionOnlyWhileItRemainsAvailable() {
        val order = listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE)
        val live = KeyboardPreviewTarget(KeyboardType.SUMIRE)
        assertEquals(live, resolveHomeKeyboardPreviewTarget(order, emptyList(), false, 0, false, "", live))
        assertEquals(KeyboardPreviewTarget(KeyboardType.TENKEY), resolveHomeKeyboardPreviewTarget(
            listOf(KeyboardType.TENKEY), emptyList(), false, 0, false, "", live))
    }
    @Test fun homeResolvesTheSelectedCustomStableIdAndFallsBackAfterDeletion() {
        val layouts = listOf(custom(1, "first"), custom(2, "second"))
        val selected = KeyboardPreviewTarget(KeyboardType.CUSTOM, "second", 2)
        assertEquals(selected, resolveHomeKeyboardPreviewTarget(listOf(KeyboardType.CUSTOM), layouts, false, 0, true, "second", null))
        assertEquals(KeyboardPreviewTarget(KeyboardType.CUSTOM, "first", 1), resolveHomeKeyboardPreviewTarget(
            listOf(KeyboardType.CUSTOM), layouts.take(1), false, 0, true, "second", selected))
    }

    @Test fun lastRealSelectionWinsOverStartupOrderEvenWhenRememberIsOff() {
        val selected = KeyboardPreviewTarget(KeyboardType.SUMIRE, inputMode = com.kazumaproject.core.domain.state.InputMode.ModeEnglish)
        assertEquals(selected, resolveHomeKeyboardPreviewTarget(listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE), emptyList(),
            false, 0, false, null, live = null, lastUsed = selected))
        assertEquals(selected, resolveHomeKeyboardPreviewTarget(listOf(KeyboardType.SUMIRE, KeyboardType.TENKEY), emptyList(),
            false, 1, false, null, live = null, lastUsed = selected))
    }
    @Test fun deletedOrDisabledLastSelectionIsNotPresentedAsTheCurrentKeyboard() {
        val selected = KeyboardPreviewTarget(KeyboardType.CUSTOM, "deleted", 7)
        assertFalse(selected.isAvailable(listOf(KeyboardType.CUSTOM), listOf(custom(1, "remaining"))))
        assertFalse(KeyboardPreviewTarget(KeyboardType.SUMIRE).isAvailable(listOf(KeyboardType.TENKEY), emptyList()))
        val startup = resolveHomeKeyboardPreviewTarget(listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE), emptyList(),
            false, 0, false, null, live = null, lastUsed = selected)
        assertEquals(KeyboardType.TENKEY, startup.type)
        assertNotEquals(selected, startup)
    }

}
