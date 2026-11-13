package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.Spinner
import android.widget.EditText
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SumirePreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SumirePopupEditorDeviceTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val context get()=ins.targetContext
    private fun activity()=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
    private fun nav()=(activity()?.supportFragmentManager?.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment)
    private fun descendants(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap {descendants(view.getChildAt(it))} else emptyList()
    private fun views()=activity()?.window?.decorView?.let(::descendants).orEmpty()
    private fun canvas()=views().filterIsInstance<PopupMapEditorCanvas>().firstOrNull()
    private fun await(test: ()->Boolean) {
        val until=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<until) {var ready=false;ins.runOnMainSync {ready=test()};if(ready)return;SystemClock.sleep(50)}
        fail("Editor did not reach expected state")
    }
    @Test fun newAndLegacySettingsOpenTheSameEditorAndEditsPersist() {
        assumeTrue(context.packageName.endsWith(".popuptest"))
        val repository=SumirePopupRepository(context)
        repository.save(LegacyPopupFixture.create("は"))
        for(modern in listOf(true,false)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("setting_use_new_home_screen_preference",modern).commit()
            ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await {nav()?.navController?.currentDestination!=null}
            ins.runOnMainSync {
                if(modern) nav()!!.navController.navigate(R.id.settingSearchFragment)
                else nav()!!.navController.navigate(R.id.sumirePreferenceFragment)
            }
            if(modern) {
                await {views().any {it.id==R.id.setting_search_input}}
                ins.runOnMainSync {activity()!!.findViewById<EditText>(R.id.setting_search_input).setText(context.getString(R.string.popup_editor_entry))}
                await {views().filterIsInstance<TextView>().any {it !is EditText && it.isShown && it.text.toString()==context.getString(R.string.popup_editor_entry)}}
                ins.runOnMainSync {
                    var target: View?=views().filterIsInstance<TextView>().first {it !is EditText && it.isShown && it.text.toString()==context.getString(R.string.popup_editor_entry)}
                    while(target!=null && !target.isClickable) target=target.parent as? View
                    assertTrue("Search result must open the editor",target?.performClick()==true)
                }
            } else {
                await {(nav()?.childFragmentManager?.primaryNavigationFragment as? SumirePreferenceFragment)?.findPreference<Preference>("sumire_popup_map_editor_preference")!=null}
                ins.runOnMainSync {(nav()!!.childFragmentManager.primaryNavigationFragment as SumirePreferenceFragment).findPreference<Preference>("sumire_popup_map_editor_preference")!!.performClick()}
            }
            await {canvas()!=null && canvas()!!.config!=null}
            ins.runOnMainSync {
                val input=views().filterIsInstance<TextInputEditText>().single {it.contentDescription==context.getString(R.string.popup_input)}
                input.setText(if(modern) "は★" else "は☆")
                views().filterIsInstance<MaterialButton>().single {it.text==context.getString(R.string.popup_save)}.performClick()
            }
            assertEquals(if(modern) "は★" else "は☆",repository.load("は").map("normal")!!.tapText)
            ins.runOnMainSync {activity()!!.finish()}
        }
    }
    @Test fun popupTextDragConstraintsAndDraftSurviveRecreation() {
        assumeTrue(context.packageName.endsWith(".popuptest"))
        SumirePopupRepository(context).save(LegacyPopupFixture.create("は"))
        ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await {nav()?.navController?.currentDestination!=null}
        ins.runOnMainSync {nav()!!.navController.navigate(R.id.popupMapEditorFragment)}
        await {canvas()!=null && canvas()!!.width>0}
        ins.runOnMainSync {
            val c=canvas()!!
            val mapSpinner=views().filterIsInstance<Spinner>().single {it.contentDescription==context.getString(R.string.popup_map)}
            mapSpinner.setSelection(c.config!!.maps.indexOfFirst {it.id=="voiced"})
        }
        await {canvas()?.map?.id=="voiced"}
        ins.runOnMainSync {
            val c=canvas()!!
            fun touch(action: Int,x: Float,y: Float) {val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,c.originX+x*c.keyWidth,c.originY+y*c.keyHeight,0);c.dispatchTouchEvent(event);event.recycle()}
            touch(MotionEvent.ACTION_DOWN,-1f,-.7f);touch(MotionEvent.ACTION_UP,-1f,-.7f)
            views().filterIsInstance<TextInputEditText>().single {it.contentDescription==context.getString(R.string.popup_input)}.setText("ありがとう")
            touch(MotionEvent.ACTION_DOWN,-1f,-.7f);touch(MotionEvent.ACTION_MOVE,0f,0f);touch(MotionEvent.ACTION_UP,0f,0f)
            assertTrue(PopupMapValidator.conflicts(c.config!!).isEmpty())
            assertFalse(c.selectedBounds!!.intersects(PopupBounds.KEY))
            activity()!!.recreate()
        }
        await {canvas()?.map?.id=="voiced" && canvas()!!.map!!.regions.any {it.action.text=="ありがとう"}}
        ins.runOnMainSync {
            views().filterIsInstance<MaterialButton>().single {it.text==context.getString(R.string.popup_save)}.performClick()
            activity()!!.finish()
        }
        assertTrue(SumirePopupRepository(context).load("は").map("voiced")!!.regions.any {it.action.text=="ありがとう"})
    }
    @Test fun keyboardSelectionKeepsDraftOnCancelAndRecreation() {
        assumeTrue(context.packageName.endsWith(".popuptest"))
        SumirePopupRepository(context).save(LegacyPopupFixture.create("は"))
        ins.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await {nav()?.navController?.currentDestination!=null}
        ins.runOnMainSync {nav()!!.navController.navigate(R.id.popupMapEditorFragment)}
        await {canvas()?.config!=null && views().any {it is FreeFlickKeyPicker}}
        fun choose(key: String) {
            ins.runOnMainSync {
                val picker=views().filterIsInstance<FreeFlickKeyPicker>().single()
                val keyboard=picker.keyboard
                val button=(0 until keyboard.childCount).map(keyboard::getChildAt)
                    .filterIsInstance<TextView>().single {it.text.toString()==key}
                assertTrue(button.isEnabled)
                button.performClick()
            }
        }
        for(key in SumirePopupDefaults.rows.keys) {
            choose(key)
            await {canvas()?.config?.keyId==key}
            ins.runOnMainSync {
                val keyboard=views().filterIsInstance<FreeFlickKeyPicker>().single().keyboard
                assertEquals(1,(0 until keyboard.childCount).count {keyboard.getChildAt(it).isSelected})
            }
        }
        choose("は");await {canvas()?.config?.keyId=="は"}
        ins.runOnMainSync {views().filterIsInstance<TextInputEditText>().single {it.contentDescription==context.getString(R.string.popup_input)}.setText("編集中")}
        choose("か")
        fun dialogButton(id: Int) {
            await {WindowInspector.getGlobalWindowViews().flatMap(::descendants).filterIsInstance<Button>().any {it.id==id && it.isShown}}
            ins.runOnMainSync {WindowInspector.getGlobalWindowViews().flatMap(::descendants).filterIsInstance<Button>().last {it.id==id && it.isShown}.performClick()}
        }
        dialogButton(android.R.id.button3)
        await {canvas()?.config?.keyId=="は" && canvas()?.config?.map("normal")?.tapText=="編集中"}
        ins.runOnMainSync {activity()!!.recreate()}
        await {canvas()?.config?.keyId=="は" && canvas()?.config?.map("normal")?.tapText=="編集中"}
        choose("か")
        dialogButton(android.R.id.button2)
        await {canvas()?.config?.keyId=="か"}
        ins.runOnMainSync {activity()!!.finish()}
    }

}
