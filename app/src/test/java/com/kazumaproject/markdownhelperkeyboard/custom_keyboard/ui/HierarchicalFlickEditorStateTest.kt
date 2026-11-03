package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.KeyMode
import com.kazumaproject.custom_keyboard.data.ModeSwitchBoundary
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*
import org.junit.Assert.*
import org.junit.Test

class HierarchicalFlickEditorStateTest {
    @Test fun createThreeStagesPreservesReleaseOutputAndLimitsDepth() {
        val state = HierarchicalFlickEditorState(TfbiFlickNode.StatefulKey(
            normalMap = mapOf(TAP to TfbiFlickNode.Input("base"), RIGHT to TfbiFlickNode.Input("first")), label = "key"))
        state.makeBranch(RIGHT)
        state.open(RIGHT)
        assertEquals(TfbiFlickNode.Input("first"), state.currentMap()[RIGHT])
        assertTrue(runCatching { state.makeBranch(RIGHT) }.isFailure)
        state.makeBranch(DOWN)
        state.open(DOWN)
        state.setOutput(LEFT, "third😀")
        assertTrue(runCatching { state.makeBranch(UP) }.isFailure)
        assertEquals(TfbiFlickNode.Input("third😀"), state.currentMap()[LEFT])
        state.back(); state.back()
        state.delete(RIGHT)
        assertEquals(setOf(TAP), state.currentMap().keys)
        assertTrue(state.hasNormalOutput())
    }

    @Test fun editLeafRetainsStateTransitionsAndUneditedBranches() {
        val input = TfbiFlickNode.Input("が", KeyMode.DAKUTEN, ModeSwitchBoundary.I_COLUMN_DIACRITIC)
        val branch = TfbiFlickNode.SubMenu(mapOf(UP to input), "branch", true, setOf(LEFT, DOWN))
        val definition = TfbiFlickNode.StatefulKey(
            normalMap = mapOf(LEFT to branch), dakutenMap = mapOf(TAP to input), label = "か")
        val state = HierarchicalFlickEditorState(definition)
        state.open(LEFT)
        state.setOutput(UP, "changed")
        val edited = state.definition.normalMap[LEFT] as TfbiFlickNode.SubMenu
        assertEquals(branch.copy(nextMap = mapOf(UP to input.copy(char = "changed"))), edited)
        assertEquals(definition.dakutenMap, state.definition.dakutenMap)
        state.selectMode(KeyMode.DAKUTEN)
        assertTrue(state.path.isEmpty())
        state.setOutput(TAP, "voiced")
        assertEquals(input.copy(char = "voiced"), state.definition.dakutenMap!![TAP])
        assertEquals(edited, state.definition.normalMap[LEFT])
    }
}
