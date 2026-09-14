package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplitKeyboardSettingsTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("split-test", 0)
    private val settings = SplitKeyboardSettings(prefs)
    @Before fun clear() { prefs.edit().clear().commit() }
    @Test fun defaultsAndInvalidValuesAreSafe() {
        SplitSlot.entries.forEach { assertEquals(SplitKeyboardSelection(), settings.selection(it)) }
        assertEquals(SplitCandidatePlacement.BOTH, settings.candidates)
        prefs.edit().putString(SplitKeyboardSettings.typeKey(SplitSlot.MAIN), "SPLIT")
            .putString(SplitKeyboardSettings.typeKey(SplitSlot.SUB), "missing")
            .putString(SplitKeyboardSettings.CANDIDATES, "missing").commit()
        SplitSlot.entries.forEach { assertEquals(KeyboardType.TENKEY, settings.selection(it).type) }
        assertEquals(SplitCandidatePlacement.BOTH, settings.candidates)
    }
    @Test fun customIdsAndFourIndependentGeometriesSurviveRecreation() {
        settings.saveSelection(SplitSlot.MAIN, SplitKeyboardSelection(KeyboardType.CUSTOM, "left"))
        settings.saveSelection(SplitSlot.SUB, SplitKeyboardSelection(KeyboardType.CUSTOM, "right"))
        val placements = SplitSlot.entries.flatMap { slot -> listOf(false, true).map { slot to it } }
        placements.forEachIndexed { i, (slot, landscape) ->
            settings.savePlacement(slot, landscape, SplitPlacement(i / 4f, 1f, 200f + i, 220f + i))
        }
        val restored = SplitKeyboardSettings(prefs)
        assertEquals("left", restored.selection(SplitSlot.MAIN).customStableId)
        assertEquals("right", restored.selection(SplitSlot.SUB).customStableId)
        placements.forEachIndexed { i, (slot, landscape) ->
            assertEquals(SplitPlacement(i / 4f, 1f, 200f + i, 220f + i), restored.placement(slot, landscape))
        }
        assertEquals(KeyboardType.CUSTOM, restored.selection(SplitSlot.MAIN).resolved(setOf("left")).type)
        assertEquals(KeyboardType.TENKEY, restored.selection(SplitSlot.SUB).resolved(setOf("left")).type)
    }
    @Test fun allCandidateChoicesPersistAndSelectExactlyTheRequestedPanes() {
        SplitCandidatePlacement.entries.forEach { placement ->
            prefs.edit().putString(SplitKeyboardSettings.CANDIDATES, placement.name).commit()
            val restored = SplitKeyboardSettings(prefs).candidates
            assertEquals(placement, restored)
            assertEquals(placement != SplitCandidatePlacement.SUB, restored.shows(SplitSlot.MAIN))
            assertEquals(placement != SplitCandidatePlacement.MAIN, restored.shows(SplitSlot.SUB))
        }
    }
    @Test fun smallBodiesAndEditPlacementSurviveRecreation() {
        assertEquals(SplitEditPlacement.MAIN, settings.editPlacement)
        prefs.edit().putString(SplitKeyboardSettings.EDIT_PLACEMENT, "invalid").commit()
        assertEquals(SplitEditPlacement.MAIN, settings.editPlacement)
        SplitEditPlacement.entries.forEach { value ->
            prefs.edit().putString(SplitKeyboardSettings.EDIT_PLACEMENT, value.name).commit()
            assertEquals(value, SplitKeyboardSettings(prefs).editPlacement)
            assertTrue(value.shows(SplitSlot.MAIN))
            assertEquals(value == SplitEditPlacement.BOTH, value.shows(SplitSlot.SUB))
        }
        for (slot in SplitSlot.entries) for (landscape in listOf(false, true)) {
            val placement = SplitPlacement(.25f, .75f, 120f, 80f)
            settings.savePlacement(slot, landscape, placement)
            assertEquals(placement, SplitKeyboardSettings(prefs).placement(slot, landscape))
        }
    }
    @Test fun corruptedGeometryIsFiniteAndBounded() {
        val value = SplitPlacement(Float.NaN, Float.POSITIVE_INFINITY, -1f, Float.NaN).normalized()
        assertEquals(SplitPlacement(0f, 1f, 120f, 240f), value)
        settings.savePlacement(SplitSlot.SUB, false, SplitPlacement(-2f, 4f, 10000f, 1f))
        assertEquals(SplitPlacement(0f, 1f, 1200f, 80f), settings.placement(SplitSlot.SUB, false))
    }
}
