package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun resolveInitialCustomKeyboardSelectionReturnsNullWhenLayoutsAreEmpty() {
        assertNull(resolveInitialCustomKeyboardSelection(emptyList(), true, "stable-a"))
    }

    @Test
    fun resolveInitialCustomKeyboardSelectionReturnsDefaultWhenRememberLastIsOff() {
        val layouts = listOf(
            layout("A", "stable-a"),
            layout("B", "stable-b")
        )

        assertEquals(
            InitialCustomKeyboardSelection(
                index = 0,
                reason = CustomKeyboardSelectionReason.InitialDefault
            ),
            resolveInitialCustomKeyboardSelection(layouts, false, "stable-b")
        )
    }

    @Test
    fun resolveInitialCustomKeyboardSelectionReturnsSavedStableIdWhenRememberLastIsOn() {
        val layouts = listOf(
            layout("A", "stable-a"),
            layout("B", "stable-b")
        )

        assertEquals(
            InitialCustomKeyboardSelection(
                index = 1,
                reason = CustomKeyboardSelectionReason.InitialRestore
            ),
            resolveInitialCustomKeyboardSelection(layouts, true, "stable-b")
        )
    }

    @Test
    fun resolveInitialCustomKeyboardSelectionReturnsDefaultWhenSavedStableIdIsNullOrBlank() {
        val layouts = listOf(
            layout("A", "stable-a"),
            layout("B", "stable-b")
        )
        val expected = InitialCustomKeyboardSelection(
            index = 0,
            reason = CustomKeyboardSelectionReason.InitialDefault
        )

        assertEquals(expected, resolveInitialCustomKeyboardSelection(layouts, true, null))
        assertEquals(expected, resolveInitialCustomKeyboardSelection(layouts, true, ""))
        assertEquals(expected, resolveInitialCustomKeyboardSelection(layouts, true, "   "))
    }

    @Test
    fun resolveInitialCustomKeyboardSelectionReturnsDefaultWhenSavedStableIdIsMissing() {
        val layouts = listOf(
            layout("A", "stable-a"),
            layout("B", "stable-b")
        )

        assertEquals(
            InitialCustomKeyboardSelection(
                index = 0,
                reason = CustomKeyboardSelectionReason.InitialDefault
            ),
            resolveInitialCustomKeyboardSelection(layouts, true, "missing")
        )
    }

    @Test
    fun resolveInitialCustomKeyboardSelectionUsesStableIdAfterReorder() {
        val reordered = listOf(
            layout("B", "stable-b"),
            layout("A", "stable-a"),
            layout("C", "stable-c")
        )

        assertEquals(
            InitialCustomKeyboardSelection(
                index = 1,
                reason = CustomKeyboardSelectionReason.InitialRestore
            ),
            resolveInitialCustomKeyboardSelection(reordered, true, "stable-a")
        )
    }

    @Test
    fun shouldPersistCustomKeyboardSelectionReturnsFalseWhenRememberLastIsOff() {
        assertFalse(
            shouldPersistCustomKeyboardSelection(
                layout("A", "stable-a"),
                rememberLast = false,
                reason = CustomKeyboardSelectionReason.UserTabClick
            )
        )
    }

    @Test
    fun shouldPersistCustomKeyboardSelectionReturnsFalseWhenStableIdIsBlank() {
        assertFalse(
            shouldPersistCustomKeyboardSelection(
                layout("A", ""),
                rememberLast = true,
                reason = CustomKeyboardSelectionReason.UserTabClick
            )
        )
    }

    @Test
    fun shouldPersistCustomKeyboardSelectionReturnsFalseForInitialSelections() {
        val layout = layout("A", "stable-a")

        assertFalse(
            shouldPersistCustomKeyboardSelection(
                layout,
                rememberLast = true,
                reason = CustomKeyboardSelectionReason.InitialRestore
            )
        )
        assertFalse(
            shouldPersistCustomKeyboardSelection(
                layout,
                rememberLast = true,
                reason = CustomKeyboardSelectionReason.InitialDefault
            )
        )
    }

    @Test
    fun shouldPersistCustomKeyboardSelectionReturnsTrueForUserSelections() {
        val layout = layout("A", "stable-a")

        assertTrue(
            shouldPersistCustomKeyboardSelection(
                layout,
                rememberLast = true,
                reason = CustomKeyboardSelectionReason.UserTabClick
            )
        )
        assertTrue(
            shouldPersistCustomKeyboardSelection(
                layout,
                rememberLast = true,
                reason = CustomKeyboardSelectionReason.UserNextTab
            )
        )
        assertTrue(
            shouldPersistCustomKeyboardSelection(
                layout,
                rememberLast = true,
                reason = CustomKeyboardSelectionReason.MoveToStableId
            )
        )
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
