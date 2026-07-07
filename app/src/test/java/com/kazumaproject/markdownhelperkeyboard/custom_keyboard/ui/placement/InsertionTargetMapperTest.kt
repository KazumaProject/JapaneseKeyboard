package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class InsertionTargetMapperTest {
    private val mapper = InsertionTargetMapper()
    private val layout = KeyboardLayout(
        keys = emptyList(),
        flickKeyMaps = emptyMap(),
        columnCount = 5,
        rowCount = 4,
        items = listOf(
            key("a", GridPlacement(0, 0, 2, 2)),
            key("b", GridPlacement(0, 4, 2, 2)),
            key("c", GridPlacement(4, 0, 2, 2))
        ),
        columnUnitCount = 10,
        rowUnitCount = 8
    )

    @Test fun mapper_itemTopZone_returnsAboveRowGroup(): Unit =
        assertEquals(InsertionTarget.AboveRowGroup(0), map(1f, 0.2f))

    @Test fun mapper_itemBottomZone_returnsBelowRowGroup(): Unit =
        assertEquals(InsertionTarget.BelowRowGroup(0), map(1f, 1.8f))

    @Test fun mapper_itemMiddleLeft_returnsBeforeItem(): Unit =
        assertEquals(InsertionTarget.BeforeItem("a"), map(0.4f, 1f))

    @Test fun mapper_itemMiddleRight_returnsAfterItem(): Unit =
        assertEquals(InsertionTarget.AfterItem("a"), map(1.6f, 1f))

    @Test fun mapper_preferVerticalItemUpperHalf_returnsBeforeItemByLocalY() {
        assertEquals(
            InsertionTarget.BeforeItem("a"),
            map(1.8f, 0.5f, InsertionPolicy.PreferVertical)
        )
    }

    @Test fun mapper_preferVerticalItemLowerHalf_returnsAfterItemByLocalY() {
        assertEquals(
            InsertionTarget.AfterItem("a"),
            map(0.2f, 1.5f, InsertionPolicy.PreferVertical)
        )
    }

    @Test fun mapper_preferVerticalIgnoresLocalXForItemCenterTarget() {
        assertEquals(
            map(0.2f, 1.5f, InsertionPolicy.PreferVertical),
            map(1.8f, 1.5f, InsertionPolicy.PreferVertical)
        )
    }

    @Test fun mapper_preferVerticalItemHitDoesNotReturnRowGroupAndLoseColumn() {
        assertEquals(
            InsertionTarget.BeforeItem("a"),
            map(1f, 0.2f, InsertionPolicy.PreferVertical)
        )
        assertEquals(
            InsertionTarget.AfterItem("a"),
            map(1f, 1.8f, InsertionPolicy.PreferVertical)
        )
    }

    @Test fun mapper_betweenItems_returnsDeterministicBeforeOrAfterTarget(): Unit =
        assertEquals(InsertionTarget.BeforeItem("b"), map(3f, 1f))

    @Test fun mapper_betweenRows_returnsDeterministicAboveOrBelowRowGroup(): Unit =
        assertEquals(InsertionTarget.AboveRowGroup(4), map(1f, 3f))

    @Test fun mapper_rightOfRow_returnsRowEnd(): Unit =
        assertEquals(InsertionTarget.RowEnd(0), map(7f, 1f))

    @Test fun mapper_belowAllRows_returnsNewBottomRow(): Unit =
        assertEquals(InsertionTarget.NewBottomRow(2), map(2.4f, 7f))

    @Test fun mapper_emptyArea_returnsEmptyAreaOnlyWhenNoSemanticTargetExists() {
        val target = map(8f, -1f)
        assertEquals(InsertionTarget.EmptyArea(GridPlacement(0, 8, 1, 1)), target)
    }

    @Test fun mapper_usesHalfCellSnappingForNewBottomRowColumn(): Unit =
        assertEquals(InsertionTarget.NewBottomRow(2), map(2.9f, 7f))

    @Test fun mapper_touchingBoundaryDoesNotCreateAmbiguousTarget(): Unit =
        assertEquals(InsertionTarget.BeforeItem("b"), map(4f, 1f))

    @Test fun mapper_samePointerAlwaysReturnsSameTarget() {
        val first = map(3f, 1f)
        repeat(5) { assertEquals(first, map(3f, 1f)) }
    }

    private fun map(
        xUnits: Float,
        yUnits: Float,
        policy: InsertionPolicy = InsertionPolicy.PreferHorizontal
    ): InsertionTarget =
        mapper.mapPointer(
            layout,
            xUnits,
            yUnits,
            layout.columnUnitCount.toFloat(),
            layout.rowUnitCount.toFloat(),
            policy
        )

    private fun key(id: String, placement: GridPlacement): KeyItem {
        val key = KeyData(
            label = id,
            row = placement.rowUnits / 2,
            column = placement.columnUnits / 2,
            isFlickable = false,
            action = KeyAction.Text(id),
            keyType = KeyType.NORMAL,
            keyId = id
        )
        return KeyItem(id, key, placement)
    }
}
