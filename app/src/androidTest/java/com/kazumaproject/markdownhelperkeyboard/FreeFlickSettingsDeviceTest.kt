package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.button.MaterialButton
import com.kazumaproject.custom_keyboard.data.SumirePopupDefaults
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeFlickSettingsDeviceTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val context get()=ins.targetContext
    private fun activity()=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
    private fun nav()=activity()!!.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
    private fun all(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap {all(view.getChildAt(it))} else emptyList()
    private fun views()=activity()?.window?.decorView?.let(::all).orEmpty()
    private fun preferences()=nav().childFragmentManager.primaryNavigationFragment as? PreferenceFragmentCompat
    private fun await(test: ()->Boolean) {
        repeat(400) {var ready=false;ins.runOnMainSync {ready=test()};if(ready)return;SystemClock.sleep(50)}
        fail("Free Flick UI did not reach expected state")
    }
    private fun start() {
        assumeTrue(context.packageName.endsWith(".popuptest"))
        ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await {activity()!=null}
    }
    @Test fun nativeSettingsAndSearchShareOptionsAndPopupLabelsAreReadable() {
        val prefs=PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().remove(FreeFlickPreferences.NEAREST).remove(FreeFlickPreferences.TEXT)
            .remove(FreeFlickPreferences.SWITCH).remove(FreeFlickPreferences.BACK).remove(FreeFlickPreferences.PRESENTATION).commit()
        start()
        ins.runOnMainSync {nav().navController.navigate(R.id.sumirePreferenceFragment)}
        await {preferences()?.findPreference<Preference>("sumire_free_flick_settings")!=null}
        ins.runOnMainSync {preferences()!!.findPreference<Preference>("sumire_free_flick_settings")!!.performClick()}
        await {preferences()?.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.NEAREST)!=null}
        ins.runOnMainSync {
            val screen=preferences()!!
            assertTrue(screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.NEAREST)!!.isChecked)
            assertTrue(screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.TEXT)!!.isChecked)
            assertFalse(screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.SWITCH)!!.isChecked)
            assertFalse(screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.BACK)!!.isChecked)
            screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.SWITCH)!!.isChecked=true
            screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.NEAREST)!!.isChecked=false
            assertFalse(screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.TEXT)!!.isEnabled)
            assertFalse(screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.SWITCH)!!.isEnabled)
            assertTrue(screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.SWITCH)!!.isChecked)
            screen.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.NEAREST)!!.isChecked=true
            screen.findPreference<ListPreference>(FreeFlickPreferences.PRESENTATION)!!.value="guide_above_key"
            activity()!!.recreate()
        }
        await {preferences()?.findPreference<ListPreference>(FreeFlickPreferences.PRESENTATION)?.value=="guide_above_key"}
        assertTrue(FreeFlickPreferences.read(context).nearestSwitch)
        ins.runOnMainSync {nav().navController.navigate(R.id.settingSearchFragment)}
        await {activity()?.findViewById<EditText>(R.id.setting_search_input)!=null}
        ins.runOnMainSync {activity()!!.findViewById<EditText>(R.id.setting_search_input).setText(context.getString(R.string.free_flick_nearest_back))}
        await {views().filterIsInstance<TextView>().any {it.id==R.id.setting_search_result_title && it.text==context.getString(R.string.free_flick_nearest_back)}}
        ins.runOnMainSync {views().filterIsInstance<TextView>().firstOrNull {it.id==R.id.setting_search_result_title && it.text==context.getString(R.string.free_flick_nearest_back)}?.let { title ->
            generateSequence(title.parent as? View) {it.parent as? View}.first {it.isClickable}.performClick()
        }}
        await {preferences()?.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.BACK)!=null}
        ins.runOnMainSync {nav().navController.navigate(R.id.flickKeyboardPopupStyleListFragment)}
        await {views().filterIsInstance<TextView>().any {it.text==context.getString(R.string.popup_style)}}
        ins.runOnMainSync {
            assertFalse(views().filterIsInstance<TextView>().any {"３段" in it.text || "3段" in it.text})
            val row=views().filterIsInstance<TextView>().first {it.text==context.getString(R.string.popup_style)}
            generateSequence(row.parent as? View) {it.parent as? View}.first {it.isClickable}.performClick()
        }
        await {preferences()?.findPreference<SwitchPreferenceCompat>(FreeFlickPreferences.NEAREST)!=null}
        ins.runOnMainSync {activity()!!.finish()}
    }
    @Test fun templateConfirmationUndoAndSavePreserveOtherKeys() {
        val repository=SumirePopupRepository(context)
        val original=LegacyPopupFixture.create("は")
        repository.save(original)
        val other=repository.load("か")
        start();ins.runOnMainSync {nav().navController.navigate(R.id.popupMapEditorFragment)}
        await {views().filterIsInstance<PopupMapEditorCanvas>().isNotEmpty()}
        fun apply() {
            ins.runOnMainSync {views().filterIsInstance<MaterialButton>().single {it.text==context.getString(R.string.free_flick_template)}.performClick()}
            await {WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<Button>().any {it.id==android.R.id.button1}}
            ins.runOnMainSync {WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<Button>().last {it.id==android.R.id.button1}.performClick()}
            await {views().filterIsInstance<PopupMapEditorCanvas>().first().config!!.maps.size==1}
        }
        apply()
        assertEquals(original,repository.load("は"))
        ins.runOnMainSync {views().filterIsInstance<MaterialButton>().single {it.text==context.getString(R.string.free_flick_settings)}.performClick()}
        await {preferences()?.findPreference<Preference>(FreeFlickPreferences.NEAREST)!=null}
        ins.runOnMainSync {nav().navController.popBackStack()}
        await {views().filterIsInstance<PopupMapEditorCanvas>().firstOrNull()?.config?.maps?.size==1}
        assertEquals(original,repository.load("は"))
        ins.runOnMainSync {views().filterIsInstance<MaterialButton>().single {it.text==context.getString(R.string.popup_undo)}.performClick()}
        await {views().filterIsInstance<PopupMapEditorCanvas>().first().config==original}
        apply()
        ins.runOnMainSync {views().filterIsInstance<MaterialButton>().single {it.text==context.getString(R.string.popup_save)}.performClick()}
        assertEquals(SumirePopupDefaults.create("は"),repository.load("は"))
        assertEquals(other,repository.load("か"))
        ins.runOnMainSync {activity()!!.finish()}
    }
}
