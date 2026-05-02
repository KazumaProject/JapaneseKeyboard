package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomKeyboardIndexResolverTest {

    @Test
    fun resolveCustomKeyboardIndexByStableIdReturnsTargetIndex() {
        val layouts = listOf(
            layout("A", "stable-a"),
            layout("B", "stable-b")
        )

        assertEquals(1, resolveCustomKeyboardIndexByStableId(layouts, "stable-b"))
    }

    @Test
    fun resolveCustomKeyboardIndexByStableIdStillFindsTargetAfterReorder() {
        val reordered = listOf(
            layout("B", "stable-b"),
            layout("A", "stable-a")
        )

        assertEquals(1, resolveCustomKeyboardIndexByStableId(reordered, "stable-a"))
    }

    @Test
    fun resolveCustomKeyboardIndexByStableIdReturnsNullWhenMissingOrBlank() {
        val layouts = listOf(layout("A", "stable-a"))

        assertNull(resolveCustomKeyboardIndexByStableId(layouts, "missing"))
        assertNull(resolveCustomKeyboardIndexByStableId(layouts, ""))
    }

    @Test
    fun resolveInitialCustomKeyboardIndexUsesSavedStableIdWhenRememberLastIsOn() {
        val layouts = listOf(
            layout("A", "stable-a"),
            layout("B", "stable-b")
        )

        assertEquals(1, resolveInitialCustomKeyboardIndex(layouts, true, "stable-b"))
    }

    @Test
    fun resolveInitialCustomKeyboardIndexFallsBackToFirstWhenSavedStableIdIsMissing() {
        val layouts = listOf(
            layout("A", "stable-a"),
            layout("B", "stable-b")
        )

        assertEquals(0, resolveInitialCustomKeyboardIndex(layouts, true, "missing"))
    }

    @Test
    fun resolveInitialCustomKeyboardIndexReturnsFirstWhenRememberLastIsOff() {
        val layouts = listOf(
            layout("A", "stable-a"),
            layout("B", "stable-b")
        )

        assertEquals(0, resolveInitialCustomKeyboardIndex(layouts, false, "stable-b"))
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
