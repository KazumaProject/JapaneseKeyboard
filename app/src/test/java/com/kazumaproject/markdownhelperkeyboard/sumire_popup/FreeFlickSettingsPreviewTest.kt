package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.SumirePopupDefaults
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FreeFlickSettingsPreviewTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
        com.google.android.material.R.style.Theme_Material3_DayNight)
    @Before fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }
    @Test fun settingsBindsTheActualKeyboardWithAllSavedMapsAndCurrentHeight() {
        val repository = SumirePopupRepository(context)
        for ((key, text) in listOf("は" to "保存", SumirePopupDefaults.PUNCTUATION_KEY to "句読点")) {
            val config = SumirePopupDefaults.create(key)
            repository.save(config.withMap(config.maps.single().copy(tapText = text)))
        }
        AppPreference.keyboard_height = 248
        val root = LayoutInflater.from(context).inflate(R.layout.free_flick_preview, FrameLayout(context), false)
        val preference = FreeFlickPreviewPreference(context)
        preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(root))
        val frame = root.findViewById<FrameLayout>(R.id.free_flick_preview_canvas)
        val preview = frame.getChildAt(0) as FreeFlickKeyboardPreview
        assertTrue(preview.getChildAt(0) is FlickKeyboardView)
        val maps = preview.previewLayout!!.keys.mapNotNull { it.popupMapConfig }
        assertEquals(11, maps.size)
        assertEquals("保存", maps.single { it.keyId == "は" }.maps.single().tapText)
        assertEquals("句読点", maps.single { it.keyId == SumirePopupDefaults.PUNCTUATION_KEY }.maps.single().tapText)
        assertEquals((248 * context.resources.displayMetrics.density).toInt(), frame.layoutParams.height)
        AppPreference.keyboard_height = 224
        preference.updateOptions()
        assertEquals((224 * context.resources.displayMetrics.density).toInt(), frame.layoutParams.height)
    }
}
