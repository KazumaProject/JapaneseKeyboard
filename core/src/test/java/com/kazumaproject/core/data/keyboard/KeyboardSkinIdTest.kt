package com.kazumaproject.core.data.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSkinIdTest {

    @Test
    fun unknownPreferenceFallsBackToDefault() {
        assertEquals(KeyboardSkinId.DEFAULT, KeyboardSkinId.fromPreference("missing"))
        assertEquals(KeyboardSkinId.DEFAULT, KeyboardSkinId.fromPreference(null))
    }

    @Test
    fun allBuiltInSkinsHaveStableUniquePreferenceValues() {
        val values = KeyboardSkinId.entries.map { it.preferenceValue }

        assertEquals(9, values.size)
        assertEquals(values.size, values.toSet().size)
        assertTrue(values.contains(KeyboardSkinId.DEFAULT.preferenceValue))
        assertNotEquals(
            KeyboardSkinId.DEFAULT.preferenceValue,
            KeyboardSkinId.GLASS.preferenceValue,
        )
        assertTrue(values.contains(KeyboardSkinId.CUPERTINO.preferenceValue))
    }

    @Test
    fun preferenceValuesRoundTrip() {
        KeyboardSkinId.entries.forEach { skin ->
            assertEquals(skin, KeyboardSkinId.fromPreference(skin.preferenceValue))
        }
    }

    @Test
    fun nonDefaultSkinsOwnTheirPaletteAndVisualIdentity() {
        val specs = KeyboardSkinCatalog.all().filter { it.id != KeyboardSkinId.DEFAULT }

        assertEquals(8, specs.size)
        assertEquals(specs.size, specs.map { it.palette }.toSet().size)
        assertEquals(specs.size, specs.map { it.material }.toSet().size)
        assertEquals(specs.size, specs.map { it.depthModel }.toSet().size)
    }

    @Test
    fun motionPreferenceFallsBackToFullAndRoundTrips() {
        assertEquals(KeyboardSkinMotionMode.FULL, KeyboardSkinMotionMode.fromPreference(null))
        assertEquals(KeyboardSkinMotionMode.FULL, KeyboardSkinMotionMode.fromPreference("invalid"))
        KeyboardSkinMotionMode.entries.forEach { mode ->
            assertEquals(mode, KeyboardSkinMotionMode.fromPreference(mode.preferenceValue))
        }
    }
}
