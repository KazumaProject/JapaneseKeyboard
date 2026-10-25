package com.kazumaproject.markdownhelperkeyboard.gesture

import com.kazumaproject.custom_keyboard.data.KeyAction

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.gesture_keyboard.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.isTenKeyFamily
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GesturePreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var store: GesturePreferences
    private val custom = GestureSet("custom", "Personal", listOf(
        GestureEntry("my-stroke", GestureOutput.Reading("か"), listOf(GesturePresets.path("0,0 10,0 10,10")))
    ))
    @Before fun setup() { preferences.edit().clear().commit(); AppPreference.init(context); store = GesturePreferences(context) }
    @Test fun allSettingsBackupRestoresSamplesAssignmentsEmptySidesDuplicateActionsAndSelection() {
        store.saveSet(custom)
        val left = listOf(GestureControlKey("one", KeyAction.Delete, "A"), GestureControlKey("two", KeyAction.Delete, "B"))
        store.saveLayout(GestureControlLayout(left, emptyList()))
        store.saveSession(GestureSessionState().withSet("pad_1", "custom").withSet("pad_2", GesturePresets.LATIN).update("pad_2") { it.copy(uppercase = true) })
        val backup = AppPreference.exportAllToJson()
        preferences.edit().clear().commit()
        AppPreference.importAllFromJson(backup)
        val restored = GesturePreferences(context)
        assertEquals(listOf(custom), restored.customSets())
        assertEquals(GestureControlLayout(left, emptyList()), restored.layout())
        assertEquals(GestureSessionState().withSet("pad_1", "custom").withSet("pad_2", GesturePresets.LATIN).update("pad_2") { it.copy(uppercase = true) }, restored.session())
    }
    @Test fun deletingSelectedCustomSetRepairsOnlyItsReferences() {
        store.saveSet(custom)
        store.saveSession(GestureSessionState().withSet("pad_1", "custom").withSet("pad_2", GesturePresets.LATIN))
        store.deleteSet("custom")
        assertEquals(GesturePresets.KANA, store.session().pads[0].setId)
        assertEquals(GesturePresets.LATIN, store.session().pads[1].setId)
        assertTrue(store.customSets().isEmpty())
    }
    @Test fun corruptAndUnknownVersionsAreRetainedAndNotOverwritten() {
        val invalid = """{"version":99,"sets":[]}"""
        preferences.edit().putString(GesturePreferences.SETS, invalid).commit()
        assertTrue(store.invalidData())
        assertEquals(4, store.sets().size)
        assertThrows(IllegalArgumentException::class.java) { store.saveSet(custom) }
        assertEquals(invalid, preferences.getString(GesturePreferences.SETS, null))
        val corrupt = """{"version":1,"left":[{"id":"same","action":"DELETE"}],"right":[{"id":"same","action":"SPACE"}]}"""
        preferences.edit().putString(GesturePreferences.LAYOUT, corrupt).commit()
        assertEquals(GestureControlLayout(), store.layout())
        assertThrows(IllegalArgumentException::class.java) { store.saveLayout(GestureControlLayout()) }
        assertEquals(corrupt, preferences.getString(GesturePreferences.LAYOUT, null))
    }
    @Test fun invalidStrokeCannotPartiallyReplaceExistingSet() {
        store.saveSet(custom)
        val raw = preferences.getString(GesturePreferences.SETS, null)
        assertThrows(IllegalArgumentException::class.java) {
            store.saveSet(custom.copy(entries = listOf(custom.entries.first().copy(samples = listOf(listOf(GesturePoint(1f, 1f), GesturePoint(1f, 1f)))))))
        }
        assertEquals(raw, preferences.getString(GesturePreferences.SETS, null))
    }
    @Test fun gestureOrderAndSizeFamilyRoundTripWithoutChangingDefaults() {
        assertEquals(listOf(KeyboardType.TENKEY, KeyboardType.QWERTY), AppPreference.keyboard_order)
        val order = listOf(KeyboardType.QWERTY, KeyboardType.GESTURE, KeyboardType.TENKEY)
        AppPreference.keyboard_order = order
        AppPreference.init(context)
        assertEquals(order, AppPreference.keyboard_order)
        assertTrue(KeyboardType.GESTURE.isTenKeyFamily)
    }
    @Test fun invalidOptionalPreferenceTypesUseDefaultsWithoutRewritingRawValues() {
        preferences.edit().putString("gesture_show_guide", "invalid")
            .putString("gesture_recognition_tolerance", "invalid").commit()
        assertTrue(store.showGuide())
        assertEquals(GestureRecognizer.DEFAULT_MAX_DISTANCE, store.maxDistance())
        assertEquals("invalid", preferences.getString("gesture_recognition_tolerance", null))
    }
}
