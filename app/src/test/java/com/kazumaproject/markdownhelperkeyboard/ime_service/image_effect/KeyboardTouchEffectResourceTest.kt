package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardTouchEffectResourceTest {

    @Test
    fun touchEffectEntriesAndValuesStayAligned() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = context.resources.getStringArray(R.array.keyboard_touch_effect_type_entries)
        val values = context.resources.getStringArray(R.array.keyboard_touch_effect_type_values)

        assertEquals(entries.size, values.size)
        assertEquals(
            listOf(
                KeyboardTouchEffectType.NONE,
                KeyboardTouchEffectType.LIQUID_INK,
                KeyboardTouchEffectType.AURORA_INK,
                KeyboardTouchEffectType.LIQUID_RIPPLE,
                KeyboardTouchEffectType.SPRAY_PAINT,
                KeyboardTouchEffectType.LUMINOUS_BLOB,
                KeyboardTouchEffectType.CINEMATIC_WAVE
            ),
            values.toList()
        )
        assertFalse(values.contains("suminagashi"))
        assertFalse(values.contains("suminagashi_ink"))
    }

    @Test
    fun touchEffectDisplayNamesUseLiquidAndAuroraInk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val englishEntries =
            context.resources.getStringArray(R.array.keyboard_touch_effect_type_entries).toList()
        val japaneseContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.JAPANESE)
            }
        )
        val japaneseEntries =
            japaneseContext.resources.getStringArray(R.array.keyboard_touch_effect_type_entries)
                .toList()

        assertTrue(englishEntries.contains("Liquid Ink"))
        assertTrue(englishEntries.contains("Aurora Ink"))
        assertTrue(englishEntries.contains("Luminous Blob"))
        assertTrue(englishEntries.contains("Cinematic Wave"))
        assertTrue(japaneseEntries.contains("リキッドインク"))
        assertTrue(japaneseEntries.contains("オーロラインク"))
        assertTrue(japaneseEntries.contains("光の膜"))
        assertTrue(japaneseEntries.contains("シネマティックウェーブ"))

        val legacyJapaneseLabel = "\u58a8\u6d41\u3057"
        (englishEntries + japaneseEntries).forEach { label ->
            assertFalse(label.contains("Suminagashi"))
            assertFalse(label.contains(legacyJapaneseLabel))
        }
    }

    @Test
    fun cinematicWaveTypeEntriesAndJapaneseLabelsStayAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val values =
            context.resources.getStringArray(R.array.keyboard_touch_effect_cinematic_wave_type_values)
                .toList()
        val englishEntries =
            context.resources.getStringArray(R.array.keyboard_touch_effect_cinematic_wave_type_entries)
                .toList()
        val japaneseContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.JAPANESE)
            }
        )
        val japaneseEntries =
            japaneseContext.resources.getStringArray(
                R.array.keyboard_touch_effect_cinematic_wave_type_entries
            ).toList()

        assertEquals(
            listOf(
                CinematicWaveSettings.WAVE_TYPE_AURORA_MEMBRANE,
                CinematicWaveSettings.WAVE_TYPE_SILK_SINE
            ),
            values
        )
        assertTrue(englishEntries.contains("Aurora Membrane"))
        assertTrue(englishEntries.contains("Silk Sine"))
        assertTrue(japaneseEntries.contains("オーロラ膜"))
        assertTrue(japaneseEntries.contains("シルキーサインウェーブ"))
    }
}
