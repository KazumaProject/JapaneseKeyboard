package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.PopupMapInputOptions
import com.kazumaproject.custom_keyboard.data.PopupMapPresentation
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class FreeFlickPreferencesTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val preferences=PreferenceManager.getDefaultSharedPreferences(context)
    @Before fun setup() {preferences.edit().clear().commit();AppPreference.init(context)}
    @Test fun defaultsAndIndependentValuesSurviveMasterOffAndBackup() {
        assertEquals(PopupMapInputOptions(),FreeFlickPreferences.read(context))
        preferences.edit().putBoolean(FreeFlickPreferences.NEAREST,false)
            .putBoolean(FreeFlickPreferences.TEXT,false).putBoolean(FreeFlickPreferences.SWITCH,true)
            .putBoolean(FreeFlickPreferences.BACK,true)
            .putString(FreeFlickPreferences.PRESENTATION,"guide_above_key").commit()
        val options=FreeFlickPreferences.read(context)
        assertEquals(PopupMapInputOptions(false,false,true,true,PopupMapPresentation.GUIDE_ABOVE_KEY),options)
        val backup=AppPreference.exportAllToJson()
        preferences.edit().clear().commit();AppPreference.importAllFromJson(backup)
        assertEquals(options,FreeFlickPreferences.read(context))
        preferences.edit().putBoolean(FreeFlickPreferences.NEAREST,true).commit()
        assertEquals(options.copy(nearestEnabled=true),FreeFlickPreferences.read(context))
        preferences.edit().putString(FreeFlickPreferences.PRESENTATION,"unknown").commit()
        assertEquals(PopupMapPresentation.AROUND_KEY,FreeFlickPreferences.read(context).presentation)
    }
    @Test fun replacingTemplateOnlyChangesDraftAndUndoRestoresAllMaps() {
        val original=SumirePopupRepository(context).load("は").let { it.copy(maps=it.maps+it.maps.single().copy(id="extra",name="追加",tapText="★")) }
        val repository=SumirePopupRepository(context);repository.save(original)
        val model=PopupMapEditorViewModel(androidx.lifecycle.SavedStateHandle());model.load(original)
        val template=com.kazumaproject.custom_keyboard.data.SumirePopupDefaults.create("は")
        model.change(template)
        assertEquals(original,repository.load("は"));assertEquals(1,model.config!!.maps.size)
        assertTrue(model.undo());assertEquals(original,model.config)
        model.change(template);repository.save(model.config!!)
        assertEquals(template,repository.load("は"))
    }
    @Test fun tapSizeIsSharedBackedUpAndUsesTheCurrentSensitivityWithoutEditingMaps() {
        val original=SumirePopupRepository(context).load("は")
        preferences.edit().putString(FreeFlickPreferences.TAP_SIZE,"flick_sensitivity").commit()
        AppPreference.flick_sensitivity_preference=1
        val first=FreeFlickPreferences.tapArea(context,200f,100f)
        AppPreference.flick_sensitivity_preference=200
        val next=FreeFlickPreferences.tapArea(context,200f,100f)
        assertTrue(first.bounds.width<next.bounds.width)
        assertEquals(original,SumirePopupRepository(context).load("は"))
        val backup=AppPreference.exportAllToJson()
        preferences.edit().clear().commit();AppPreference.importAllFromJson(backup)
        assertEquals(com.kazumaproject.custom_keyboard.data.PopupTapSizeMode.FLICK_SENSITIVITY,FreeFlickPreferences.read(context).tapSizeMode)
        assertEquals(next,FreeFlickPreferences.tapArea(context,200f,100f))
        preferences.edit().putString(FreeFlickPreferences.TAP_SIZE,"invalid").commit()
        assertEquals(com.kazumaproject.custom_keyboard.data.PopupTapArea.KEY,FreeFlickPreferences.tapArea(context,200f,100f))
    }
}
