package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorGridGeometryTest {

    @Test
    fun flexibleLayout_hidesRowColumnDeleteChrome() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val bounds = layout.canonicalLayoutForEditor().editorGridBounds()

        assertFalse(bounds.showRowColumnDeleteChrome)
        assertTrue(bounds.rowDeleteSpecs(layout.rowCount).isEmpty())
        assertTrue(bounds.columnDeleteSpecs(layout.columnCount).isEmpty())
    }

    @Test
    fun legacyLayout_deleteSpecsFitInsideGridBounds() {
        val layout = KeyboardLayout(
            keys = emptyList(),
            flickKeyMaps = emptyMap(),
            columnCount = 5,
            rowCount = 4
        )
        val bounds = layout.editorGridBounds()

        bounds.rowDeleteSpecs(layout.rowCount).forEach { spec ->
            assertTrue(spec.fitsWithin(bounds.gridRowCount))
        }
        bounds.columnDeleteSpecs(layout.columnCount).forEach { spec ->
            assertTrue(spec.fitsWithin(bounds.gridColumnCount))
        }
    }

    @Test
    fun flexibleLayout_canonicalEditorBoundsContainItemEdges() {
        val layout = KeyboardLayout(
            keys = emptyList(),
            flickKeyMaps = emptyMap(),
            columnCount = 1,
            rowCount = 1,
            items = listOf(SpacerItem("far", GridPlacement(7, 9, 2, 3))),
            columnUnitCount = 2,
            rowUnitCount = 2
        )
        val canonical = layout.canonicalLayoutForEditor()
        val bounds = canonical.editorGridBounds()

        assertEquals(9, canonical.rowUnitCount)
        assertEquals(12, canonical.columnUnitCount)
        assertEquals(canonical.rowUnitCount + 2, bounds.gridRowCount)
        assertEquals(canonical.columnUnitCount + 2, bounds.gridColumnCount)
    }

    @Test
    fun newBottomRowCursor_expandsEditorBoundsWithoutClampingCursorInsideOldBounds() {
        val layout = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val cursor = PlacementCursor(
            target = InsertionTarget.NewBottomRow(columnUnits = 22),
            span = GridSpan(rowSpanUnits = 2, columnSpanUnits = 2),
            policy = InsertionPolicy.Auto2D,
            source = CursorSource.PointerMove
        )

        val canonical = layout.canonicalLayoutForEditor(cursor)
        val cursorPlacement = cursor.displayPlacement(canonical)!!

        assertTrue(canonical.rowUnitCount > layout.rowUnitCount)
        assertTrue(canonical.columnUnitCount > layout.columnUnitCount)
        assertEquals(canonical.rowUnitCount - 2, cursorPlacement.rowUnits)
        assertTrue(cursorPlacement.rowUnits + cursorPlacement.rowSpanUnits <= canonical.rowUnitCount)
        assertTrue(cursorPlacement.columnUnits + cursorPlacement.columnSpanUnits <= canonical.columnUnitCount)
    }
}
