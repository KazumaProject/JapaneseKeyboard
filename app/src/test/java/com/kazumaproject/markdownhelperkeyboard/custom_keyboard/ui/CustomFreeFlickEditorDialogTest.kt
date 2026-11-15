package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.PopupMapEditorFragment
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.PopupMapEditorViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomFreeFlickEditorDialogTest {
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun customMapDraftSurvivesRecreationAndReturnsWithoutChangingBuiltInPreferences() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        controller.setup()
        try {
            var activity = controller.get()
            AppPreference.init(activity)
            val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
            val before = preferences.all.toMap()
            val original = PopupKeyConfigCodec.create("custom-key", "first")
            CustomFreeFlickEditorDialog.create(PopupKeyConfigCodec.encode(original))
                .showNow(activity.supportFragmentManager, "editor")
            shadowOf(Looper.getMainLooper()).idle()
            fun editor(): PopupMapEditorFragment {
                val dialog = activity.supportFragmentManager.findFragmentByTag("editor") as CustomFreeFlickEditorDialog
                return dialog.childFragmentManager.findFragmentById(R.id.custom_free_flick_editor_host) as PopupMapEditorFragment
            }
            val changed = original.withMap(original.maps.single().copy(tapText = "changed😀"))
            ViewModelProvider(editor())[PopupMapEditorViewModel::class.java].change(changed)
            controller.recreate()
            activity = controller.get()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(changed, ViewModelProvider(editor())[PopupMapEditorViewModel::class.java].config)
            var returned: PopupKeyConfig? = null
            activity.supportFragmentManager.setFragmentResultListener(CustomFreeFlickEditorDialog.RESULT, activity) { _, result ->
                returned = PopupKeyConfigCodec.decode(result.getString(CustomFreeFlickEditorDialog.ARG_CONFIG))
            }
            descendants(editor().requireView()).filterIsInstance<TextView>()
                .single { it.text.toString() == activity.getString(R.string.popup_apply) }.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(changed, returned)
            assertEquals(before, preferences.all)
            assertNull(activity.supportFragmentManager.findFragmentByTag("editor"))
        } finally { controller.pause().stop().destroy() }
    }
}
