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
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.FreeFlickPreferences
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.PopupMapEditorCanvas
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
    @Test fun addingPopupUsesNormalKeySizeEvenWhenTapSizeFollowsSensitivity() {
        for (mode in PopupTapSizeMode.entries) {
            val controller = Robolectric.buildActivity(FragmentActivity::class.java)
            controller.get().setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
            controller.setup()
            try {
                val activity = controller.get()
                AppPreference.init(activity)
                PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putString(FreeFlickPreferences.TAP_SIZE, mode.preferenceValue).commit()
                val existing = PopupRegion("existing", PopupBounds(.6f, -.4f, 1.4f, .4f), PopupAction(text = "saved"))
                val original = PopupKeyConfig("custom-key", "normal", listOf(PopupMap("normal", "Normal", "tap", listOf(existing))))
                val dialog = CustomFreeFlickEditorDialog.create(PopupKeyConfigCodec.encode(original))
                dialog.showNow(activity.supportFragmentManager, "editor")
                shadowOf(Looper.getMainLooper()).idle()
                val editor = dialog.childFragmentManager.findFragmentById(R.id.custom_free_flick_editor_host) as PopupMapEditorFragment
                val canvas = descendants(editor.requireView()).filterIsInstance<PopupMapEditorCanvas>().single()
                canvas.setSourceKeySize(200f * activity.resources.displayMetrics.density, 100f * activity.resources.displayMetrics.density)
                canvas.layout(0, 0, 900, 600)
                assertEquals(mode, canvas.inputOptions.tapSizeMode)
                if (mode == PopupTapSizeMode.FLICK_SENSITIVITY) assertTrue(canvas.tapArea.bounds.width < 1f)
                descendants(editor.requireView()).filterIsInstance<TextView>()
                    .single { it.text == activity.getString(R.string.popup_add) }.performClick()
                val regions = ViewModelProvider(editor)[PopupMapEditorViewModel::class.java].config!!.maps.single().regions
                assertEquals(2, regions.size)
                assertEquals(existing, regions.single { it.id == existing.id })
                val added = regions.single { it.id != existing.id }
                assertEquals(1f, added.bounds.width, .0001f)
                assertEquals(1f, added.bounds.height, .0001f)
                assertTrue(PopupMapValidator.conflicts(original.withMap(original.maps.single().copy(regions = regions))).isEmpty())
            } finally { controller.pause().stop().destroy() }
        }
    }

}
