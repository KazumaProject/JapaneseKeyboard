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

        assertEquals(17, values.size)
        assertEquals(values.size, values.toSet().size)
        assertTrue(values.contains(KeyboardSkinId.DEFAULT.preferenceValue))
        assertNotEquals(
            KeyboardSkinId.DEFAULT.preferenceValue,
            KeyboardSkinId.GLASS.preferenceValue,
        )
        assertTrue(values.contains(KeyboardSkinId.CUPERTINO.preferenceValue))
        assertTrue(values.contains(KeyboardSkinId.CUPERTINO_DARK.preferenceValue))
        assertTrue(values.contains(KeyboardSkinId.SUMI_HANSHI.preferenceValue))
        assertTrue(values.contains(KeyboardSkinId.LETTERPRESS.preferenceValue))
        assertTrue(values.contains(KeyboardSkinId.PORCELAIN.preferenceValue))
        assertTrue(values.contains(KeyboardSkinId.URUSHI.preferenceValue))
        assertTrue(values.contains(KeyboardSkinId.CHALKBOARD.preferenceValue))
        assertTrue(values.contains(KeyboardSkinId.LINEN.preferenceValue))
        assertTrue(values.contains(KeyboardSkinId.MONOCHROME_LCD.preferenceValue))
    }

    @Test
    fun preferenceValuesRoundTrip() {
        KeyboardSkinId.entries.forEach { skin ->
            assertEquals(skin, KeyboardSkinId.fromPreference(skin.preferenceValue))
        }
    }

    @Test
    fun importedReferencesUseAStableNamespacedPreferenceValue() {
        val reference = KeyboardSkinRef.fromPreference("imported:ai-sakura-cyber")

        assertEquals(
            KeyboardSkinRef.Imported("ai-sakura-cyber"),
            reference,
        )
        assertEquals("imported:ai-sakura-cyber", reference.preferenceValue)
        assertEquals(KeyboardSkinRef.DEFAULT, KeyboardSkinRef.fromPreference("imported:bad id"))
    }

    @Test
    fun nonDefaultSkinsOwnTheirPaletteAndVisualIdentity() {
        val specs = KeyboardSkinCatalog.all().filter { it.id != KeyboardSkinId.DEFAULT }
        val baseDesigns = specs.filter { it.id != KeyboardSkinId.CUPERTINO_DARK }

        assertEquals(16, specs.size)
        assertEquals(specs.size, specs.map { it.palette }.toSet().size)
        assertEquals(baseDesigns.size, baseDesigns.map { it.material }.toSet().size)
        assertEquals(
            KeyboardSkinCatalog.specFor(KeyboardSkinId.CUPERTINO).material,
            KeyboardSkinCatalog.specFor(KeyboardSkinId.CUPERTINO_DARK).material,
        )
    }

    @Test
    fun tactileConceptSkinsHaveDedicatedMaterialsAndDepthModels() {
        val ids = listOf(
            KeyboardSkinId.SUMI_HANSHI,
            KeyboardSkinId.LETTERPRESS,
            KeyboardSkinId.PORCELAIN,
            KeyboardSkinId.URUSHI,
            KeyboardSkinId.CHALKBOARD,
            KeyboardSkinId.LINEN,
            KeyboardSkinId.MONOCHROME_LCD,
        )
        val specs = ids.map(KeyboardSkinCatalog::specFor)

        assertEquals(ids.size, specs.map { it.material }.toSet().size)
        assertEquals(ids.size, specs.map { it.depthModel }.toSet().size)
        assertEquals(ids.size, specs.map { it.palette }.toSet().size)
        assertTrue(specs.all { it.motion.continuousPeriodMs == 0L })
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
