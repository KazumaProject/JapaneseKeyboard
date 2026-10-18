package com.kazumaproject.markdownhelperkeyboard.morse

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.morse_keyboard.MorseControl
import com.kazumaproject.morse_keyboard.MorseControlLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorseControlLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun twoRowsAndPlacementSurviveReopeningWithoutChangingMaps() {
        val store = MorsePreferences(context)
        val maps = store.enabledMaps()
        val layout = MorseControlLayout(listOf(listOf(MorseControl.DELETE, MorseControl.KEYBOARD),
            listOf(MorseControl.SPACE, MorseControl.BOUNDARY, MorseControl.COMMIT)), abovePads = true)
        store.saveControlLayout(layout)
        store.saveReverse(true)
        val reopened = MorsePreferences(context)
        assertEquals(layout, reopened.settings().controlLayout)
        assertTrue(reopened.settings().reverse)
        assertEquals(maps, reopened.enabledMaps())
        assertEquals(layout, MorseLayoutCodec.decode(MorseLayoutCodec.encode(layout)))
    }
    @Test fun invalidLayoutsCannotReplaceSavedPlacement() {
        val store = MorsePreferences(context)
        store.saveControlLayout(MorseControlLayout(abovePads = true))
        val before = prefs.all.toMap()
        for(rows in listOf(emptyList(), listOf(emptyList()), listOf(listOf(MorseControl.DELETE)),
            listOf(listOf(MorseControl.BOUNDARY,MorseControl.KEYBOARD,MorseControl.BOUNDARY)),
            listOf(MorseControl.entries.take(7)))) {
            assertThrows(IllegalArgumentException::class.java) { store.saveControlLayout(MorseControlLayout(rows)) }
            assertEquals(before,prefs.all)
        }
    }
    @Test fun corruptedBackupLayoutFallsBackWithoutOverwritingData() {
        val invalid = """{"version":2,"rows":[["BOUNDARY","KEYBOARD"]],"abovePads":true}"""
        prefs.edit().putString("morse_key_layout_v1",invalid).commit()
        assertEquals(MorseControlLayout(),MorsePreferences(context).controlLayout())
        assertEquals(invalid,prefs.getString("morse_key_layout_v1",null))
    }
}
