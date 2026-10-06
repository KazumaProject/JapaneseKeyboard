package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run with investigation/custom-toggle.init.gradle to keep user settings isolated. */
@RunWith(AndroidJUnit4::class)
class SumireSpecialKeyActionEditorDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun groupedPickerSavesReloadsAndResetsAllDirections() {
        val context = instrumentation.targetContext
        assumeTrue("Use investigation/custom-toggle.init.gradle",
            context.packageName.startsWith("com.kazumaproject.customtoggletest"))
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun host() = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
        fun openEditor() = await {
            val nav = host()?.navController
            if (nav?.currentDestination == null) false else {
                nav.navigate(R.id.sumireSpecialKeyActionEditorFragment, bundleOf(
                    "layoutType" to "toggle", "inputMode" to "ENGLISH", "keyId" to "enter_key"))
                true
            }
        }
        fun rows() = activity.findViewById<LinearLayout>(R.id.action_rows_container)
        val hiragana = context.getString(com.kazumaproject.custom_keyboard.R.string.action_switch_to_hiragana_mode)
        try {
            openEditor()
            await { rows() != null && rows().childCount == 5 }
            instrumentation.runOnMainSync { activity.findViewById<View>(R.id.reset_key_button).performClick() }
            await { descendants(rows()).filterIsInstance<TextView>().count {
                it.text.toString() == context.getString(R.string.sumire_special_key_default_status)
            } == 5 }
            for (direction in 0 until 5) {
                instrumentation.runOnMainSync { rows().getChildAt(direction).performClick() }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val list = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
                        .filterIsInstance<ListView>().single()
                    val adapter = list.adapter
                    assertEquals(context.getString(R.string.sumire_special_key_use_default), adapter.getItem(0))
                    assertTrue(adapter.isEnabled(0))
                    val header = (0 until adapter.count).first {
                        adapter.getItem(it) == context.getString(R.string.special_key_category_keyboard)
                    }
                    assertFalse(adapter.isEnabled(header))
                    val kana = (0 until adapter.count).single { adapter.getItem(it) == hiragana }
                    assertEquals(header + 1, kana)
                    assertTrue(adapter.isEnabled(kana))
                    list.performItemClick(adapter.getView(kana, null, list), kana, adapter.getItemId(kana))
                }
            }
            await { descendants(rows()).filterIsInstance<TextView>().count { it.text.toString() == hiragana } == 5 }
            instrumentation.runOnMainSync { activity.findViewById<View>(R.id.save_button).performClick() }
            await { host()?.navController?.currentDestination?.id != R.id.sumireSpecialKeyActionEditorFragment }
            openEditor()
            await { rows() != null && descendants(rows()).filterIsInstance<TextView>().count {
                it.text.toString() == hiragana
            } == 5 }
            // The first selectable option restores the default without an index offset.
            for (direction in 0 until 5) {
                instrumentation.runOnMainSync {
                    rows().getChildAt(direction).performClick()
                    val list = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
                        .filterIsInstance<ListView>().single()
                    list.performItemClick(list.adapter.getView(0, null, list), 0, list.adapter.getItemId(0))
                }
            }
            await { descendants(rows()).filterIsInstance<TextView>().count {
                it.text.toString() == context.getString(R.string.sumire_special_key_default_status)
            } == 5 }
            instrumentation.runOnMainSync { activity.findViewById<View>(R.id.save_button).performClick() }
            await { host()?.navController?.currentDestination?.id != R.id.sumireSpecialKeyActionEditorFragment }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("Editor did not reach the expected state")
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        else emptyList()
}
