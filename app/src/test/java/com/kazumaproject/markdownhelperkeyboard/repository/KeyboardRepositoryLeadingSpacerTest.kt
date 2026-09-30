package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import com.kazumaproject.custom_keyboard.data.usesFlexiblePlacement
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyWithFlicks
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.SpacerDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KeyboardRepositoryLeadingSpacerTest {
    private val dao: KeyboardLayoutDao = mock()
    private val repository = KeyboardRepository(dao)

    @Test
    fun loadLayout_bottomLeftTwoRowKey_doesNotCreateOverlappingSpacer() {
        // #980: extend the original bottom-left key after adding a fifth row.
        val keys = (0 until 5).flatMap { row ->
            (0 until 5).mapNotNull { column ->
                if (row == 4 && column == 0) null
                else key(row * 2, column * 2, rowSpan = if (row == 3 && column == 0) 4 else 2)
            }
        }

        // New saves contain half-cell coordinates; old saves only contain cell coordinates.
        for (explicitCoordinates in listOf(true, false)) {
            val dbKeys = if (explicitCoordinates) keys else keys.map {
                it.copy(rowUnits = null, columnUnits = null, rowSpanUnits = null, columnSpanUnits = null)
            }
            val restored = loadLayout(dbKeys)

            assertEquals(24, restored.items.size)
            assertEquals(emptyList<SpacerItem>(), restored.items.filterIsInstance<SpacerItem>())
            assertEquals(GridPlacement(6, 0, 4, 2), restored.items.filterIsInstance<KeyItem>()
                .single { it.keyData.keyId == "key_6_0" }.placement)
            assertFalse(restored.usesFlexiblePlacement())
            assertValid(restored)
        }
    }

    @Test
    fun loadLayout_genuineLeadingGap_isRestored() {
        val restored = loadLayout(listOf(key(0, 2)))

        assertEquals(listOf(GridPlacement(0, 0, 2, 2)), spacerPlacements(restored))
        assertValid(restored)
    }

    @Test
    fun loadLayout_spanningKeyOccupiesPartOfLeadingGap_preservesOnlyFreeColumns() {
        val restored = loadLayout(listOf(key(0, 0, rowSpan = 4), key(2, 4)))

        assertEquals(listOf(GridPlacement(2, 2, 2, 2)), spacerPlacements(restored))
        assertValid(restored)
    }

    @Test
    fun loadLayout_spanningKeyEndsWithinLeadingGap_preservesOnlyFreeRows() {
        val restored = loadLayout(listOf(key(0, 0, rowSpan = 3), key(2, 2)))

        assertEquals(listOf(GridPlacement(3, 0, 1, 2)), spacerPlacements(restored))
        assertValid(restored)
    }

    @Test
    fun loadLayout_savedInferredSpacer_removesOverlapAndPreservesOtherSpacers() {
        val stored = listOf(
            spacer("restored_row_2_start_spacer", GridPlacement(2, 0, 2, 2)),
            spacer("user_spacer", GridPlacement(4, 0, 2, 2))
        )
        val restored = loadLayout(
            listOf(key(0, 0, rowSpan = 4), key(2, 2)),
            stored
        )

        assertEquals(listOf(SpacerItem("user_spacer", GridPlacement(4, 0, 2, 2))),
            restored.items.filterIsInstance<SpacerItem>())
        assertValid(restored)
    }

    @Test
    fun loadLayout_savedInferredSpacer_preservesGenuineLeadingGap() {
        val stored = listOf(spacer("restored_row_2_start_spacer", GridPlacement(2, 0, 2, 4)))
        val restored = loadLayout(listOf(key(0, 0, rowSpan = 4), key(2, 4)), stored)

        assertEquals(listOf(GridPlacement(2, 2, 2, 2)), spacerPlacements(restored))
        assertValid(restored)
    }

    @Test
    fun loadLayout_savedInferredSpacerWithInteriorKey_preservesDistinctStableFreeParts() {
        val keys = listOf(key(0, 2, rowSpan = 4), key(2, 6))
        val restored = loadLayout(
            keys,
            listOf(spacer("restored_row_2_start_spacer", GridPlacement(2, 0, 2, 6)))
        )
        val parts = restored.items.filterIsInstance<SpacerItem>()

        assertEquals(
            listOf(GridPlacement(2, 0, 2, 2), GridPlacement(2, 4, 2, 2)),
            parts.map { it.placement }
        )
        assertEquals(parts.size, parts.map { it.id }.toSet().size)
        assertValid(restored)
        val reloaded = loadLayout(keys, parts.map { spacer(it.id, it.placement) })
        assertEquals(parts, reloaded.items.filterIsInstance<SpacerItem>())
        assertValid(reloaded)
    }

    private fun assertValid(layout: KeyboardLayout) {
        assertFalse(hasPlacementIssues(layout.items, layout.rowUnitCount, layout.columnUnitCount))
    }

    private fun spacerPlacements(layout: KeyboardLayout): List<GridPlacement> =
        layout.items.filterIsInstance<SpacerItem>().map { it.placement }

    private fun loadLayout(
        keys: List<KeyDefinition>,
        spacers: List<SpacerDefinition> = emptyList()
    ): KeyboardLayout = runBlocking {
        whenever(dao.getFullLayoutById(1L)).thenReturn(flowOf(
            FullKeyboardLayout(
                layout = CustomKeyboardLayout(
                    layoutId = 1,
                    name = "test",
                    columnCount = 5,
                    rowCount = 5
                ),
                keysWithFlicks = keys.map {
                    KeyWithFlicks(it, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                },
                spacers = spacers
            )
        ))
        repository.getFullLayout(1L).first()
    }

    private fun key(row: Int, column: Int, rowSpan: Int = 2): KeyDefinition =
        KeyDefinition(
            ownerLayoutId = 1,
            label = "key",
            row = row / 2,
            column = column / 2,
            rowSpan = (rowSpan + 1) / 2,
            keyType = KeyType.NORMAL,
            keyIdentifier = "key_${row}_${column}",
            isSpecialKey = false,
            rowUnits = row,
            columnUnits = column,
            rowSpanUnits = rowSpan,
            columnSpanUnits = 2
        )

    private fun spacer(id: String, placement: GridPlacement): SpacerDefinition =
        SpacerDefinition(
            ownerLayoutId = 1,
            itemIdentifier = id,
            rowUnits = placement.rowUnits,
            columnUnits = placement.columnUnits,
            rowSpanUnits = placement.rowSpanUnits,
            columnSpanUnits = placement.columnSpanUnits
        )
}
