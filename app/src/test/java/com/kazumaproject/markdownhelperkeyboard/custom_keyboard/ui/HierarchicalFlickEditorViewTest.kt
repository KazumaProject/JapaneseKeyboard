package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HierarchicalFlickEditorViewTest {
    private fun editor() = HierarchicalFlickEditorView(ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar))

    private fun HierarchicalFlickEditorView.select(direction: TfbiFlickDirection) {
        val directions = listOf(UP_LEFT, UP, UP_RIGHT, LEFT, TAP, RIGHT, DOWN_LEFT, DOWN, DOWN_RIGHT)
        val index = directions.indexOf(direction)
        val density = resources.displayMetrics.density
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP,
            (4 + index % 3 * 74 + 28) * density,
            (4 + index / 3 * 74 + 28) * density, 0)
        findViewById<FlickGridEditorView>(R.id.hierarchical_grid).onTouchEvent(event)
        event.recycle()
    }

    @Test fun editThreeStagesUsingTheSharedGridAndInlineInput() {
        val view = editor()
        view.setDefinition(TfbiFlickNode.StatefulKey(mapOf(TAP to TfbiFlickNode.Input("base")), label = "key"))
        val input = view.findViewById<EditText>(R.id.hierarchical_input)
        val branch = view.findViewById<View>(R.id.hierarchical_branch)
        assertEquals("base", input.text.toString())
        var changes = 0
        view.onDefinitionChanged = { changes++ }
        view.select(RIGHT)
        assertEquals("", input.text.toString())
        branch.performClick()
        view.select(DOWN)
        branch.performClick()
        view.select(LEFT)
        input.setText("third😀")
        val stage2 = view.definition.normalMap[RIGHT] as TfbiFlickNode.SubMenu
        val stage3 = stage2.nextMap[DOWN] as TfbiFlickNode.SubMenu
        assertEquals(TfbiFlickNode.Input("third😀"), stage3.nextMap[LEFT])
        assertEquals(3, changes)
        assertTrue(view.hasOutput())
        view.select(UP)
        assertEquals(View.GONE, branch.visibility)
        view.select(LEFT)
        assertEquals("third😀", input.text.toString())
        input.setText("")
        assertTrue(view.hasOutput()) // The root TAP output remains intact.
        assertFalse(((view.definition.normalMap[RIGHT] as TfbiFlickNode.SubMenu)
            .nextMap[DOWN] as TfbiFlickNode.SubMenu).nextMap.containsKey(LEFT))
    }

    @Test fun branchLabelNavigationAndDeletionPreserveSiblingOutputs() {
        val view = editor()
        val nested = TfbiFlickNode.SubMenu(mapOf(UP to TfbiFlickNode.Input("nested")), label = "branch")
        view.setDefinition(TfbiFlickNode.StatefulKey(mapOf(
            TAP to TfbiFlickNode.Input("base"), RIGHT to nested), label = "key"))
        val input = view.findViewById<EditText>(R.id.hierarchical_input)
        view.select(RIGHT)
        assertEquals("branch", input.text.toString())
        input.setText("renamed")
        view.findViewById<View>(R.id.hierarchical_branch).performClick()
        view.select(UP)
        assertEquals("nested", input.text.toString())
        input.setText("updated")
        view.findViewById<View>(R.id.hierarchical_back).performClick()
        assertEquals("renamed", input.text.toString())
        val branch = view.definition.normalMap[RIGHT] as TfbiFlickNode.SubMenu
        assertEquals(TfbiFlickNode.Input("updated"), branch.nextMap[UP])
        view.findViewById<View>(R.id.hierarchical_clear).performClick()
        assertFalse(view.definition.normalMap.containsKey(RIGHT))
        assertEquals(TfbiFlickNode.Input("base"), view.definition.normalMap[TAP])
        assertEquals(View.GONE, view.findViewById<View>(R.id.hierarchical_back).visibility)
    }
}
