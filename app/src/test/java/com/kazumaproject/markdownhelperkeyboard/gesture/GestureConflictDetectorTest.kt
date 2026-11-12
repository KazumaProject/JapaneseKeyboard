package com.kazumaproject.markdownhelperkeyboard.gesture

import com.kazumaproject.gesture_keyboard.*
import org.junit.Assert.*
import org.junit.Test

class GestureConflictDetectorTest {
    private val path = GesturePresets.path("0,0 10,10 20,0")
    private val entry = GestureEntry("new", GestureOutput.Reading("あ"), listOf(path))
    @Test fun anExactSameOutputVariantCannotHideAnotherSimilarOutput() {
        val same = entry.copy(id = "same-output")
        val conflict = GestureEntry("different", GestureOutput.Reading("い"), listOf(GesturePresets.path("0,0 10,11 20,0")))
        val set = GestureSet("custom", "Custom", listOf(same, conflict))
        assertEquals(listOf(conflict.output), gestureConflicts(set, entry, .14f).map { it.entry.output })
    }
    @Test fun sameOutputVariantsAndTheEntryBeingReplacedAreNotConflicts() {
        val set = GestureSet("custom", "Custom", listOf(entry, entry.copy(id = "variant")))
        assertTrue(gestureConflicts(set, entry, .14f).isEmpty())
        assertTrue(gestureConflicts(set.copy(entries = listOf(entry)), entry.copy(output = GestureOutput.Reading("い")), .14f).isEmpty())
    }
    @Test fun differentShapesDoNotWarn() {
        val other = GestureEntry("other", GestureOutput.Reading("い"), listOf(GesturePresets.path("0,0 0,20")))
        assertTrue(gestureConflicts(GestureSet("custom", "Custom", listOf(other)), entry, .14f).isEmpty())
    }
}
