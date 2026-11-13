package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.SumirePopupDefaults
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SumirePopupRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val preferences=PreferenceManager.getDefaultSharedPreferences(context)
    private val repository=SumirePopupRepository(context)
    @Before fun setup() {preferences.edit().clear().commit();AppPreference.init(context)}
    @Test fun keysAndMapsPersistIndependentlyAndSurviveBackup() {
        val initial=repository.load("は")
        val edited=initial.withMap(initial.map("voiced")!!.copy(tapText="ありがとう"))
        repository.save(edited)
        assertEquals(edited,SumirePopupRepository(context).load("は"))
        assertEquals(SumirePopupDefaults.create("か"),repository.load("か"))
        val backup=AppPreference.exportAllToJson()
        preferences.edit().clear().commit()
        AppPreference.importAllFromJson(backup)
        assertEquals(edited,repository.load("は"))
    }
    @Test fun malformedOrDanglingDataIsNotExecutedOrSilentlyOverwritten() {
        val key=SumirePopupRepository.PREFIX+"は"
        preferences.edit().putString(key,"{broken").commit()
        assertTrue(repository.hasInvalidSavedConfig("は"))
        assertEquals(SumirePopupDefaults.create("は"),repository.load("は"))
        assertEquals("{broken",preferences.getString(key,null))
        val original=SumirePopupDefaults.create("は")
        val invalid=original.copy(maps=original.maps.filterNot {it.id=="voiced"})
        assertThrows(IllegalArgumentException::class.java) {repository.save(invalid)}
    }
    @Test fun draftAndUndoSurviveViewModelRecreationWithoutChangingSavedConfiguration() {
        val handle=SavedStateHandle()
        val first=PopupMapEditorViewModel(handle)
        first.load(repository.load("は"))
        first.mapId="voiced"
        val edited=first.config!!.withMap(first.config!!.map("voiced")!!.copy(tapText="★"))
        first.change(edited)
        val restored=PopupMapEditorViewModel(handle)
        assertEquals("voiced",restored.mapId)
        assertEquals("★",restored.config!!.map("voiced")!!.tapText)
        assertTrue(restored.dirty)
        assertEquals("ば",repository.load("は").map("voiced")!!.tapText)
        assertTrue(restored.undo())
        assertEquals("ば",restored.config!!.map("voiced")!!.tapText)
        assertFalse(restored.dirty)
    }
}
