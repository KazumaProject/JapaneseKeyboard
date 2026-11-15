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
}
