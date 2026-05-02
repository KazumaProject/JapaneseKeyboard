package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KeyboardRepositoryStableIdTest {

    @Test
    fun ensureStableIdsForLayoutsFillsBlankStableId() {
        val layouts = listOf(layout("A", ""))

        val ensured = ensureStableIdsForLayouts(layouts) { "generated-stable-id" }

        assertEquals("generated-stable-id", ensured.single().stableId)
    }

    @Test
    fun ensureStableIdsForLayoutsKeepsExistingStableId() {
        val layouts = listOf(layout("A", "stable-a"))

        val ensured = ensureStableIdsForLayouts(layouts) { "generated-stable-id" }

        assertEquals("stable-a", ensured.single().stableId)
        assertNotEquals("generated-stable-id", ensured.single().stableId)
    }

    private fun layout(name: String, stableId: String): CustomKeyboardLayout {
        return CustomKeyboardLayout(
            name = name,
            columnCount = 1,
            rowCount = 1,
            stableId = stableId
        )
    }
}
